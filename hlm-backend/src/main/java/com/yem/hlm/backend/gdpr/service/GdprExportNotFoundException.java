package com.yem.hlm.backend.gdpr.service;

import java.util.UUID;

/** Thrown when the requested contact is not found within the tenant for a GDPR data export. */
public class GdprExportNotFoundException extends RuntimeException {

    public GdprExportNotFoundException(UUID contactId) {
        super("Contact not found for GDPR export: " + contactId);
    }
}
