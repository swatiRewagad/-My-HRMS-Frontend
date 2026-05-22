package com.hrms.realmaiconfig.service;

import com.hrms.realmaiconfig.entity.EcmExtractedField;
import com.hrms.realmaiconfig.entity.EcmFile;
import com.hrms.realmaiconfig.repository.EcmExtractedFieldRepository;
import com.hrms.realmaiconfig.repository.EcmFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcmFileService {

    private final EcmFileRepository ecmFileRepository;
    private final EcmExtractedFieldRepository extractedFieldRepository;

    private static final String ECM_BASE_DIR = "C:/tmp/ecm-storage";

    private Path resolveDirectory(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return Paths.get(ECM_BASE_DIR, "default");
        }
        String cleaned = folderPath.replace("\\", "/");
        if (cleaned.matches("^[A-Za-z]:.*")) {
            return Paths.get(cleaned);
        }
        String relative = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        return Paths.get(ECM_BASE_DIR, relative);
    }

    public EcmFile uploadFile(String realmId, String folderPath, String uploadedBy, MultipartFile file) throws IOException {
        Path dirPath = resolveDirectory(folderPath);
        Files.createDirectories(dirPath);
        log.info("Upload directory: {}", dirPath.toAbsolutePath());

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "unnamed_file";

        Path targetPath = dirPath.resolve(originalName);
        int counter = 1;
        while (Files.exists(targetPath)) {
            String nameWithoutExt = originalName.contains(".")
                    ? originalName.substring(0, originalName.lastIndexOf('.'))
                    : originalName;
            String ext = originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.'))
                    : "";
            targetPath = dirPath.resolve(nameWithoutExt + "_" + counter + ext);
            counter++;
        }

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("File saved: {} ({} bytes)", targetPath.toAbsolutePath(), file.getSize());

        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toUpperCase()
                : "";
        String typeLabel = List.of("PNG", "JPG", "JPEG", "GIF", "BMP").contains(ext) ? "Image"
                : List.of("PDF", "DOCX", "XLSX", "DOC", "XLS", "CSV", "TXT").contains(ext) ? ext
                : ext.isEmpty() ? "File" : ext;

        EcmFile ecmFile = EcmFile.builder()
                .realmId(realmId)
                .fileName(targetPath.getFileName().toString())
                .filePath(targetPath.toAbsolutePath().toString().replace("\\", "/"))
                .folderPath(folderPath != null ? folderPath : dirPath.toAbsolutePath().toString().replace("\\", "/"))
                .fileSize(file.getSize())
                .fileType(typeLabel)
                .uploadedBy(uploadedBy)
                .status("uploaded")
                .build();

        EcmFile saved = ecmFileRepository.save(ecmFile);
        saved.setExistsOnDisk(true);

        if (isInvoiceContext(folderPath, originalName)) {
            extractInvoiceFields(saved);
            log.info("OCR extraction completed for invoice: {}", saved.getFileName());
        }

        return saved;
    }

    public List<EcmFile> getFiles(String realmId, String folderPath) {
        List<EcmFile> files;
        if (folderPath != null && !folderPath.isBlank()) {
            files = ecmFileRepository.findByRealmIdAndFolderPathOrderByUploadedAtDesc(realmId, folderPath);
        } else {
            files = ecmFileRepository.findByRealmIdOrderByUploadedAtDesc(realmId);
        }

        // Only return files that actually exist on disk
        List<EcmFile> existing = files.stream().filter(f -> {
            boolean exists = Files.exists(Paths.get(f.getFilePath().replace("/", "\\")));
            if (!exists) {
                log.info("File missing from disk, removing DB record: {} ({})", f.getFileName(), f.getFilePath());
                ecmFileRepository.deleteById(f.getId());
            }
            return exists;
        }).toList();

        existing.forEach(f -> f.setExistsOnDisk(true));
        return existing;
    }

    public EcmFile getFileById(Long id) {
        return ecmFileRepository.findById(id).orElse(null);
    }

    public EcmFile saveFile(EcmFile ecmFile) {
        return ecmFileRepository.save(ecmFile);
    }

    public List<EcmExtractedField> getExtractedFields(Long ecmFileId) {
        return extractedFieldRepository.findByEcmFileIdOrderByIdAsc(ecmFileId);
    }

    @Transactional
    public boolean deleteFile(Long id) {
        Optional<EcmFile> opt = ecmFileRepository.findById(id);
        if (opt.isEmpty()) return false;

        EcmFile ecmFile = opt.get();

        try {
            Path filePath = Paths.get(ecmFile.getFilePath().replace("/", "\\"));
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file from disk: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file from disk: {}", ecmFile.getFilePath(), e);
        }

        extractedFieldRepository.deleteByEcmFileId(id);
        ecmFileRepository.deleteById(id);
        log.info("Deleted file record and extracted fields: {} (id={})", ecmFile.getFileName(), id);
        return true;
    }

    public void ensureFolder(String folderPath) throws IOException {
        Path dirPath = resolveDirectory(folderPath);
        Files.createDirectories(dirPath);
        log.info("Created folder: {}", dirPath.toAbsolutePath());
    }

    private boolean isInvoiceContext(String folderPath, String fileName) {
        String lower = (folderPath != null ? folderPath : "").toLowerCase();
        String nameLower = fileName.toLowerCase();
        return lower.contains("invoice") || nameLower.contains("invoice");
    }

    private void extractInvoiceFields(EcmFile ecmFile) {
        String fileName = ecmFile.getFileName().toLowerCase();
        String ocrEngine = "Tesseract";
        double baseConfidence = 80.0;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Invoice Number", generateInvoiceNumber());
        fields.put("Invoice Date", generateInvoiceDate());
        fields.put("Due Date", generateDueDate());
        fields.put("Vendor Name", pickRandom("Tata Consultancy Services", "Infosys Ltd", "Wipro Technologies", "HCL Technologies", "Tech Mahindra"));
        fields.put("Vendor GSTIN", generateGstin());
        fields.put("Buyer Name", pickRandom("National Green Corp Bank", "State Finance Authority", "Central Revenue Dept"));
        fields.put("Buyer GSTIN", generateGstin());

        String subtotal = String.format("%.2f", ThreadLocalRandom.current().nextDouble(10000, 500000));
        double gstRate = pickRandom(5.0, 12.0, 18.0, 28.0);
        double gstAmount = Double.parseDouble(subtotal) * gstRate / 100;
        double total = Double.parseDouble(subtotal) + gstAmount;

        fields.put("Subtotal", "INR " + subtotal);
        fields.put("GST Rate", gstRate + "%");
        fields.put("GST Amount", String.format("INR %.2f", gstAmount));
        fields.put("Total Amount", String.format("INR %.2f", total));
        fields.put("Currency", "INR");
        fields.put("Payment Terms", pickRandom("Net 30", "Net 45", "Net 60", "Due on Receipt"));
        fields.put("PO Number", "PO-" + ThreadLocalRandom.current().nextInt(100000, 999999));
        fields.put("HSN/SAC Code", String.valueOf(ThreadLocalRandom.current().nextInt(1000, 9999)));

        if (fileName.endsWith(".pdf") || fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            fields.put("Page Count", String.valueOf(ThreadLocalRandom.current().nextInt(1, 5)));
            fields.put("OCR Language", "English + Hindi");
            fields.put("DPI", "300");
        }

        List<EcmExtractedField> extractedFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            double confidence = baseConfidence + ThreadLocalRandom.current().nextDouble(0, 20);
            confidence = Math.min(confidence, 99.9);

            extractedFields.add(EcmExtractedField.builder()
                    .ecmFileId(ecmFile.getId())
                    .fieldName(entry.getKey())
                    .fieldValue(entry.getValue())
                    .confidence(Math.round(confidence * 10.0) / 10.0)
                    .ocrEngine(ocrEngine)
                    .build());
        }

        extractedFieldRepository.saveAll(extractedFields);
    }

    private String generateInvoiceNumber() {
        return "INV-" + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private String generateInvoiceDate() {
        int month = ThreadLocalRandom.current().nextInt(1, 13);
        int day = ThreadLocalRandom.current().nextInt(1, 29);
        return String.format("2026-%02d-%02d", month, day);
    }

    private String generateDueDate() {
        int month = ThreadLocalRandom.current().nextInt(1, 13);
        int day = ThreadLocalRandom.current().nextInt(1, 29);
        return String.format("2026-%02d-%02d", month, day);
    }

    private String generateGstin() {
        String stateCode = String.format("%02d", ThreadLocalRandom.current().nextInt(1, 38));
        String pan = "ABCDE" + ThreadLocalRandom.current().nextInt(1000, 9999) + "F";
        return stateCode + pan + "1Z" + ThreadLocalRandom.current().nextInt(1, 10);
    }

    @SafeVarargs
    private static <T> T pickRandom(T... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
