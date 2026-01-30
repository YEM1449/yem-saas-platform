package com.yem.hlm.backend.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires de TenantContext.
 *
 * Objectif :
 * - vérifier le stockage ThreadLocal
 * - vérifier clear() (pas de fuite entre tests / requêtes)
 */
class TenantContextTest {

    /**
     * Nettoyage systématique après chaque test
     * (bonne hygiène car ThreadLocal est statique).
     */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void set_and_get_should_work() {
        // Arrange : ids simulés
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Act : on les met dans le contexte
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);

        // Assert : on récupère les mêmes valeurs
        assertEquals(tenantId, TenantContext.getTenantId());
        assertEquals(userId, TenantContext.getUserId());
    }

    @Test
    void clear_should_remove_values() {
        // Arrange
        TenantContext.setTenantId(UUID.randomUUID());
        TenantContext.setUserId(UUID.randomUUID());

        // Act : nettoyage
        TenantContext.clear();

        // Assert : ThreadLocal doit être vide
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
    }
}
