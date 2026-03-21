package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.contract.service.pdf.ContractDocumentService;
import com.yem.hlm.backend.payments.api.dto.PaymentScheduleItemResponse;
import com.yem.hlm.backend.payments.service.PaymentScheduleService;
import com.yem.hlm.backend.portal.api.dto.PortalContractResponse;
import com.yem.hlm.backend.portal.api.dto.PortalPropertyResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTenantInfoResponse;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * All portal data-access operations scoped to the authenticated buyer (contactId from JWT).
 *
 * <p>The contactId is extracted from {@code SecurityContextHolder} principal — the portal JWT
 * stores contactId as the JWT subject, so the filter sets it as the authentication principal.
 */
@Service
@Transactional(readOnly = true)
public class PortalContractService {

    private final SaleContractRepository contractRepository;
    private final PaymentScheduleService paymentScheduleService;
    private final PropertyRepository propertyRepository;
    private final SocieteRepository societeRepository;
    private final ContractDocumentService contractDocumentService;

    public PortalContractService(SaleContractRepository contractRepository,
                                 PaymentScheduleService paymentScheduleService,
                                 PropertyRepository propertyRepository,
                                 SocieteRepository societeRepository,
                                 ContractDocumentService contractDocumentService) {
        this.contractRepository      = contractRepository;
        this.paymentScheduleService  = paymentScheduleService;
        this.propertyRepository      = propertyRepository;
        this.societeRepository       = societeRepository;
        this.contractDocumentService = contractDocumentService;
    }

    // =========================================================================
    // Contracts
    // =========================================================================

    /** Returns all contracts where the authenticated contact is the buyer. */
    public List<PortalContractResponse> listContracts() {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();
        return contractRepository.findPortalContracts(societeId, contactId)
                .stream()
                .map(PortalContractResponse::from)
                .toList();
    }

    /**
     * Generates the contract PDF for the authenticated buyer.
     * Enforces: contract must belong to this contact (→ 404 otherwise).
     */
    public byte[] getContractPdf(UUID contractId) {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();

        var contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!contract.getBuyerContact().getId().equals(contactId)) {
            throw new ContractNotFoundException(contractId); // 404, no info leak
        }

        // ContractDocumentService.enforceAgentOwnership() only restricts ROLE_AGENT,
        // not ROLE_PORTAL, so it passes cleanly.
        return contractDocumentService.generate(contractId);
    }

    // =========================================================================
    // Payment schedule (v2 — PaymentScheduleItem)
    // =========================================================================

    /**
     * Returns the v2 payment schedule items for a contract owned by the authenticated buyer.
     * Enforces buyer ownership (→ 404 if this contact is not the buyer).
     * Delegates to {@link PaymentScheduleService#listByContract(UUID)} which uses SocieteContext
     * (already set by the portal JWT filter) for société isolation.
     */
    public List<PaymentScheduleItemResponse> getPaymentSchedule(UUID contractId) {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();

        var contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!contract.getBuyerContact().getId().equals(contactId)) {
            throw new ContractNotFoundException(contractId);
        }

        return paymentScheduleService.listByContract(contractId);
    }

    // =========================================================================
    // Property
    // =========================================================================

    /**
     * Returns property details if the authenticated contact has a contract for it.
     * Throws 404 if no such contract exists.
     */
    public PortalPropertyResponse getProperty(UUID propertyId) {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();

        boolean hasContract = contractRepository.existsBySocieteIdAndProperty_IdAndBuyerContact_Id(
                societeId, propertyId, contactId);
        if (!hasContract) {
            throw new PropertyNotFoundException(propertyId);
        }

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        return PortalPropertyResponse.from(property, property.getProject().getName());
    }

    // =========================================================================
    // Société info
    // =========================================================================

    /** Returns the société's display name for the portal shell header. */
    public PortalTenantInfoResponse getTenantInfo() {
        UUID societeId = requireSocieteId();
        Societe societe = societeRepository.findById(societeId)
                .orElseThrow(() -> new IllegalStateException("Société not found in context"));
        return new PortalTenantInfoResponse(societe.getNom(), null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new IllegalStateException("Missing société context");
        return id;
    }

    /** The portal JWT stores contactId as the JWT subject → authentication principal. */
    private UUID getContactId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
