package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.ComplaintAttachmentData;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ComplaintAttachmentDataRepository;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock private ComplaintAttachmentRepository attachmentRepository;
    @Mock private ComplaintAttachmentDataRepository attachmentDataRepository;

    private FileStorageConfig config;
    private FileStorageService fileStorageService;

    /**
     * The SIZE and COUNT limits, backing {@link #configuredLimit}.
     *
     * <p>They are no longer taken from {@link FileStorageConfig}: that is an application.yml startup
     * snapshot, so {@code /api/files/upload} ignored the configured limit entirely. A test that sets
     * {@code config.setMaxFileSize(...)} is therefore turning a knob the product does not read, and
     * would pass whatever the validator did. Limits are set here the way an administrator sets them.
     */
    private final Map<String, String> liveConfig = new HashMap<>();

    @TempDir
    Path tempDir;

    /** Uploads are now signature-checked, so .pdf fixtures must actually start like a PDF. */
    private static final byte[] PDF = "%PDF-1.7 stub content".getBytes();

    private void configuredLimit(String key, long value) {
        liveConfig.put(key, String.valueOf(value));
    }

    @BeforeEach
    void setUp() throws IOException {
        config = new FileStorageConfig();
        config.setRootPath(tempDir.toString());
        config.setChunkSize(5242880L);
        config.setAllowedTypes("pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4");
        config.setTempDir("temp-chunks");

        Files.createDirectories(tempDir.resolve("temp-chunks"));

        // Generous by default so a fixture is never rejected on size or count unless a test says so:
        // this suite is about chunking and storage, not about the limit rule.
        configuredLimit(UploadLimitsService.KEY_MAX_FILE_BYTES, 52428800L);
        configuredLimit(UploadLimitsService.KEY_MAX_TOTAL_BYTES, 104857600L);
        configuredLimit(UploadLimitsService.KEY_MAX_FILE_COUNT, 10L);

        // A real SystemConfigService over a stubbed repository, rather than a mocked service: the
        // 30-second cache and the string→long parsing are then exercised as the product uses them, and
        // per-test limit changes need no re-stubbing.
        // lenient(): the storage/cleanup tests never reach a limit check, so strict stubs would fail them
        // for not consulting configuration. The stub describes the fixture, not an expected interaction.
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        lenient().when(repository.findByConfigKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(liveConfig.get(inv.getArgument(0)))
                        .map(value -> SystemConfig.builder().configKey(inv.getArgument(0)).configValue(value).build()));
        UploadLimitsService uploadLimits = new UploadLimitsService(new SystemConfigService(repository));

        fileStorageService = new FileStorageService(config, attachmentRepository, attachmentDataRepository,
                new FileUploadValidator(config, uploadLimits), uploadLimits);
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

            String freshUploadId = "fresh-upload";
            MockMultipartFile c0 = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF);
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
            MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF);
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
            verify(attachmentDataRepository).save(argThat(data ->
                    data.getAttachmentId().equals(1L) && Arrays.equals(data.getFileData(), PDF)));
        }

        @Test
        void shouldRejectDisallowedType() {
            MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "data".getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        void shouldRejectOversizedFile() {
            configuredLimit(UploadLimitsService.KEY_MAX_FILE_BYTES, 10L);
            MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", "a".repeat(100).getBytes());

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("maximum size");
        }

        @Test
        void shouldRejectDisguisedExecutable() {
            MockMultipartFile file = new MockMultipartFile("file", "payload.pdf", "application/pdf",
                    new byte[]{'M', 'Z', (byte) 0x90, 0x00});

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        void shouldRejectWhenMaxFilesReached() {
            configuredLimit(UploadLimitsService.KEY_MAX_FILE_COUNT, 2L);
            MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF);

            ComplaintAttachment existing1 = ComplaintAttachment.builder().id(1L).build();
            ComplaintAttachment existing2 = ComplaintAttachment.builder().id(2L).build();
            when(attachmentRepository.findByComplaintId(1L)).thenReturn(List.of(existing1, existing2));

            assertThatThrownBy(() -> fileStorageService.handleSingleUpload(file, "CMS-001", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Max files per complaint reached (2)");
        }
    }

    @Nested
    class ReadContent {

        @Test
        void shouldReturnContentFromDatabase() throws IOException {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).originalName("file.pdf").contentType("application/pdf").build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));
            ComplaintAttachmentData data = ComplaintAttachmentData.builder()
                    .attachmentId(1L).fileData(PDF).build();
            when(attachmentDataRepository.findById(1L)).thenReturn(Optional.of(data));

            Optional<FileStorageService.AttachmentContent> result = fileStorageService.readContent(1L);

            assertThat(result).isPresent();
            assertThat(result.get().fileName()).isEqualTo("file.pdf");
            assertThat(result.get().contentType()).isEqualTo("application/pdf");
            assertThat(result.get().bytes()).isEqualTo(PDF);
        }

        @Test
        void shouldFallBackToLegacyFileWhenNoDatabaseBytes() throws IOException {
            Path complaintDir = tempDir.resolve("CMS-001");
            Files.createDirectories(complaintDir);
            Path file = complaintDir.resolve("file.pdf");
            Files.write(file, PDF);

            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).originalName("file.pdf").contentType("application/pdf")
                    .storagePath("CMS-001/file.pdf").build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));
            when(attachmentDataRepository.findById(1L)).thenReturn(Optional.empty());

            Optional<FileStorageService.AttachmentContent> result = fileStorageService.readContent(1L);

            assertThat(result).isPresent();
            assertThat(result.get().bytes()).isEqualTo(PDF);
        }

        @Test
        void shouldReturnEmptyWhenAttachmentNotFound() {
            when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

            Optional<FileStorageService.AttachmentContent> result = fileStorageService.readContent(99L);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenBytesAreNowhereToBeFound() {
            ComplaintAttachment att = ComplaintAttachment.builder().id(1L).build();
            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(att));
            when(attachmentDataRepository.findById(1L)).thenReturn(Optional.empty());

            Optional<FileStorageService.AttachmentContent> result = fileStorageService.readContent(1L);

            assertThat(result).isEmpty();
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
