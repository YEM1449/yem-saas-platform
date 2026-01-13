package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter : s'exécute une seule fois par requête.
 *
 * Rôle :
 * - lire Authorization: Bearer <token>
 * - valider le token
 * - extraire userId + tenantId
 * - initialiser SecurityContext (Spring Security)
 * - initialiser TenantContext (multi-tenant)
 * - nettoyer en fin de requête (très important)
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Service JWT (créé en AUTH-04) : generate/validate/extract
    private final JwtProvider jwtProvider;

    // Injection par constructeur : rend le filtre testable et propre
    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Méthode appelée automatiquement par Spring Security à chaque requête.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,   // la requête entrante
            HttpServletResponse response, // la réponse HTTP
            FilterChain filterChain       // la chaîne de filtres à continuer
    ) throws ServletException, IOException {

        try {
            // 1) Lire le header Authorization
            String authHeader = request.getHeader("Authorization");

            // 2) Si le header n'existe pas ou ne commence pas par "Bearer ",
            //    on ne fait rien et on laisse la requête continuer.
            //    Spring Security décidera ensuite si la route est publique ou protégée.
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return; // on sort du filtre ici
            }

            // 3) Extraire le token (après "Bearer ")
            //    "Bearer " fait 7 caractères.
            String token = authHeader.substring(7).trim();

            // 4) Valider le token : signature + expiration
            //    Si invalide, on ne met rien dans les contextes et on continue.
            //    La sécurité refusera ensuite l'accès aux endpoints protégés.
            if (!jwtProvider.isValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 5) Extraire userId (subject) et tenantId (claim "tid")
            var userId = jwtProvider.extractUserId(token);
            var tenantId = jwtProvider.extractTenantId(token);

            // 6) Remplir le TenantContext (utile pour le multi-tenant dans tes services)
            TenantContext.setUserId(userId);
            TenantContext.setTenantId(tenantId);

            // 7) Remplir le SecurityContext (utile pour Spring Security)
            //    Ici on crée une Authentication minimale :
            //    - principal = userId (string)
            //    - credentials = null (jamais stocké)
            //    - authorities = vide (roles viendront plus tard)
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(),
                    null,
                    List.of()
            );

            // 8) Stocker cette authentication dans le contexte de sécurité
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 9) Continuer vers le prochain filtre / controller
            filterChain.doFilter(request, response);

        } finally {
            // 10) Nettoyage OBLIGATOIRE :
            // - évite qu'un tenant "reste" dans le ThreadLocal pour une autre requête
            // - évite des bugs inter-tenant très graves (fuites de données)
            TenantContext.clear();

            // Nettoyage SecurityContext aussi (bonne hygiène)
            SecurityContextHolder.clearContext();
        }
    }
}
