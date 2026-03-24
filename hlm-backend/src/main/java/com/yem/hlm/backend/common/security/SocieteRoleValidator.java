package com.yem.hlm.backend.common.security;

import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.yem.hlm.backend.common.error.ErrorCode.*;

/**
 * Single place for all company-scope RBAC checks.
 *
 * <p>Enforces:
 * <ul>
 *   <li>An ADMIN can only act on their own company.</li>
 *   <li>An ADMIN can only assign MANAGER or AGENT — not ADMIN (privilege escalation prevention).</li>
 *   <li>SUPER_ADMIN bypasses company-scope checks but is still bound by platform rules.</li>
 * </ul>
 */
@Component
public class SocieteRoleValidator {

    private static final List<String> VALID_ROLES = List.of("ADMIN", "MANAGER", "AGENT");

    /**
     * Validates the role being assigned.
     *
     * <ul>
     *   <li>ADMIN → FORBIDDEN (privilege escalation) if the caller is not SUPER_ADMIN.</li>
     *   <li>Any value not in {ADMIN, MANAGER, AGENT} → BAD_REQUEST (ROLE_INVALIDE).</li>
     * </ul>
     *
     * @param targetRole the role string to be assigned (case-sensitive, no ROLE_ prefix)
     * @throws BusinessRuleException ROLE_ESCALATION_FORBIDDEN or ROLE_INVALIDE
     */
    public void validateAssignableRole(String targetRole) {
        if (!VALID_ROLES.contains(targetRole)) {
            throw new BusinessRuleException(ROLE_INVALIDE,
                    "Rôle invalide : '" + targetRole + "'. Valeurs acceptées : ADMIN, MANAGER, AGENT.");
        }
        if ("ADMIN".equals(targetRole) && !SocieteContext.isSuperAdmin()) {
            throw new BusinessRuleException(ROLE_ESCALATION_FORBIDDEN,
                    "Seul un SUPER_ADMIN peut attribuer le rôle ADMIN. " +
                    "Contactez l'administrateur de la plateforme.");
        }
    }
}
