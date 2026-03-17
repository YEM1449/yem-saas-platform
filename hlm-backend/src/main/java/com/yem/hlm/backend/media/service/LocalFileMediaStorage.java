package com.yem.hlm.backend.media.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p>Active by default (when {@code app.media.object-storage.enabled} is false or absent).
 * Replaced by {@link ObjectStorageMediaStorage} when {@code app.media.object-storage.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "app.media.object-storage.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileMediaStorage implements MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileMediaStorage.class);

    private final Path storageRoot;

    public LocalFileMediaStorage(
            @Value("${app.media.storage-dir:./uploads}") String storageDir) {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
            log.info("[MEDIA-STORAGE] Local storage root: {}", this.storageRoot);
        } catch (IOException e) {
            log.warn("[MEDIA-STORAGE] Cannot create storage directory {} at startup: {} — " +
                     "will retry on first upload. Set MEDIA_STORAGE_DIR to a writable path.",
                     this.storageRoot, e.getMessage());
        }
    }

    @Override
    public String store(byte[] data, String originalFilename, String contentType) throws IOException {
        Files.createDirectories(storageRoot);
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
