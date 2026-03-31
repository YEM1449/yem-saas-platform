package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.common.event.ContactStatusChangedEvent;
import com.yem.hlm.backend.common.event.PropertyInterestEvent;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for property interest events and auto-promotes contacts
 * from PROSPECT to QUALIFIED_PROSPECT when they express interest in a property.
 */
@Component
public class ProspectStatusListener {

    private static final Logger log = LoggerFactory.getLogger(ProspectStatusListener.class);

    private final ContactRepository contactRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProspectStatusListener(ContactRepository contactRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.contactRepository = contactRepository;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    @Transactional
    public void onPropertyInterest(PropertyInterestEvent event) {
        contactRepository.findBySocieteIdAndId(event.getSocieteId(), event.getContactId())
                .ifPresent(contact -> {
                    if (contact.getStatus() == ContactStatus.PROSPECT) {
                        ContactStatus oldStatus = contact.getStatus();
                        contact.setStatus(ContactStatus.QUALIFIED_PROSPECT);
                        contact.setQualified(true);
                        contactRepository.save(contact);

                        eventPublisher.publishEvent(new ContactStatusChangedEvent(
                                event.getSocieteId(), event.getActorUserId(),
                                contact.getId(), oldStatus, ContactStatus.QUALIFIED_PROSPECT));

                        log.info("[PROSPECT] Contact {} promoted to QUALIFIED_PROSPECT via {}",
                                event.getContactId(), event.getInterestType());
                    }
                });
    }
}
