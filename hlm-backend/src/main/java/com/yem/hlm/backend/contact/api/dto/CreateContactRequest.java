package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.common.validation.PhoneOrEmailRequired;
import com.yem.hlm.backend.common.validation.PhoneOrEmailTarget;
import com.yem.hlm.backend.contact.domain.ConsentMethod;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PhoneOrEmailRequired
public record CreateContactRequest(
        @NotBlank
        @Size(max = 120)
        String firstName,
        @NotBlank
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
        String notes,
        // GDPR / Law 09-08 consent fields (all nullable — defaults applied in service)
        Boolean consentGiven,
        ConsentMethod consentMethod,
        ProcessingBasis processingBasis
) implements PhoneOrEmailTarget {
    public CreateContactRequest {
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
