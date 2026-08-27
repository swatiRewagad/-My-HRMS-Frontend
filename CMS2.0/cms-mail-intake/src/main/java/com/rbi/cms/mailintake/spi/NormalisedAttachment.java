package com.rbi.cms.mailintake.spi;

import lombok.Builder;

@Builder
public record NormalisedAttachment(
        String filename,
        String detectedContentType,
        long sizeBytes,
        /** Populated when Tika extraction succeeded; null otherwise — a handler must not assume
         *  it's present. */
        String extractedText,
        String storeUri
) {}
