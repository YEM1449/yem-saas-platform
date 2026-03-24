package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.api.dto.SocieteFilter;
import com.yem.hlm.backend.societe.domain.Societe;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

public final class SocieteSpecification {

    private SocieteSpecification() {}

    public static Specification<Societe> from(SocieteFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nom")), like),
                        cb.like(cb.lower(root.get("nomCommercial")), like),
                        cb.like(cb.lower(root.get("siretIce")), like)
                ));
            }
            if (f.pays() != null && !f.pays().isBlank()) {
                predicates.add(cb.equal(root.get("pays"), f.pays()));
            }
            if (f.planAbonnement() != null && !f.planAbonnement().isBlank()) {
                predicates.add(cb.equal(root.get("planAbonnement"), f.planAbonnement()));
            }
            if (f.actif() != null) {
                predicates.add(cb.equal(root.get("actif"), f.actif()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
