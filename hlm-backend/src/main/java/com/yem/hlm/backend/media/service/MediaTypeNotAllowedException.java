package com.yem.hlm.backend.media.service;

public class MediaTypeNotAllowedException extends RuntimeException {
    public MediaTypeNotAllowedException(String contentType) {
        super("Media type not allowed: " + contentType);
    }
}
