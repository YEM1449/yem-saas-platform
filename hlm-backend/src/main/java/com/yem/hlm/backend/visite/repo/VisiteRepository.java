package com.yem.hlm.backend.visite.repo;

import com.yem.hlm.backend.visite.domain.StatutVisite;
import com.yem.hlm.backend.visite.domain.Visite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Visite}. Every query is société-scoped (RG-V04 / multi-tenant rule).
 */
public interface VisiteRepository extends JpaRepository<Visite, UUID> {

    Optional<Visite> findBySocieteIdAndId(UUID societeId, UUID id);

    /** Agenda for one agent over a window (RG-V04 AGENT scope). */
    List<Visite> findBySocieteIdAndAgentIdAndDateHeureBetweenOrderByDateHeureAsc(
            UUID societeId, UUID agentId, Instant from, Instant to);

    /** Agenda for the whole société over a window (MANAGER/ADMIN scope). */
    List<Visite> findBySocieteIdAndDateHeureBetweenOrderByDateHeureAsc(
            UUID societeId, Instant from, Instant to);

    /** Société-wide window filtered by statut. */
    List<Visite> findBySocieteIdAndStatutAndDateHeureBetweenOrderByDateHeureAsc(
            UUID societeId, StatutVisite statut, Instant from, Instant to);

    /** All visites of a contact (newest first) — for the Contact "Visites" tab (P5-T1). */
    List<Visite> findBySocieteIdAndContactIdOrderByDateHeureDesc(UUID societeId, UUID contactId);

    /**
     * Conflict-detection candidates (RG-V05): non-cancelled visites of the same agent that
     * <em>start before</em> our slot ends and start no earlier than {@code borneBasse}
     * (= debut minus a window wider than any realistic duration). The exact overlap test
     * ({@code existing.getFin() > debut}) is applied in Java by the service, avoiding fragile
     * timestamp arithmetic in JPQL. {@code excludeId} skips the visite being updated (self).
     */
    @Query("""
           SELECT v FROM Visite v
           WHERE v.societeId = :societeId
             AND v.agent.id = :agentId
             AND v.statut NOT IN (com.yem.hlm.backend.visite.domain.StatutVisite.ANNULEE,
                                  com.yem.hlm.backend.visite.domain.StatutVisite.NO_SHOW)
             AND (:excludeId IS NULL OR v.id <> :excludeId)
             AND v.dateHeure < :fin
             AND v.dateHeure >= :borneBasse
           """)
    List<Visite> findConflitCandidats(@Param("societeId") UUID societeId,
                                      @Param("agentId") UUID agentId,
                                      @Param("borneBasse") Instant borneBasse,
                                      @Param("fin") Instant fin,
                                      @Param("excludeId") UUID excludeId);

    /** KPI "Visites réalisées" over a period (RG-V09). */
    long countBySocieteIdAndStatutAndDateHeureBetween(
            UUID societeId, StatutVisite statut, Instant from, Instant to);

    /** KPI conversion: visites whose résultat created an opportunité over a period (RG-V09). */
    long countBySocieteIdAndResultatAndDateHeureBetween(
            UUID societeId, com.yem.hlm.backend.visite.domain.ResultatVisite resultat,
            Instant from, Instant to);

    /** Agent-scoped variant of the réalisées KPI (RG-V04 — agent sees only own activity). */
    long countBySocieteIdAndAgentIdAndStatutAndDateHeureBetween(
            UUID societeId, UUID agentId, StatutVisite statut, Instant from, Instant to);

    /** Agent-scoped variant of the opportunité conversion KPI (RG-V04). */
    long countBySocieteIdAndAgentIdAndResultatAndDateHeureBetween(
            UUID societeId, UUID agentId, com.yem.hlm.backend.visite.domain.ResultatVisite resultat,
            Instant from, Instant to);
}
