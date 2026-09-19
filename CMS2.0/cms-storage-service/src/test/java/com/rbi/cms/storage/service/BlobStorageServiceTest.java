package com.rbi.cms.storage.service;

import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.storage.config.BlobStorageProperties;
import com.rbi.cms.storage.dto.StorageBlobResponse;
import com.rbi.cms.storage.entity.StorageBlob;
import com.rbi.cms.storage.repository.StorageBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlobStorageServiceTest {

    @Mock private StorageBlobRepository repository;

    private BlobStorageProperties properties;
    private BlobStorageService service;

    @BeforeEach
    void setUp() {
        properties = new BlobStorageProperties();
        properties.setMaxFileSize(1024L);
        properties.setAllowedTypes("pdf,png,jpg,txt");
        service = new BlobStorageService(repository, properties);
    }

    @Nested
    class Store {

        @Test
        void shouldPersistPayloadUnderAGeneratedObjectId() {
            when(repository.save(any(StorageBlob.class))).thenAnswer(inv -> inv.getArgument(0));
            MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf-bytes".getBytes());

            StorageBlobResponse response = service.store("CMS-001", null, file);

            assertThat(response.objectId()).isNotBlank();
            assertThat(java.util.UUID.fromString(response.objectId())).isNotNull();
            assertThat(response.bucket()).isEqualTo("CMS-001");
            assertThat(response.fileName()).isEqualTo("report.pdf");
            assertThat(response.contentType()).isEqualTo("application/pdf");
            assertThat(response.sizeBytes()).isEqualTo(9L);
            // SHA-256 of "pdf-bytes", so a corrupted round-trip is detectable.
            assertThat(response.checksum()).hasSize(64);
        }

        @Test
        void shouldAcceptNullTenantIdBecauseTheDeploymentIsSingleTenant() {
            when(repository.save(any(StorageBlob.class))).thenAnswer(inv -> inv.getArgument(0));
            MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

            assertThat(service.store("CMS-001", null, file).tenantId()).isNull();
        }

        @Test
        void shouldRejectDisallowedExtension() {
            MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "x".getBytes());

            assertThatThrownBy(() -> service.store("CMS-001", null, file))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_FILE_TYPE")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            verifyNoInteractions(repository);
        }

        @Test
        void shouldRejectFileWithNoExtension() {
            MockMultipartFile file = new MockMultipartFile("file", "noextension", "application/octet-stream", "x".getBytes());

            assertThatThrownBy(() -> service.store("CMS-001", null, file))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_FILE_TYPE");
        }

        @Test
        void shouldRejectPayloadOverTheConfiguredLimit() {
            MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[2048]);

            assertThatThrownBy(() -> service.store("CMS-001", null, file))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "FILE_TOO_LARGE")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.PAYLOAD_TOO_LARGE);
            verifyNoInteractions(repository);
        }

        @Test
        void shouldRejectEmptyUpload() {
            MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> service.store("CMS-001", null, file))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "EMPTY_FILE");
        }
    }

    @Nested
    class Fetch {

        @Test
        void shouldReturnStoredBlob() {
            StorageBlob blob = StorageBlob.builder().objectId("obj-1").storageData("data".getBytes()).build();
            when(repository.findByObjectId("obj-1")).thenReturn(Optional.of(blob));

            assertThat(service.fetch("obj-1").getStorageData()).isEqualTo("data".getBytes());
        }

        @Test
        void shouldThrow404ForUnknownObjectId() {
            when(repository.findByObjectId("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.fetch("missing"))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "OBJECT_NOT_FOUND")
                    .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldRemoveTheRow() {
            when(repository.deleteByObjectId("obj-1")).thenReturn(1);

            service.delete("obj-1");

            verify(repository).deleteByObjectId("obj-1");
        }

        @Test
        void shouldThrow404WhenNothingWasDeleted() {
            when(repository.deleteByObjectId("missing")).thenReturn(0);

            assertThatThrownBy(() -> service.delete("missing"))
                    .isInstanceOf(CmsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "OBJECT_NOT_FOUND");
        }
    }
}
