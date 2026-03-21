package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ProspectDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProspectDetailRepository extends JpaRepository<ProspectDetail, UUID> {

    @Query("SELECT pd FROM ProspectDetail pd JOIN pd.contact c WHERE c.societeId = :societeId AND pd.contactId = :contactId")
    Optional<ProspectDetail> findBySocieteIdAndContactId(@Param("societeId") UUID societeId,
                                                          @Param("contactId") UUID contactId);
}
