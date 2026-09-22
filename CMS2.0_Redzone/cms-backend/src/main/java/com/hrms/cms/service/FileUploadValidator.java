package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * UST16/20/23: the browser's `accept` attribute and the extension check in FileStorageConfig are both
 * trivially bypassed — an attacker posting directly to /api/files/upload can name a payload "x.pdf".
 * This validator additionally sniffs the leading bytes, so the declared extension has to match the
 * actual container format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadValidator {

    private final FileStorageConfig config;

    /** Longest signature below is 8 bytes; read a little more so future signatures fit. */
    private static final int SIGNATURE_BYTES = 16;

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /**
     * Extensions whose bytes we can prove. Anything absent (txt, csv) has no signature to check, so it
     * is accepted on extension alone — deliberately, since inventing a signature would reject valid files.
     */
    private static final Map<String, List<byte[]>> SIGNATURES = Map.of(
            "pdf", List.of(PDF),
            "png", List.of(PNG),
            "jpg", List.of(JPEG),
            "jpeg", List.of(JPEG),
            "zip", List.of(ZIP),
            // Office 2007+ files are ZIP containers; the 97-2003 formats are OLE2. Both remain in use.
            "docx", List.of(ZIP),
            "xlsx", List.of(ZIP),
            "doc", List.of(OLE2, ZIP),
            "xls", List.of(OLE2, ZIP)
    );

    /** Characters that turn a stored name into a path or a shell/Windows-hostile name. */
    private static final String ILLEGAL_NAME_CHARS = "<>:\"|?*\\/";

    public static class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }

    /** Throws InvalidUploadException with a citizen-safe message when the file must be rejected. */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is empty");
        }
        validateName(file.getOriginalFilename());
        if (file.getSize() > config.getMaxFileSize()) {
            throw new InvalidUploadException(
                    "File exceeds maximum size of " + (config.getMaxFileSize() / 1048576) + "MB");
        }
        validateSignature(file, extensionOf(file.getOriginalFilename()));
    }

    /**
     * NFR-006 for a whole upload batch: at most 10 files, at most 2MB each, at most 25MB in total.
     *
     * Per-file validation alone cannot enforce NFR-006 — twenty 2MB files each pass validate() while
     * together breaching both the count and the aggregate cap, and no aggregate cap existed anywhere in
     * the product. The count and total are checked BEFORE per-file work so an oversized batch is
     * rejected without reading every payload.
     *
     * existingCount and existingBytes carry what the record already holds, so the caps apply to the
     * record rather than to one request: ten separate single-file uploads must not bypass a ten-file
     * limit.
     */
    public void validateBatch(List<MultipartFile> files, int existingCount, long existingBytes) {
        if (files == null || files.isEmpty()) {
            return;
        }

        long incomingCount = files.stream().filter(f -> f != null && !f.isEmpty()).count();
        if (existingCount + incomingCount > config.getMaxFilesPerComplaint()) {
            throw new InvalidUploadException(
                    "A maximum of " + config.getMaxFilesPerComplaint() + " files may be attached");
        }

        long incomingBytes = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();
        if (existingBytes + incomingBytes > config.getMaxTotalSize()) {
            throw new InvalidUploadException(
                    "Attachments exceed the total size limit of "
                            + (config.getMaxTotalSize() / 1048576) + "MB");
        }

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                validate(file);
            }
        }
    }

    /** Convenience for the common case of a first upload against an empty record. */
    public void validateBatch(List<MultipartFile> files) {
        validateBatch(files, 0, 0L);
    }

    /** Name-and-extension checks for the chunked path, where no bytes are available up front. */
    public void validateName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidUploadException("File name is required");
        }
        if (fileName.length() > 255) {
            throw new InvalidUploadException("File name must not exceed 255 characters");
        }
        if (fileName.contains("..") || fileName.chars().anyMatch(c -> ILLEGAL_NAME_CHARS.indexOf(c) >= 0)) {
            throw new InvalidUploadException("File name contains invalid characters");
        }
        if (fileName.chars().anyMatch(c -> c < 0x20)) {
            throw new InvalidUploadException("File name contains invalid characters");
        }
        if (!config.isAllowedType(fileName)) {
            throw new InvalidUploadException("File type not allowed. Allowed: " + config.getAllowedTypes());
        }
    }

    /**
     * Signature check for the chunked path, where the bytes only exist once every chunk has landed.
     * Returns null when the file is acceptable, otherwise the rejection message.
     */
    public String checkAssembledSignature(Path assembled, String fileName) {
        String extension = extensionOf(fileName);
        List<byte[]> expected = SIGNATURES.get(extension);
        if (expected == null) return null;

        byte[] head;
        try (InputStream in = Files.newInputStream(assembled)) {
            head = in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException e) {
            return "File could not be read";
        }

        if (expected.stream().noneMatch(sig -> startsWith(head, sig))) {
            log.warn("Rejected assembled upload '{}': content does not match .{} format", fileName, extension);
            return "File contents do not match its ." + extension + " extension";
        }
        return null;
    }

    private void validateSignature(MultipartFile file, String extension) {
        List<byte[]> expected = SIGNATURES.get(extension);
        if (expected == null) return;

        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException e) {
            throw new InvalidUploadException("File could not be read");
        }

        if (expected.stream().noneMatch(sig -> startsWith(head, sig))) {
            log.warn("Rejected upload '{}': content does not match .{} format", file.getOriginalFilename(), extension);
            throw new InvalidUploadException(
                    "File contents do not match its ." + extension + " extension");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
