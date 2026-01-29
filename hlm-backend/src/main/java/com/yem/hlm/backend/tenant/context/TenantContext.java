package com.yem.hlm.backend.tenant.context;

import java.util.UUID;

/**
 * TenantContext = "contexte de requête" pour ton SaaS multi-tenant.
 *
 * On stocke tenantId et userId pendant la durée de la requête.
 * ThreadLocal marche car dans un serveur web classique :
 * 1 requête HTTP ≈ 1 thread (pour la durée du traitement).
 *
 * ⚠️ IMPORTANT : toujours appeler clear() en fin de requête.
 */
public class TenantContext {
    // Stocke le tenantId pour le thread courant (donc la requête courante)
    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();

    // Stocke le userId pour le thread courant (donc la requête courante)
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();

    // Constructeur privé : empêche l'instanciation, c'est une classe utilitaire
    private TenantContext() {}

    /** Assigne le tenantId dans le contexte de la requête courante */
    public static void setTenantId(UUID tenantId) {
        TENANT_ID.set(tenantId);
    }

    /** Récupère le tenantId de la requête courante */
    public static UUID getTenantId() {
        return TENANT_ID.get();
    }

    /** Assigne le userId dans le contexte de la requête courante */
    public static void setUserId(UUID userId) {
        USER_ID.set(userId);
    }

    /** Récupère le userId de la requête courante */
    public static UUID getUserId() {
        return USER_ID.get();
    }

    /**
     * Nettoie le contexte.
     * remove() est préférable à set(null) (évite certaines fuites mémoire).
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
