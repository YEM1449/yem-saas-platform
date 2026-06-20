package com.yem.hlm.backend.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Installs the field-encryption key into the static {@link FieldCrypto} holder at startup.
 *
 * <p>{@link EncryptedStringConverter} is instantiated by Hibernate (no Spring DI), so the key is
 * shared through {@code FieldCrypto}'s static state, populated here from
 * {@code app.security.field-encryption-key} (env {@code FIELD_ENCRYPTION_KEY}). A blank value leaves
 * encryption disabled (plaintext) for local/dev — see {@link FieldCrypto}.
 */
@Component
public class FieldCryptoInitializer {

    public FieldCryptoInitializer(@Value("${app.security.field-encryption-key:}") String base64Key) {
        FieldCrypto.init(base64Key);
    }
}
