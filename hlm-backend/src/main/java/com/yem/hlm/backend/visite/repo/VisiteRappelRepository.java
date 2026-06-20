package com.yem.hlm.backend.visite.repo;

import com.yem.hlm.backend.visite.domain.StatutRappel;
import com.yem.hlm.backend.visite.domain.VisiteRappel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link VisiteRappel}.
 *
 * <p>The due-reminder scan ({@link #findByStatutAndDuABeforeOrderByDuAAsc}) runs in
 * system mode (cross-société) inside the {@code @Scheduled} reminder job — the nil-UUID
 * RLS bypass lets it read every société's pending reminders. The per-visite lookups are
 * société-scoped and used during create/cancel.
 */
public interface VisiteRappelRepository extends JpaRepository<VisiteRappel, UUID> {

    /** Reminders due to be sent — scanned every 5 min by the job (RG-V07). */
    List<VisiteRappel> findByStatutAndDuABeforeOrderByDuAAsc(StatutRappel statut, Instant cutoff);

    /** Pending reminders of a visite — used to cancel them on annulation (RG-V08). */
    List<VisiteRappel> findByVisiteIdAndStatut(UUID visiteId, StatutRappel statut);

    List<VisiteRappel> findByVisiteId(UUID visiteId);
}
