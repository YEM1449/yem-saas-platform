package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.ReserveLivraison;
import com.yem.hlm.backend.vente.domain.StatutReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReserveLivraisonRepository extends JpaRepository<ReserveLivraison, UUID> {

    List<ReserveLivraison> findBySocieteIdAndVenteIdOrderByDateConstatAsc(UUID societeId, UUID venteId);

    Optional<ReserveLivraison> findBySocieteIdAndId(UUID societeId, UUID id);

    /** Count reserves for a vente that are not yet lifted (used to decide RESERVES_LEVEES). */
    long countBySocieteIdAndVenteIdAndStatutNot(UUID societeId, UUID venteId, StatutReserve statut);

    /**
     * All reserves for every vente whose property belongs to a given project (A-003).
     * Rows: [rl.id, rl.description, rl.statut, rl.dateConstat, rl.dateLeveePrevue,
     *        rl.dateLeveeReelle, rl.responsableUserId, v.id, v.venteRef, pr.id, pr.referenceCode].
     */
    @Query(value = """
            SELECT rl.id, rl.description, rl.statut, rl.date_constat,
                   rl.date_levee_prevue, rl.date_levee_reelle, rl.responsable_user_id,
                   v.id AS vente_id, v.vente_ref,
                   pr.id AS property_id, pr.reference_code
            FROM reserve_livraison rl
            JOIN vente    v  ON v.id    = rl.vente_id
            JOIN property pr ON pr.id   = v.property_id
            WHERE rl.societe_id = :societeId
              AND pr.project_id = :projectId
            ORDER BY rl.date_constat ASC
            """, nativeQuery = true)
    List<Object[]> findByProjectId(@Param("societeId") UUID societeId,
                                   @Param("projectId") UUID projectId);
}
