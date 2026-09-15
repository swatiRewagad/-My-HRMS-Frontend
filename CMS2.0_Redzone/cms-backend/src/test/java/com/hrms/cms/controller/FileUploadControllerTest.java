package com.hrms.cms.controller;

import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.service.FileStorageService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @Nested
    class UploadChunk {

        @Test
        void shouldReturnOkForValidChunk() throws Exception {
            ChunkUploadResponse response = ChunkUploadResponse.builder()
                    .uploadId("upload-1").chunkIndex(0).totalChunks(3)
                    .complete(false).message("Chunk 1/3 received").build();

            when(fileStorageService.handleChunkUpload(any(), eq("upload-1"), eq(0), eq(3),
                    eq("test.pdf"), eq("CMS-001"), eq(1L), eq(5000L)))
                    .thenReturn(response);

            MockMultipartFile chunk = new MockMultipartFile("file", "test.pdf", "application/pdf", "chunk-data".getBytes());

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(chunk)
                            .param("uploadId", "upload-1")
                            .param("chunkIndex", "0")
                            .param("totalChunks", "3")
                            .param("fileName", "test.pdf")
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1")
                            .param("totalFileSize", "5000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadId").value("upload-1"))
                    .andExpect(jsonPath("$.complete").value(false));
        }

        @Test
        void shouldReturnBadRequestForDisallowedType() throws Exception {
            ChunkUploadResponse response = ChunkUploadResponse.builder()
                    .message("File type not allowed. Allowed: pdf,png,jpg")
                    .complete(false).build();

            when(fileStorageService.handleChunkUpload(any(), any(), anyInt(), anyInt(),
                    any(), any(), anyLong(), anyLong()))
                    .thenReturn(response);

            MockMultipartFile chunk = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "data".getBytes());

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(chunk)
                            .param("uploadId", "up-1")
                            .param("chunkIndex", "0")
                            .param("totalChunks", "1")
                            .param("fileName", "bad.exe")
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1")
                            .param("totalFileSize", "100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not allowed")));
        }

        @Test
        void shouldReturnCompleteWhenAllChunksReceived() throws Exception {
            ChunkUploadResponse response = ChunkUploadResponse.builder()
                    .uploadId("upload-1").chunkIndex(2).totalChunks(3)
                    .complete(true).attachmentId(10L).fileName("report.pdf")
                    .storagePath("CMS-001/stored.pdf").message("Upload complete").build();

            when(fileStorageService.handleChunkUpload(any(), any(), anyInt(), anyInt(),
                    any(), any(), anyLong(), anyLong()))
                    .thenReturn(response);

            MockMultipartFile chunk = new MockMultipartFile("file", "report.pdf", "application/pdf", "last".getBytes());

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(chunk)
                            .param("uploadId", "upload-1")
                            .param("chunkIndex", "2")
                            .param("totalChunks", "3")
                            .param("fileName", "report.pdf")
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1")
                            .param("totalFileSize", "300"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complete").value(true))
                    .andExpect(jsonPath("$.attachmentId").value(10));
        }
    }

    @Nested
    class UploadSingle {

        @Test
        void shouldReturnAttachment() throws Exception {
            ComplaintAttachment attachment = ComplaintAttachment.builder()
                    .id(1L).complaintId(1L).fileName("stored.pdf")
                    .originalName("doc.pdf").contentType("application/pdf")
                    .fileSize(1024L).storagePath("CMS-001/stored.pdf").build();

            when(fileStorageService.handleSingleUpload(any(), eq("CMS-001"), eq(1L)))
                    .thenReturn(attachment);

            MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalName").value("doc.pdf"))
                    .andExpect(jsonPath("$.storagePath").value("CMS-001/stored.pdf"));
        }
    }

    @Nested
    class Download {

        @Test
        void shouldReturnFileResource() throws Exception {
            Path file = tempDir.resolve("test.pdf");
            Files.writeString(file, "PDF Content");

            when(fileStorageService.getFilePath(1L)).thenReturn(file);

            mockMvc.perform(get("/api/files/download/1"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"));
        }

        @Test
        void shouldReturn404WhenFileNotExists() throws Exception {
            Path nonExistent = tempDir.resolve("missing.pdf");
            when(fileStorageService.getFilePath(99L)).thenReturn(nonExistent);

            mockMvc.perform(get("/api/files/download/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Stream {

        @Test
        void shouldReturn206ForRangeRequest() throws Exception {
            Path file = tempDir.resolve("video.mp4");
            Files.write(file, new byte[10240]);

            when(fileStorageService.getFilePath(1L)).thenReturn(file);

            mockMvc.perform(get("/api/files/stream/1")
                            .header("Range", "bytes=0-1023"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Accept-Ranges", "bytes"));
        }

        @Test
        void shouldReturn206WithoutRangeHeader() throws Exception {
            Path file = tempDir.resolve("audio.mp3");
            Files.write(file, new byte[5000]);

            when(fileStorageService.getFilePath(2L)).thenReturn(file);

            mockMvc.perform(get("/api/files/stream/2"))
                    .andExpect(status().isPartialContent());
        }

        @Test
        void shouldReturn404WhenStreamFileNotExists() throws Exception {
            Path missing = tempDir.resolve("ghost.mp4");
            when(fileStorageService.getFilePath(99L)).thenReturn(missing);

            mockMvc.perform(get("/api/files/stream/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ListAttachments {

        @Test
        void shouldReturnAttachmentsForComplaint() throws Exception {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).complaintId(5L).originalName("file.pdf").build();
            when(fileStorageService.getAttachments(5L)).thenReturn(List.of(att));

            mockMvc.perform(get("/api/files/complaint/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[0].originalName").value("file.pdf"));
        }
    }

    @Nested
    class DeleteAttachment {

        @Test
        void shouldReturnNoContent() throws Exception {
            doNothing().when(fileStorageService).deleteAttachment(1L);

            mockMvc.perform(delete("/api/files/1"))
                    .andExpect(status().isNoContent());

            verify(fileStorageService).deleteAttachment(1L);
        }
    }

    @Nested
    class Cleanup {

        @Test
        void shouldReturnOkMessage() throws Exception {
            doNothing().when(fileStorageService).cleanupStaleTempUploads();

            mockMvc.perform(post("/api/files/cleanup"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Cleanup initiated"));
        }
    }
}
