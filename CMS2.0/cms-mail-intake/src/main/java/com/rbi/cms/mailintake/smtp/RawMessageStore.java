package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.common.crypto.PayloadEncryptionService;
import com.rbi.cms.mailintake.config.MailIntakeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;

/**
 * Encrypts and durably writes raw message bytes to disk. "Durably" means: write to a temp file,
 * fsync it, then atomically rename into place — a crash between those steps leaves either nothing
 * or a complete file, never a half-written one masquerading as complete. The returned URI is an
 * opaque UUID-based filename — never anything derived from message content (brief: "Never derive
 * a filesystem path from an untrusted filename").
 *
 * {@link InboundEmailIngestService} calls this BEFORE the database row commits — see that class
 * for the ordering that satisfies rule 1 (never ack before durability).
 */
@Component
@RequiredArgsConstructor
public class RawMessageStore {

    private final MailIntakeProperties properties;
    private final PayloadEncryptionService encryptionService;

    /** Returns a store URI (relative path under the configured base) that can be handed to
     *  {@link #read(String)} later. Throws on any I/O failure — the caller must not proceed to
     *  commit a DB row referencing a URI that didn't durably land. */
    public String store(byte[] rawBytes) throws IOException {
        Path baseDir = Paths.get(properties.getStorage().getRawMessageBasePath());
        Files.createDirectories(baseDir);

        String filename = UUID.randomUUID() + ".eml.enc";
        Path target = baseDir.resolve(filename);
        Path temp = baseDir.resolve(filename + ".tmp");

        byte[] encrypted = encryptionService.encrypt(rawBytes);

        try (FileChannel channel = FileChannel.open(temp,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true); // fsync data + metadata before the rename below
        }

        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    public byte[] read(String storeUri) throws IOException {
        Path path = Paths.get(properties.getStorage().getRawMessageBasePath()).resolve(storeUri);
        byte[] encrypted = Files.readAllBytes(path);
        return encryptionService.decrypt(encrypted);
    }

    /** Best-effort cleanup for the narrow race where this connection's write lost to a
     *  concurrent delivery of the same content — see InboundEmailIngestService. Never throws;
     *  an orphaned blob is a disk-space nuisance, not a correctness problem, so a failed delete
     *  here must not turn into a 451 for a message we've already durably accepted (under the
     *  winning connection's row). */
    public void deleteBestEffort(String storeUri) {
        try {
            Path path = Paths.get(properties.getStorage().getRawMessageBasePath()).resolve(storeUri);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Deliberately swallowed — see Javadoc above.
        }
    }
}
