package com.yem.hlm.backend.media.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Default {@link MediaStorageService} implementation that writes files to the local filesystem.
 *
 * <p>Storage root is configured via {@code app.media.storage-dir} (default {@code ./uploads}).
 * Swap for an S3-backed implementation by providing an {@code @Primary} bean.
 */
@Service
public class LocalFileMediaStorage implements MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileMediaStorage.class);

    private final Path storageRoot;

    public LocalFileMediaStorage(
            @Value("${app.media.storage-dir:./uploads}") String storageDir) throws IOException {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(this.storageRoot);
        log.info("[MEDIA-STORAGE] Local storage root: {}", this.storageRoot);
    }

    @Override
    public String store(byte[] data, String originalFilename, String contentType) throws IOException {
        String ext = extractExtension(originalFilename);
        String fileKey = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = storageRoot.resolve(fileKey);
        Files.write(target, data);
        log.debug("[MEDIA-STORAGE] Stored {} bytes → {}", data.length, fileKey);
        return fileKey;
    }

    @Override
    public InputStream load(String fileKey) throws IOException {
        Path file = resolveAndValidate(fileKey);
        return new ByteArrayInputStream(Files.readAllBytes(file));
    }

    @Override
    public void delete(String fileKey) throws IOException {
        Path file = resolveAndValidate(fileKey);
        Files.deleteIfExists(file);
        log.debug("[MEDIA-STORAGE] Deleted {}", fileKey);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Path resolveAndValidate(String fileKey) throws IOException {
        Path resolved = storageRoot.resolve(fileKey).normalize();
        // Path traversal guard
        if (!resolved.startsWith(storageRoot)) {
            throw new IOException("Invalid file key: path traversal detected");
        }
        return resolved;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }
}
