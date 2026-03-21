package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ClientDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClientDetailRepository extends JpaRepository<ClientDetail, UUID> {

    @Query("SELECT cd FROM ClientDetail cd JOIN cd.contact c WHERE c.societeId = :societeId AND cd.contactId = :contactId")
    Optional<ClientDetail> findBySocieteIdAndContactId(@Param("societeId") UUID societeId,
                                                        @Param("contactId") UUID contactId);
}
