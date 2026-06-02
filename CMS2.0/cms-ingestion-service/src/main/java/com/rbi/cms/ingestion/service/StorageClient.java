package com.rbi.cms.ingestion.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageClient {

    String store(String complaintId, MultipartFile file);

    byte[] retrieve(String storagePath);

    void delete(String storagePath);
}
