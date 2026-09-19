package com.hrms.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hrms.cms.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Talks to cms-storage-service, which owns the STORAGE_BLOBS table. cms-backend keeps only the
 * returned objectId (in COMPLAINT_ATTACHMENTS.storage_path) and never touches the payload column.
 */
@Slf4j
@Component
public class StorageServiceClient {

    private final RestTemplate restTemplate;
    private final String blobsUrl;

    public StorageServiceClient(
            @Value("${cms.storage-service.base-url:http://localhost:8090/cms-storage}") String baseUrl,
            @Value("${cms.storage-service.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${cms.storage-service.read-timeout-ms:60000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restTemplate = new RestTemplate(factory);
        this.blobsUrl = baseUrl.replaceAll("/+$", "") + "/api/v1/storage/blobs";
    }

    /** Stores the payload and returns the objectId handle. */
    public String store(byte[] content, String fileName, String contentType, String bucket) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(parseOrOctetStream(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<Resource>(new NamedByteArrayResource(content, fileName), partHeaders));
        if (bucket != null) {
            body.add("bucket", bucket);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        JsonNode response = exchange(() -> restTemplate.postForObject(
                blobsUrl, new HttpEntity<>(body, headers), JsonNode.class));

        JsonNode objectId = response != null ? response.path("data").path("objectId") : null;
        if (objectId == null || objectId.isMissingNode() || objectId.asText().isEmpty()) {
            throw FileStorageException.storageUnavailable("upload response did not contain an objectId");
        }
        return objectId.asText();
    }

    public byte[] fetch(String objectId) {
        byte[] content = exchange(() -> restTemplate.getForObject(
                blobsUrl + "/{objectId}/content", byte[].class, objectId));

        if (content == null) {
            throw FileStorageException.storageUnavailable("empty payload for objectId " + objectId);
        }
        return content;
    }

    public void delete(String objectId) {
        exchange(() -> {
            restTemplate.delete(blobsUrl + "/{objectId}", objectId);
            return null;
        });
    }

    private <T> T exchange(StorageCall<T> call) {
        try {
            return call.execute();
        } catch (RestClientException e) {
            log.error("cms-storage-service call failed: {}", e.getMessage());
            throw FileStorageException.storageUnavailable(e.getMessage());
        }
    }

    private static MediaType parseOrOctetStream(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private interface StorageCall<T> {
        T execute();
    }

    /**
     * RestTemplate derives the multipart part filename from {@link Resource#getFilename()}, so the
     * original name has to be carried on the resource itself.
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(byte[] content, String fileName) {
            super(content);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
