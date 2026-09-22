package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileUploadValidator (UST16/20/23)")
class FileUploadValidatorTest {

    private FileUploadValidator validator;

    private static final byte[] PDF_BYTES = "%PDF-1.7\nstub".getBytes();
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] EXE_BYTES = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0, 0, 0, 0, 0};

    @BeforeEach
    void setup() {
        FileStorageConfig config = new FileStorageConfig();
        config.setMaxFileSize(2 * 1024 * 1024);
        config.setAllowedTypes("pdf,png,jpg,jpeg,doc,docx,txt");
        validator = new FileUploadValidator(config);
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("should accept a genuine PDF")
        void shouldAcceptPdf() {
            var file = new MockMultipartFile("file", "complaint.pdf", "application/pdf", PDF_BYTES);
            validator.validate(file);
        }

        @Test
        @DisplayName("should accept a genuine PNG")
        void shouldAcceptPng() {
            var file = new MockMultipartFile("file", "reply.png", "image/png", PNG_BYTES);
            validator.validate(file);
        }

        @Test
        @DisplayName("should reject an executable renamed to .pdf even with a matching content type")
        void shouldRejectDisguisedExecutable() {
            var file = new MockMultipartFile("file", "payload.pdf", "application/pdf", EXE_BYTES);

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("should reject a disallowed extension")
        void shouldRejectDisallowedExtension() {
            var file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", EXE_BYTES);

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("should reject a path traversal attempt in the name")
        void shouldRejectTraversal() {
            var file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", PDF_BYTES);

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("invalid characters");
        }

        @Test
        @DisplayName("should reject a file over the configured size")
        void shouldRejectOversizedFile() {
            byte[] big = new byte[3 * 1024 * 1024];
            System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
            var file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class)
                    .hasMessageContaining("maximum size");
        }

        @Test
        @DisplayName("should reject an empty file")
        void shouldRejectEmptyFile() {
            var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(FileUploadValidator.InvalidUploadException.class);
        }

        @Test
        @DisplayName("should accept a .txt, which has no signature to verify")
        void shouldAcceptUnsignedFormat() {
            var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
            validator.validate(file);
        }
    }

    @Nested
    @DisplayName("checkAssembledSignature")
    class AssembledSignature {

        @Test
        @DisplayName("should pass a genuine PDF assembled from chunks")
        void shouldPassGenuinePdf(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
            Path file = Files.write(dir.resolve("assembled.pdf"), PDF_BYTES);

            assertThat(validator.checkAssembledSignature(file, "complaint.pdf")).isNull();
        }

        @Test
        @DisplayName("should fail an executable assembled under a .pdf name")
        void shouldFailDisguisedPayload(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
            Path file = Files.write(dir.resolve("assembled.pdf"), EXE_BYTES);

            assertThat(validator.checkAssembledSignature(file, "complaint.pdf"))
                    .contains("do not match");
        }
    }
}
