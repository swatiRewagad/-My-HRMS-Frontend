package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock private ComplaintAttachmentRepository attachmentRepository;

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

        fileStorageService = new FileStorageService(config, attachmentRepository);
    }

    @Nested
    class HandleChunkUpload {

        @Test
        void shouldRejectDisallowedFileType() throws IOException {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.exe", "application/octet-stream", "data".getBytes());

            ChunkUploadResponse response = fileStorageService.handleChunkUpload(
                    chunk, "upload-1", 0, 2, "test.exe", "CMS-001", 1L, 1000L
            );

            assertThat(response.isComplete()).isFalse();
            assertThat(response.getMessage()).contains("not allowed");
        }

        @Test
        void shouldRejectOversizedFile() throws IOException {
            MockMultipartFile chunk = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

            ChunkUploadResponse response = fileStorageService.handleChunkUpload(
                    chunk, "upload-1", 0, 2, "test.pdf", "CMS-001", 1L, 100_000_000L
            );

            assertThat(response.isComplete()).isFalse();
            assertThat(response.getMessage()).contains("exceeds maximum size");
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
        }

        @Test
        void shouldAssembleWhenAllChunksReceived() throws IOException {
            when(attachmentRepository.save(any(ComplaintAttachment.class))).thenAnswer(inv -> {
                ComplaintAttachment a = inv.getArgument(0);
                a.setId(10L);
                return a;
            });

            String uploadId = "upload-complete";
            for (int i = 0; i < 2; i++) {
                MockMultipartFile chunk = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                        ("chunk" + i).getBytes());
                fileStorageService.handleChunkUpload(chunk, uploadId, i, 2, "doc.pdf", "CMS-002", 2L, 100L);
            }

            // After second chunk, should be complete
            MockMultipartFile lastChunk = new MockMultipartFile("file", "doc.pdf", "application/pdf", "last".getBytes());
            // Re-upload chunk index 1 to trigger assembly
            Path chunkDir = config.getTempChunkDir().resolve(uploadId);
            // Chunks already exist from loop above, verify response
            // The second iteration of the loop already assembled, let's test fresh:

            String freshUploadId = "fresh-upload";
            MockMultipartFile c0 = new MockMultipartFile("file", "report.pdf", "application/pdf", "part1".getBytes());
            ChunkUploadResponse r0 = fileStorageService.handleChunkUpload(c0, freshUploadId, 0, 2, "report.pdf", "CMS-003", 3L, 100L);
            assertThat(r0.isComplete()).isFalse();

            MockMultipartFile c1 = new MockMultipartFile("file", "report.pdf", "application/pdf", "part2".getBytes());
            ChunkUploadResponse r1 = fileStorageService.handleChunkUpload(c1, freshUploadId, 1, 2, "report.pdf", "CMS-003", 3L, 100L);
            assertThat(r1.isComplete()).isTrue();
            assertThat(r1.getAttachmentId()).isEqualTo(10L);
            assertThat(r1.getMessage()).isEqualTo("Upload complete");
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
        void shouldUploadFileSuccessfully() throws IOException {
            MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf-content".getBytes());
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
            assertThat(result.getStoragePath()).startsWith("CMS-001/");
        }

        @Test
        void shouldRejectDisallowedType() {
            MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "data".getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File type not allowed");
        }

        @Test
        void shouldRejectOversizedFile() {
            config.setMaxFileSize(10L);
            MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", "a".repeat(100).getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File exceeds max size");
        }

        @Test
        void shouldRejectWhenMaxFilesReached() {
            config.setMaxFilesPerComplaint(2);
            MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

            ComplaintAttachment existing1 = ComplaintAttachment.builder().id(1L).build();
            ComplaintAttachment existing2 = ComplaintAttachment.builder().id(2L).build();
            when(attachmentRepository.findByComplaintId(1L)).thenReturn(List.of(existing1, existing2));

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Max files per complaint reached (2)");
        }
    }

    @Nested
    class GetFilePath {

        @Test
        void shouldReturnPathForExistingAttachment() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).storagePath("CMS-001/file.pdf").build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));

            Path result = fileStorageService.getFilePath(1L);

            assertThat(result.toString()).contains("CMS-001");
            assertThat(result.toString()).contains("file.pdf");
        }

        @Test
        void shouldThrowWhenAttachmentNotFound() {
            when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileStorageService.getFilePath(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Attachment not found");
        }
    }

    @Nested
    class DeleteAttachment {

        @Test
        void shouldDeleteFileAndRecord() throws IOException {
            Path complaintDir = tempDir.resolve("CMS-001");
            Files.createDirectories(complaintDir);
            Path file = complaintDir.resolve("stored.pdf");
            Files.writeString(file, "content");

            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).storagePath("CMS-001/stored.pdf").build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));

            fileStorageService.deleteAttachment(1L);

            assertThat(Files.exists(file)).isFalse();
            verify(attachmentRepository).delete(att);
        }

        @Test
        void shouldThrowWhenDeletingNonExistentAttachment() {
            when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileStorageService.deleteAttachment(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Attachment not found");
        }
    }

    @Nested
    class GetAttachments {

        @Test
        void shouldReturnAttachmentsForComplaint() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).complaintId(5L).originalName("test.pdf").build();
            when(attachmentRepository.findByComplaintId(5L)).thenReturn(List.of(att));

            List<ComplaintAttachment> result = fileStorageService.getAttachments(5L);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class CleanupStaleTempUploads {

        @Test
        void shouldNotThrowWhenTempDirMissing() throws IOException {
            config.setTempDir("nonexistent-dir");
            // Just verify no exception
            fileStorageService.cleanupStaleTempUploads();
        }

        @Test
        void shouldCleanStaleDirectories() throws IOException {
            Path chunkDir = config.getTempChunkDir().resolve("stale-upload");
            Files.createDirectories(chunkDir);
            Path chunkFile = chunkDir.resolve("chunk_00000");
            Files.writeString(chunkFile, "old data");

            // Set modification time to 2 hours ago
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
