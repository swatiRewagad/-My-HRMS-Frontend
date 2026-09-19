package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.exception.FileStorageException;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock private ComplaintAttachmentRepository attachmentRepository;
    @Mock private StorageServiceClient storageServiceClient;

    private FileStorageConfig config;
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        config = new FileStorageConfig();
        config.setRootPath(tempDir.toString());
        config.setMaxFileSize(52428800L);
        config.setChunkSize(5242880L);
        config.setAllowedTypes("pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4");
        config.setMaxFilesPerComplaint(10);
        config.setTempDir("temp-chunks");

        Files.createDirectories(tempDir.resolve("temp-chunks"));

        fileStorageService = new FileStorageService(config, attachmentRepository, storageServiceClient);
    }

    @Nested
    class HandleChunkUpload {

        @Test
        void shouldRejectDisallowedFileType() {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.exe", "application/octet-stream", "data".getBytes());

            assertThatThrownBy(() -> fileStorageService.handleChunkUpload(
                    chunk, "upload-1", 0, 2, "test.exe", "CMS-001", 1L, 1000L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_FILE_TYPE")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        void shouldRejectOversizedFile() {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

            assertThatThrownBy(() -> fileStorageService.handleChunkUpload(
                    chunk, "upload-1", 0, 2, "test.pdf", "CMS-001", 1L, 100_000_000L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "FILE_TOO_LARGE")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        @Test
        void shouldStoreChunkAndReturnIncomplete() throws IOException {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.pdf", "application/pdf", "chunk-data".getBytes());

            ChunkUploadResponse response = fileStorageService.handleChunkUpload(
                    chunk, "upload-1", 0, 3, "test.pdf", "CMS-001", 1L, 5000L
            );

            assertThat(response.isComplete()).isFalse();
            assertThat(response.getUploadId()).isEqualTo("upload-1");
            assertThat(response.getChunkIndex()).isEqualTo(0);
            assertThat(response.getMessage()).contains("1/3");
            verifyNoInteractions(storageServiceClient);
        }

        @Test
        void shouldPushAssembledBytesToStorageWhenAllChunksReceived() throws IOException {
            when(storageServiceClient.store(any(), anyString(), anyString(), anyString())).thenReturn("object-uuid-1");
            when(attachmentRepository.save(any(ComplaintAttachment.class))).thenAnswer(inv -> {
                ComplaintAttachment a = inv.getArgument(0);
                a.setId(10L);
                return a;
            });

            String uploadId = "fresh-upload";
            ChunkUploadResponse r0 = fileStorageService.handleChunkUpload(
                    new MockMultipartFile("file", "report.pdf", "application/pdf", "part1".getBytes()),
                    uploadId, 0, 2, "report.pdf", "CMS-003", 3L, 100L);
            assertThat(r0.isComplete()).isFalse();

            ChunkUploadResponse r1 = fileStorageService.handleChunkUpload(
                    new MockMultipartFile("file", "report.pdf", "application/pdf", "part2".getBytes()),
                    uploadId, 1, 2, "report.pdf", "CMS-003", 3L, 100L);

            assertThat(r1.isComplete()).isTrue();
            assertThat(r1.getAttachmentId()).isEqualTo(10L);
            assertThat(r1.getStoragePath()).isEqualTo("object-uuid-1");
            assertThat(r1.getMessage()).isEqualTo("Upload complete");

            ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
            ArgumentCaptor<String> storedName = ArgumentCaptor.forClass(String.class);
            verify(storageServiceClient).store(payload.capture(), storedName.capture(),
                    eq("application/pdf"), eq("CMS-003"));
            assertThat(payload.getValue()).isEqualTo("part1part2".getBytes());
            assertThat(storedName.getValue()).endsWith("_report.pdf");
            assertThat(Files.exists(config.getTempChunkDir().resolve(uploadId))).isFalse();
        }

        @Test
        void shouldGenerateUploadIdWhenNull() throws IOException {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

            ChunkUploadResponse response = fileStorageService.handleChunkUpload(
                    chunk, null, 0, 2, "test.pdf", "CMS-001", 1L, 5000L
            );

            assertThat(response.getUploadId()).isNotNull();
            assertThat(response.getUploadId()).isNotEmpty();
        }
    }

    @Nested
    class HandleSingleUpload {

        @Test
        void shouldStoreStorageObjectIdAsStoragePath() throws IOException {
            MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf-content".getBytes());
            when(storageServiceClient.store(any(), anyString(), anyString(), anyString())).thenReturn("object-uuid-1");
            when(attachmentRepository.findByComplaintId(1L)).thenReturn(Collections.emptyList());
            when(attachmentRepository.save(any(ComplaintAttachment.class))).thenAnswer(inv -> {
                ComplaintAttachment a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

            ComplaintAttachment result = fileStorageService.handleSingleUpload(file, "CMS-001", 1L);

            assertThat(result.getOriginalName()).isEqualTo("report.pdf");
            assertThat(result.getContentType()).isEqualTo("application/pdf");
            assertThat(result.getComplaintId()).isEqualTo(1L);
            assertThat(result.getStoragePath()).isEqualTo("object-uuid-1");
            assertThat(result.getFileSize()).isEqualTo("pdf-content".length());
        }

        @Test
        void shouldRejectDisallowedType() {
            MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "data".getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_FILE_TYPE")
                    .hasMessageContaining("File type not allowed");
            verifyNoInteractions(storageServiceClient);
        }

        @Test
        void shouldRejectOversizedFile() {
            config.setMaxFileSize(10L);
            MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", "a".repeat(100).getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "FILE_TOO_LARGE");
            verifyNoInteractions(storageServiceClient);
        }

        @Test
        void shouldRejectWhenMaxFilesReached() {
            config.setMaxFilesPerComplaint(2);
            MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

            when(attachmentRepository.findByComplaintId(1L)).thenReturn(List.of(
                    ComplaintAttachment.builder().id(1L).build(),
                    ComplaintAttachment.builder().id(2L).build()));

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "ATTACHMENT_LIMIT_REACHED")
                    .hasMessage("Max files per complaint reached (2)");
            verifyNoInteractions(storageServiceClient);
        }
    }

    @Nested
    class GetFileBytes {

        @Test
        void shouldFetchPayloadByStoredObjectId() {
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(
                    ComplaintAttachment.builder().id(1L).storagePath("object-uuid-1").build()));
            when(storageServiceClient.fetch("object-uuid-1")).thenReturn("payload".getBytes());

            assertThat(fileStorageService.getFileBytes(1L)).isEqualTo("payload".getBytes());
        }

        @Test
        void shouldThrowWhenAttachmentNotFound() {
            when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileStorageService.getFileBytes(99L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "ATTACHMENT_NOT_FOUND")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteAttachment {

        @Test
        void shouldDeleteBlobThenRecord() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).storagePath("object-uuid-1").build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));

            fileStorageService.deleteAttachment(1L);

            InOrder order = inOrder(storageServiceClient, attachmentRepository);
            order.verify(storageServiceClient).delete("object-uuid-1");
            order.verify(attachmentRepository).delete(att);
        }

        @Test
        void shouldThrowWhenDeletingNonExistentAttachment() {
            when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileStorageService.deleteAttachment(99L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "ATTACHMENT_NOT_FOUND");
            verifyNoInteractions(storageServiceClient);
        }
    }

    @Nested
    class GetAttachments {

        @Test
        void shouldReturnAttachmentsForComplaint() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).complaintId(5L).originalName("test.pdf").build();
            when(attachmentRepository.findByComplaintId(5L)).thenReturn(List.of(att));

            assertThat(fileStorageService.getAttachments(5L)).hasSize(1);
        }
    }

    @Nested
    class DownloadAllAttachmentsAsZip {

        @Test
        void shouldWriteOneEntryPerAttachment() throws IOException {
            when(attachmentRepository.findByComplaintId(5L)).thenReturn(List.of(
                    ComplaintAttachment.builder().id(1L).storagePath("obj-a").originalName("a.pdf").build(),
                    ComplaintAttachment.builder().id(2L).storagePath("obj-b").originalName("b.pdf").build()));
            when(storageServiceClient.fetch("obj-a")).thenReturn("aaa".getBytes());
            when(storageServiceClient.fetch("obj-b")).thenReturn("bbb".getBytes());

            assertThat(zipEntries(5L)).containsExactly("a.pdf", "b.pdf");
        }

        @Test
        void shouldDisambiguateDuplicateFileNames() throws IOException {
            when(attachmentRepository.findByComplaintId(5L)).thenReturn(List.of(
                    ComplaintAttachment.builder().id(1L).storagePath("obj-a").originalName("scan.pdf").build(),
                    ComplaintAttachment.builder().id(2L).storagePath("obj-b").originalName("scan.pdf").build()));
            when(storageServiceClient.fetch(anyString())).thenReturn("data".getBytes());

            assertThat(zipEntries(5L)).containsExactly("scan.pdf", "scan_2.pdf");
        }

        @Test
        void shouldSkipAttachmentsMissingFromStorage() throws IOException {
            when(attachmentRepository.findByComplaintId(5L)).thenReturn(List.of(
                    ComplaintAttachment.builder().id(1L).storagePath("gone").originalName("a.pdf").build(),
                    ComplaintAttachment.builder().id(2L).storagePath("obj-b").originalName("b.pdf").build()));
            when(storageServiceClient.fetch("gone")).thenThrow(FileStorageException.storageUnavailable("404"));
            when(storageServiceClient.fetch("obj-b")).thenReturn("bbb".getBytes());

            assertThat(zipEntries(5L)).containsExactly("b.pdf");
        }

        @Test
        void shouldThrowWhenComplaintHasNoAttachments() {
            when(attachmentRepository.findByComplaintId(7L)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> fileStorageService.downloadAllAttachmentsAsZip(7L))
                    .isInstanceOf(FileStorageException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "NO_ATTACHMENTS");
        }

        private List<String> zipEntries(Long complaintId) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fileStorageService.downloadAllAttachmentsAsZip(complaintId).writeTo(out);

            List<String> names = new ArrayList<>();
            try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    names.add(entry.getName());
                    assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isNotEmpty();
                }
            }
            return names;
        }
    }

    @Nested
    class CleanupStaleTempUploads {

        @Test
        void shouldNotThrowWhenTempDirMissing() {
            config.setTempDir("nonexistent-dir");
            fileStorageService.cleanupStaleTempUploads();
        }

        @Test
        void shouldCleanStaleDirectories() throws IOException {
            Path chunkDir = config.getTempChunkDir().resolve("stale-upload");
            Files.createDirectories(chunkDir);
            Files.writeString(chunkDir.resolve("chunk_00000"), "old data");

            chunkDir.toFile().setLastModified(System.currentTimeMillis() - 7200_000L);

            fileStorageService.cleanupStaleTempUploads();

            assertThat(Files.exists(chunkDir)).isFalse();
        }

        @Test
        void shouldNotCleanRecentDirectories() throws IOException {
            Path chunkDir = config.getTempChunkDir().resolve("recent-upload");
            Files.createDirectories(chunkDir);
            Files.writeString(chunkDir.resolve("chunk_00000"), "fresh data");

            fileStorageService.cleanupStaleTempUploads();

            assertThat(Files.exists(chunkDir)).isTrue();
        }
    }
}
