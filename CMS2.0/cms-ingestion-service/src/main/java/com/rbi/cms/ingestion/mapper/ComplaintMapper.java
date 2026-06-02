package com.rbi.cms.ingestion.mapper;

import com.rbi.cms.ingestion.dto.AttachmentResponse;
import com.rbi.cms.ingestion.dto.ComplaintDetailResponse;
import com.rbi.cms.ingestion.dto.ComplaintRegistrationRequest;
import com.rbi.cms.ingestion.entity.AttachmentMetadata;
import com.rbi.cms.ingestion.entity.ComplaintMaster;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ComplaintMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "complaintId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "assignedTeam", ignore = true)
    @Mapping(target = "slaDueDate", ignore = true)
    @Mapping(target = "resolutionSummary", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    ComplaintMaster toEntity(ComplaintRegistrationRequest request);

    @Mapping(target = "attachments", ignore = true)
    ComplaintDetailResponse toDetailResponse(ComplaintMaster entity);

    AttachmentResponse toAttachmentResponse(AttachmentMetadata metadata);

    List<AttachmentResponse> toAttachmentResponseList(List<AttachmentMetadata> metadataList);
}
