package com.yem.hlm.backend.media.service;

import java.util.UUID;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(UUID id) {
        super("Media not found: " + id);
    }
}
