package com.yem.hlm.backend.gdpr.service;

import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Generates the CNDP/RGPD processing register (registre des traitements) for a société.
 *
 * <p>Per RGPD Art. 30 and Law 09-08 Art. 20, controllers must maintain a record of
 * processing activities. This service returns a structured register entry for the
 * CRM contact management processing activity.
 */
@Service
public class ProcessingRegisterService {

    private final SocieteRepository societeRepository;

    public ProcessingRegisterService(SocieteRepository societeRepository) {
        this.societeRepository = societeRepository;
    }

    public ProcessingRegisterResponse buildRegister(UUID societeId) {
        Societe s = societeRepository.findById(societeId).orElse(null);

        String controller = s != null && s.getNom() != null ? s.getNom() : "—";
        String dpo = s != null && s.getDpoNom() != null ? s.getDpoNom() : "—";
        String dpoEmail = s != null && s.getEmailDpo() != null ? s.getEmailDpo() : "—";
        String numeroCndp = s != null && s.getNumeroCndp() != null ? s.getNumeroCndp() : "—";
        String numeroCnil = s != null && s.getNumeroCnil() != null ? s.getNumeroCnil() : "—";
        String retention = s != null && s.getDureeRetentionJours() != null
                ? s.getDureeRetentionJours() + " jours"
                : "5 ans (1825 jours)";

        ProcessingActivity contactActivity = new ProcessingActivity(
                "Gestion des contacts immobiliers",
                controller,
                dpo,
                dpoEmail,
                "Prospection, suivi commercial, gestion des contrats de vente",
                List.of("CONSENT", "CONTRACT", "LEGITIMATE_INTEREST"),
                List.of("Nom, prénom, email, téléphone, adresse, CIN/CNIE",
                        "Statut commercial (prospect, client)", "Historique des échanges"),
                List.of("Équipe commerciale interne", "Administration"),
                List.of("Prestataires CRM SaaS (sous-traitants contractuels)"),
                retention,
                List.of("Chiffrement AES-256 au repos", "TLS 1.2+ en transit",
                        "Contrôle d'accès basé sur les rôles (RBAC)", "Journalisation des accès"),
                numeroCndp,
                numeroCnil
        );

        return new ProcessingRegisterResponse(
                "1.0",
                LocalDate.now().toString(),
                List.of(contactActivity)
        );
    }

    public record ProcessingRegisterResponse(
            String version,
            String generatedAt,
            List<ProcessingActivity> activities
    ) {}

    public record ProcessingActivity(
            String name,
            String controller,
            String dpoName,
            String dpoEmail,
            String purpose,
            List<String> legalBases,
            List<String> dataCategories,
            List<String> internalRecipients,
            List<String> externalRecipients,
            String retentionPeriod,
            List<String> securityMeasures,
            String numeroCndp,
            String numeroCnil
    ) {}
}
