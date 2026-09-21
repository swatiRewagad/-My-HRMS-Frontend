package com.hrms.cms.controller;

import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.exception.FileStorageException;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.service.EncryptionKeyService;
import com.hrms.cms.service.FileStorageService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FileStorageService fileStorageService;
    @MockBean private EmailDraftAttachmentRepository draftAttachmentRepository;

    // PiiDecryptionFilter is a Filter bean, so @WebMvcTest pulls it into the slice while leaving its
    // @Service dependency out. Without this the context fails to start.
    @MockBean private EncryptionKeyService encryptionKeyService;

    private static ComplaintAttachment attachment(Long id, String name, String contentType) {
        return ComplaintAttachment.builder()
                .id(id).complaintId(5L).fileName("stored_" + name).originalName(name)
                .contentType(contentType).fileSize(11L).storagePath("object-uuid-" + id).build();
    }

    @Nested
    class UploadChunk {

        @Test
        void shouldReturnOkForValidChunk() throws Exception {
            when(fileStorageService.handleChunkUpload(any(), eq("upload-1"), eq(0), eq(3),
                    eq("test.pdf"), eq("CMS-001"), eq(1L), eq(5000L)))
                    .thenReturn(ChunkUploadResponse.builder()
                            .uploadId("upload-1").chunkIndex(0).totalChunks(3)
                            .complete(false).message("Chunk 1/3 received").build());

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(new MockMultipartFile("file", "test.pdf", "application/pdf", "chunk-data".getBytes()))
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
        void shouldReturn415ForDisallowedType() throws Exception {
            when(fileStorageService.handleChunkUpload(any(), any(), anyInt(), anyInt(),
                    any(), any(), anyLong(), anyLong()))
                    .thenThrow(FileStorageException.unsupportedType("pdf,png,jpg"));

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(new MockMultipartFile("file", "bad.exe", "application/octet-stream", "data".getBytes()))
                            .param("uploadId", "up-1")
                            .param("chunkIndex", "0")
                            .param("totalChunks", "1")
                            .param("fileName", "bad.exe")
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1")
                            .param("totalFileSize", "100"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.data.errorCode").value("UNSUPPORTED_FILE_TYPE"))
                    .andExpect(jsonPath("$.message").value(containsString("not allowed")));
        }

        @Test
        void shouldReturnCompleteWhenAllChunksReceived() throws Exception {
            when(fileStorageService.handleChunkUpload(any(), any(), anyInt(), anyInt(),
                    any(), any(), anyLong(), anyLong()))
                    .thenReturn(ChunkUploadResponse.builder()
                            .uploadId("upload-1").chunkIndex(2).totalChunks(3)
                            .complete(true).attachmentId(10L).fileName("report.pdf")
                            .storagePath("object-uuid-10").message("Upload complete").build());

            mockMvc.perform(multipart("/api/files/upload/chunk")
                            .file(new MockMultipartFile("file", "report.pdf", "application/pdf", "last".getBytes()))
                            .param("uploadId", "upload-1")
                            .param("chunkIndex", "2")
                            .param("totalChunks", "3")
                            .param("fileName", "report.pdf")
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1")
                            .param("totalFileSize", "300"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complete").value(true))
                    .andExpect(jsonPath("$.attachmentId").value(10))
                    .andExpect(jsonPath("$.storagePath").value("object-uuid-10"));
        }
    }

    @Nested
    class UploadSingle {

        @Test
        void shouldReturnAttachmentCarryingTheStorageObjectId() throws Exception {
            when(fileStorageService.handleSingleUpload(any(), eq("CMS-001"), eq(1L)))
                    .thenReturn(attachment(1L, "doc.pdf", "application/pdf"));

            mockMvc.perform(multipart("/api/files/upload")
                            .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes()))
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalName").value("doc.pdf"))
                    .andExpect(jsonPath("$.storagePath").value("object-uuid-1"));
        }

        @Test
        void shouldReturn409WhenAttachmentLimitReached() throws Exception {
            when(fileStorageService.handleSingleUpload(any(), any(), anyLong()))
                    .thenThrow(FileStorageException.attachmentLimitReached(10));

            mockMvc.perform(multipart("/api/files/upload")
                            .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes()))
                            .param("complaintNumber", "CMS-001")
                            .param("complaintId", "1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.errorCode").value("ATTACHMENT_LIMIT_REACHED"));
        }
    }

    @Nested
    class ListAttachments {

        @Test
        void shouldWrapAttachmentsInApiResponse() throws Exception {
            when(fileStorageService.getAttachments(5L)).thenReturn(List.of(
                    attachment(1L, "file.pdf", "application/pdf"),
                    attachment(2L, "scan.png", "image/png")));

            mockMvc.perform(get("/api/files/complaint/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Attachments fetched successfully"))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].originalName").value("file.pdf"))
                    .andExpect(jsonPath("$.data[0].storagePath").value("object-uuid-1"));
        }
    }

    @Nested
    class Download {

        @Test
        void shouldReturnBlobBytesAsAttachment() throws Exception {
            when(fileStorageService.getAttachmentMetadata(1L)).thenReturn(attachment(1L, "test.pdf", "application/pdf"));
            when(fileStorageService.getFileBytes(1L)).thenReturn("PDF Content".getBytes());

            mockMvc.perform(get("/api/files/download/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("test.pdf")))
                    .andExpect(content().bytes("PDF Content".getBytes()));
        }

        @Test
        void shouldReturn404WhenAttachmentMissing() throws Exception {
            when(fileStorageService.getAttachmentMetadata(99L))
                    .thenThrow(FileStorageException.attachmentNotFound(99L));

            mockMvc.perform(get("/api/files/download/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.data.errorCode").value("ATTACHMENT_NOT_FOUND"));
        }
    }

    @Nested
    class Stream {

        @Test
        void shouldReturn206ForRangeRequest() throws Exception {
            when(fileStorageService.getAttachmentMetadata(1L)).thenReturn(attachment(1L, "video.mp4", "video/mp4"));
            when(fileStorageService.getFileBytes(1L)).thenReturn(new byte[10240]);

            mockMvc.perform(get("/api/files/stream/1").header("Range", "bytes=0-1023"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Accept-Ranges", "bytes"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline;")))
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 1024L));
        }

        @Test
        void shouldReturn206WithFirstMegabyteWhenNoRangeHeader() throws Exception {
            when(fileStorageService.getAttachmentMetadata(2L)).thenReturn(attachment(2L, "audio.mp3", "audio/mpeg"));
            when(fileStorageService.getFileBytes(2L)).thenReturn(new byte[5000]);

            mockMvc.perform(get("/api/files/stream/2"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5000L));
        }

        @Test
        void shouldReturn404WhenAttachmentMissing() throws Exception {
            when(fileStorageService.getAttachmentMetadata(99L))
                    .thenThrow(FileStorageException.attachmentNotFound(99L));

            mockMvc.perform(get("/api/files/stream/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DownloadAll {

        @Test
        void shouldStreamZipNamedAfterTheComplaint() throws Exception {
            when(fileStorageService.downloadAllAttachmentsAsZip(5L))
                    .thenReturn(outputStream -> outputStream.write("zip-bytes".getBytes()));

            mockMvc.perform(get("/api/files/complaint/5/download-all"))
                    .andExpect(request().asyncStarted())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            containsString("complaint_5_attachments.zip")))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
        }

        @Test
        void shouldReturn404WhenComplaintHasNoAttachments() throws Exception {
            when(fileStorageService.downloadAllAttachmentsAsZip(7L))
                    .thenThrow(new FileStorageException("No attachments found for complaint 7",
                            org.springframework.http.HttpStatus.NOT_FOUND, "NO_ATTACHMENTS"));

            mockMvc.perform(get("/api/files/complaint/7/download-all"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.data.errorCode").value("NO_ATTACHMENTS"));
        }
    }

    @Nested
    class DeleteAttachment {

        @Test
        void shouldReturnNoContent() throws Exception {
            mockMvc.perform(delete("/api/files/1"))
                    .andExpect(status().isNoContent());

            verify(fileStorageService).deleteAttachment(1L);
        }
    }

    @Nested
    class Cleanup {

        @Test
        void shouldReturnOkMessage() throws Exception {
            mockMvc.perform(post("/api/files/cleanup"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Cleanup initiated"));
        }
    }
}
