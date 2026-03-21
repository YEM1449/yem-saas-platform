package com.yem.hlm.backend.property.repo;

import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class PropertySpecification {

    public static Specification<Property> bySociete(UUID societeId) {
        return (root, query, cb) -> cb.equal(root.get("societeId"), societeId);
    }

    public static Specification<Property> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Property> byStatus(PropertyStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Property> bySearchTerm(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String like = "%" + term.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("referenceCode")), like),
                    cb.like(cb.lower(root.get("city")), like)
            );
        };
    }
}
