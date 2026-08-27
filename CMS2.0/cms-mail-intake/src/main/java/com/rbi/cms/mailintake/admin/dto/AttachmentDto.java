package com.rbi.cms.mailintake.admin.dto;

import com.rbi.cms.mailintake.entity.InboundEmailAttachment;

public record AttachmentDto(
        Long id,
        String filename,
        String declaredContentType,
        String detectedContentType,
        long sizeBytes,
        String scanStatus
) {
    public static AttachmentDto from(InboundEmailAttachment attachment) {
        return new AttachmentDto(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getDeclaredContentType(),
                attachment.getDetectedContentType(),
                attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes(),
                attachment.getScanStatus().name());
    }
}
