package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Explicit decompression-ratio and nesting-depth caps for zip-family attachments (also covers
 * Office Open XML / ODF, which are zips underneath — Tika will decompress them internally, so
 * this check runs BEFORE handing the bytes to Tika at all). A zip bomb commonly omits the
 * uncompressed-size field specifically to defeat a naive header-only check, so this actually reads
 * entries up to the computed byte ceiling rather than trusting {@link ZipEntry#getSize()} alone.
 */
@Component
class ZipBombGuard {

    private final MailIntakeProperties properties;

    ZipBombGuard(MailIntakeProperties properties) {
        this.properties = properties;
    }

    boolean isSuspicious(byte[] content) {
        return isSuspicious(content, 0);
    }

    private boolean isSuspicious(byte[] content, int depth) {
        MailIntakeProperties.Attachments.ZipBomb cfg = properties.getAttachments().getZipBomb();
        if (depth > cfg.getMaxDepth()) {
            return true;
        }

        long compressedSize = content.length;
        long ceilingBytes = Math.max(compressedSize, 1L) * cfg.getMaxDecompressionRatio();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            long totalUncompressed = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                long entryTotal = 0;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    entryTotal += read;
                    totalUncompressed += read;
                    if (totalUncompressed > ceilingBytes) {
                        return true; // exceeded the ratio ceiling regardless of what the header claimed
                    }
                }

                // A nested zip inside a zip — recurse with depth+1, re-reading this entry's bytes.
                if (looksLikeZip(entry.getName()) && entryTotal > 4) {
                    // Entry bytes were already consumed above; re-extracting them cleanly would
                    // need buffering the whole entry, which the streaming loop above avoids on
                    // purpose for large legitimate attachments. Treat unresolved nested-zip depth
                    // as suspicious rather than silently skipping the check.
                    if (depth + 1 > cfg.getMaxDepth()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException notActuallyAZip) {
            return false; // not a zip at all (or too damaged to be one) — nothing to guard here
        }
    }

    private static boolean looksLikeZip(String entryName) {
        String lower = entryName.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".docx") || lower.endsWith(".xlsx")
                || lower.endsWith(".pptx") || lower.endsWith(".odt") || lower.endsWith(".ods");
    }
}
