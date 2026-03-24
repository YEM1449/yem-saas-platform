package com.yem.hlm.backend.document.service;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id);
    }
}
