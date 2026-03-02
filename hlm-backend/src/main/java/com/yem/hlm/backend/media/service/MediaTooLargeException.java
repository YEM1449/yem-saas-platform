package com.yem.hlm.backend.media.service;

public class MediaTooLargeException extends RuntimeException {
    public MediaTooLargeException(long sizeBytes, long maxBytes) {
        super(String.format("File size %d bytes exceeds maximum allowed %d bytes", sizeBytes, maxBytes));
    }
}
