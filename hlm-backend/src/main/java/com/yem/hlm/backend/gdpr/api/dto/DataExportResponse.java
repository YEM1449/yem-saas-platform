package com.yem.hlm.backend.gdpr.api.dto;

import com.yem.hlm.backend.contact.domain.ConsentMethod;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full personal-data export for one contact.
 * Produced by {@link com.yem.hlm.backend.gdpr.service.DataExportBuilder} in response to
 * a GDPR Art. 15 / Art. 20 portability request.
 */
public record DataExportResponse(
        UUID contactId,
        String fullName,
        String email,
        String phone,
        ContactStatus status,
        ContactType type,
        boolean consentGiven,
        Instant consentDate,
        ConsentMethod consentMethod,
        ProcessingBasis processingBasis,
        List<InterestExport> interests,
        List<DepositExport> deposits,
        List<ContractExport> contracts,
        List<AuditEventExport> auditEvents,
        List<MessageExport> outboundMessages,
        Instant exportedAt,
        UUID exportedByUserId
) {

    public record InterestExport(UUID propertyId, String propertyReferenceCode, LocalDateTime createdAt) {}

    public record DepositExport(UUID depositId, BigDecimal amount, String currency, String status, LocalDateTime createdAt) {}

    public record ContractExport(UUID contractId, BigDecimal agreedPrice, String status, LocalDateTime signedAt) {}

    public record AuditEventExport(String eventType, LocalDateTime occurredAt) {}

    public record MessageExport(String channel, LocalDateTime sentAt, String status) {}
}
