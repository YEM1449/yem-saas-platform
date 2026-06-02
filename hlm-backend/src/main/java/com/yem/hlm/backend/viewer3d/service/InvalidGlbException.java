package com.yem.hlm.backend.viewer3d.service;

/**
 * Thrown when an uploaded GLB fails server-side binary validation: bad glTF magic,
 * unsupported container version, or missing {@code KHR_draco_mesh_compression}
 * extension. Enforces RG-E05 (never trust the client {@code dracoCompressed} flag
 * alone). Mapped to HTTP 422 (INVALID_GLB_FILE).
 */
public class InvalidGlbException extends RuntimeException {
    public InvalidGlbException(String message) {
        super(message);
    }
}
