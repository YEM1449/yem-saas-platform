package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public class ContactSpecification {

    public static Specification<Contact> bySociete(UUID societeId) {
        return (root, query, cb) -> cb.equal(root.get("societeId"), societeId);
    }

    public static Specification<Contact> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Contact> byStatuses(List<ContactStatus> statuses) {
        return (root, query, cb) -> statuses.isEmpty()
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    public static Specification<Contact> bySearchTerm(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String like = "%" + term.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like)
            );
        };
    }
}
