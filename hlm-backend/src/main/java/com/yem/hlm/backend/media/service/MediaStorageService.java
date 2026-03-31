package com.yem.hlm.backend.media.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over the underlying file storage backend.
 *
 * <p>Default implementation: {@link LocalFileMediaStorage} (stores files on local disk).
 * Production swap: provide an {@code @Primary} bean backed by
 * {@link ObjectStorageMediaStorage} (S3-compatible protocol,
 * works with OVH Object Storage, Scaleway, Hetzner, Cloudflare R2, MinIO, and AWS S3).
 */
public interface MediaStorageService {

    /**
     * Stores the given bytes under a generated key and returns that key.
     *
     * @param data            raw file bytes
     * @param originalFilename original upload filename (used to derive extension)
     * @param contentType     MIME type
     * @return the storage key that can be passed to {@link #load} or {@link #delete}
     */
    String store(byte[] data, String originalFilename, String contentType) throws IOException;

    /**
     * Streaming variant — reads from {@code stream} without buffering the entire file in heap.
     * Implementations that support true streaming (e.g. S3 SDK) should override this method.
     * The default implementation reads the stream to bytes (same as {@link #store(byte[], String, String)}).
     *
     * @param stream          input stream for the file data (consumed once; not closed by this method)
     * @param size            exact byte length of the stream (required by some SDK calls)
     * @param originalFilename original upload filename (used to derive extension)
     * @param contentType     MIME type
     * @return the storage key that can be passed to {@link #load} or {@link #delete}
     */
    default String store(InputStream stream, long size, String originalFilename, String contentType) throws IOException {
        return store(stream.readAllBytes(), originalFilename, contentType);
    }

    /**
     * Opens an {@link InputStream} for the given storage key.
     *
     * @param fileKey key returned by {@link #store}
     * @return stream of the stored file bytes
     */
    InputStream load(String fileKey) throws IOException;

    /**
     * Permanently removes the file identified by {@code fileKey}.
     *
     * @param fileKey key returned by {@link #store}
     */
    void delete(String fileKey) throws IOException;
}
