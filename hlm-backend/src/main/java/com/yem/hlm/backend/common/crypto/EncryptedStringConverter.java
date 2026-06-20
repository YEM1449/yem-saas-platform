package com.yem.hlm.backend.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently encrypts a {@code String} column at rest with {@link FieldCrypto} (AES-256-GCM).
 *
 * <p>Apply with {@code @Convert(converter = EncryptedStringConverter.class)} on CNDP-sensitive
 * identity fields (CIN / passport numbers). Reads decrypt automatically (legacy plaintext rows are
 * returned verbatim), so DTOs, exports and the in-Java CIN matching in {@code GroupClientService}
 * keep working on cleartext — only the stored bytes are ciphertext (DA-010).
 *
 * <p>Not {@code autoApply} — it is opt-in per field so non-sensitive strings are never touched.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldCrypto.decrypt(dbData);
    }
}
