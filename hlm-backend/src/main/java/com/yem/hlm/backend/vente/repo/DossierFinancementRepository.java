package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.DossierFinancement;
import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierFinancementRepository extends JpaRepository<DossierFinancement, UUID> {

    Optional<DossierFinancement> findBySocieteIdAndVenteId(UUID societeId, UUID venteId);

    /**
     * Financing files whose granted-agreement deadline falls within [today, until] and that
     * are not yet definitively approved/refused — feeds the "accord bancaire expire bientôt"
     * alert. System sweep (not société-scoped).
     */
    List<DossierFinancement> findByStatutInAndDateExpirationAccordBetween(
            List<StatutDossierFinancement> statuts, LocalDate from, LocalDate to);

    /** Société-scoped count of financing agreements expiring within a window (trésorerie alerts). */
    long countBySocieteIdAndStatutInAndDateExpirationAccordBetween(
            UUID societeId, List<StatutDossierFinancement> statuts, LocalDate from, LocalDate to);
}
