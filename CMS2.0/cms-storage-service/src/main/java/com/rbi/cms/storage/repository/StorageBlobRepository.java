package com.rbi.cms.storage.repository;

import com.rbi.cms.storage.dto.StorageBlobMetadata;
import com.rbi.cms.storage.entity.StorageBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StorageBlobRepository extends JpaRepository<StorageBlob, Long> {

    Optional<StorageBlob> findByObjectId(String objectId);

    boolean existsByObjectId(String objectId);

    /** Bulk delete so the BLOB payload is never loaded into memory just to remove the row. */
    @Modifying
    @Query("delete from StorageBlob b where b.objectId = :objectId")
    int deleteByObjectId(@Param("objectId") String objectId);

    @Query("""
            select new com.rbi.cms.storage.dto.StorageBlobMetadata(
                b.objectId, b.tenantId, b.bucket, b.fileName,
                b.contentType, b.sizeBytes, b.checksum, b.createdAt)
            from StorageBlob b
            where b.objectId = :objectId
            """)
    Optional<StorageBlobMetadata> findMetadataByObjectId(@Param("objectId") String objectId);

    @Query("""
            select new com.rbi.cms.storage.dto.StorageBlobMetadata(
                b.objectId, b.tenantId, b.bucket, b.fileName,
                b.contentType, b.sizeBytes, b.checksum, b.createdAt)
            from StorageBlob b
            where b.bucket = :bucket
            order by b.createdAt asc
            """)
    List<StorageBlobMetadata> findMetadataByBucket(@Param("bucket") String bucket);
}
