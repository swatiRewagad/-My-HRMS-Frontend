package com.hrms.cms.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileStorageConfigTest {

    private FileStorageConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        config = new FileStorageConfig();
        config.setRootPath(tempDir.toString());
        config.setTempDir("temp-chunks");
        config.setAllowedTypes("pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4,webm,ogg");
        config.setMaxFileSize(52428800L);
        config.setChunkSize(5242880L);
        config.setMaxFilesPerComplaint(10);
    }

    @Nested
    class IsAllowedType {

        @Test
        void shouldAllowPdf() {
            assertThat(config.isAllowedType("document.pdf")).isTrue();
        }

        @Test
        void shouldAllowPng() {
            assertThat(config.isAllowedType("image.png")).isTrue();
        }

        @Test
        void shouldAllowJpg() {
            assertThat(config.isAllowedType("photo.jpg")).isTrue();
        }

        @Test
        void shouldAllowJpeg() {
            assertThat(config.isAllowedType("photo.jpeg")).isTrue();
        }

        @Test
        void shouldAllowDoc() {
            assertThat(config.isAllowedType("file.doc")).isTrue();
        }

        @Test
        void shouldAllowDocx() {
            assertThat(config.isAllowedType("file.docx")).isTrue();
        }

        @Test
        void shouldAllowXls() {
            assertThat(config.isAllowedType("sheet.xls")).isTrue();
        }

        @Test
        void shouldAllowXlsx() {
            assertThat(config.isAllowedType("sheet.xlsx")).isTrue();
        }

        @Test
        void shouldAllowTxt() {
            assertThat(config.isAllowedType("notes.txt")).isTrue();
        }

        @Test
        void shouldAllowCsv() {
            assertThat(config.isAllowedType("data.csv")).isTrue();
        }

        @Test
        void shouldAllowZip() {
            assertThat(config.isAllowedType("archive.zip")).isTrue();
        }

        @Test
        void shouldAllowMp4() {
            assertThat(config.isAllowedType("video.mp4")).isTrue();
        }

        @Test
        void shouldRejectExe() {
            assertThat(config.isAllowedType("virus.exe")).isFalse();
        }

        @Test
        void shouldRejectBat() {
            assertThat(config.isAllowedType("script.bat")).isFalse();
        }

        @Test
        void shouldRejectSh() {
            assertThat(config.isAllowedType("script.sh")).isFalse();
        }

        @Test
        void shouldRejectNull() {
            assertThat(config.isAllowedType(null)).isFalse();
        }

        @Test
        void shouldBeCaseInsensitive() {
            assertThat(config.isAllowedType("IMAGE.PNG")).isTrue();
            assertThat(config.isAllowedType("DOC.PDF")).isTrue();
        }
    }

    @Nested
    class GetComplaintDir {

        @Test
        void shouldReturnPathWithComplaintNumber() {
            Path result = config.getComplaintDir("CMS-20260515-ABC123");

            assertThat(result.toString()).contains("CMS-20260515-ABC123");
            assertThat(result.getParent().toString()).isEqualTo(tempDir.toString());
        }
    }

    @Nested
    class GetTempChunkDir {

        @Test
        void shouldReturnTempPath() {
            Path result = config.getTempChunkDir();

            assertThat(result.toString()).contains("temp-chunks");
            assertThat(result.getParent().toString()).isEqualTo(tempDir.toString());
        }
    }

    @Nested
    class Init {

        @Test
        void shouldCreateDirectories() throws Exception {
            Path newRoot = tempDir.resolve("new-root");
            config.setRootPath(newRoot.toString());
            config.setTempDir("chunks");

            config.init();

            assertThat(newRoot.toFile().exists()).isTrue();
            assertThat(newRoot.resolve("chunks").toFile().exists()).isTrue();
        }
    }

    @Nested
    class DefaultValues {

        @Test
        void shouldHaveCorrectDefaults() {
            FileStorageConfig defaults = new FileStorageConfig();

            assertThat(defaults.getMaxFileSize()).isEqualTo(52428800L);
            assertThat(defaults.getChunkSize()).isEqualTo(5242880L);
            assertThat(defaults.getMaxFilesPerComplaint()).isEqualTo(10);
            assertThat(defaults.getRootPath()).isEqualTo("/data/cms-attachments");
            assertThat(defaults.getTempDir()).isEqualTo("temp-chunks");
        }
    }
}
