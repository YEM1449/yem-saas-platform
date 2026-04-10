package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates that a contact has all the fields required for a given pipeline stage.
 *
 * <ul>
 *   <li>RESERVATION stage → requires {@code phone}</li>
 *   <li>VENTE stage → requires {@code phone} + {@code nationalId} + {@code address}</li>
 * </ul>
 */
@Service
public class ContactCompletenessService {

    private final ContactRepository contactRepository;

    public ContactCompletenessService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    /**
     * Validates that the contact identified by {@code contactId} has all fields
     * required for {@code stage}. Throws {@link ClientIncompleteException} listing
     * every missing field if validation fails.
     *
     * @param societeId the current société (for tenant isolation)
     * @param contactId the contact to validate
     * @param stage     the pipeline stage requiring certain fields
     */
    public void validateForStage(UUID societeId, UUID contactId, ContactValidationStage stage) {
        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        List<String> missing = new ArrayList<>();

        // RESERVATION requires phone
        if (isBlank(contact.getPhone())) {
            missing.add("phone");
        }

        // VENTE additionally requires nationalId and address
        if (stage == ContactValidationStage.VENTE) {
            if (isBlank(contact.getNationalId())) missing.add("nationalId");
            if (isBlank(contact.getAddress()))    missing.add("address");
        }

        if (!missing.isEmpty()) {
            throw new ClientIncompleteException(missing);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
