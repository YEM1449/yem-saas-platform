package com.yem.hlm.backend.contact.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateContactRequest(
        @Size(max = 120)
        String firstName,
        @Size(max = 120)
        String lastName,
        @Size(max = 30)
        String phone,
        @Email
        @Size(max = 160)
        String email,
        @Size(max = 100)
        String nationalId,
        @Size(max = 500)
        String address,
        @Size(max = 2000)
        String notes
) {
    public UpdateContactRequest {
        firstName = normalizeTrim(firstName);
        lastName = normalizeTrim(lastName);
        phone = normalizeTrim(phone);
        email = normalizeLower(email);
        nationalId = normalizeTrim(nationalId);
        address = normalizeTrim(address);
        notes = normalizeTrim(notes);
    }

    private static String normalizeLower(String value) {
        if (value == null) return null;
        var v = value.trim();
        return v.isEmpty() ? null : v.toLowerCase();
    }

    private static String normalizeTrim(String value) {
        if (value == null) return null;
        var v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
