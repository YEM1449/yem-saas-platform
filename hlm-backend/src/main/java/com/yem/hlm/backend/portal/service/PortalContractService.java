package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.contract.service.pdf.ContractDocumentService;
import com.yem.hlm.backend.payment.api.dto.PaymentScheduleResponse;
import com.yem.hlm.backend.payment.repo.PaymentScheduleRepository;
import com.yem.hlm.backend.portal.api.dto.PortalContractResponse;
import com.yem.hlm.backend.portal.api.dto.PortalPropertyResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTenantInfoResponse;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
    private final PaymentScheduleRepository scheduleRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final ContractDocumentService contractDocumentService;

    public PortalContractService(SaleContractRepository contractRepository,
                                 PaymentScheduleRepository scheduleRepository,
                                 PropertyRepository propertyRepository,
                                 TenantRepository tenantRepository,
                                 ContractDocumentService contractDocumentService) {
        this.contractRepository  = contractRepository;
        this.scheduleRepository  = scheduleRepository;
        this.propertyRepository  = propertyRepository;
        this.tenantRepository    = tenantRepository;
        this.contractDocumentService = contractDocumentService;
    }

    // =========================================================================
    // Contracts
    // =========================================================================

    /** Returns all contracts where the authenticated contact is the buyer. */
    public List<PortalContractResponse> listContracts() {
        UUID tenantId  = requireTenantId();
        UUID contactId = getContactId();
        return contractRepository.findPortalContracts(tenantId, contactId)
                .stream()
                .map(PortalContractResponse::from)
                .toList();
    }

    /**
     * Generates the contract PDF for the authenticated buyer.
     * Enforces: contract must belong to this contact (→ 404 otherwise).
     */
    public byte[] getContractPdf(UUID contractId) {
        UUID tenantId  = requireTenantId();
        UUID contactId = getContactId();

        var contract = contractRepository.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!contract.getBuyerContact().getId().equals(contactId)) {
            throw new ContractNotFoundException(contractId); // 404, no info leak
        }

        // ContractDocumentService.enforceAgentOwnership() only restricts ROLE_AGENT,
        // not ROLE_PORTAL, so it passes cleanly.
        return contractDocumentService.generate(contractId);
    }

    // =========================================================================
    // Payment schedule
    // =========================================================================

    /**
     * Returns the payment schedule for a contract owned by the authenticated buyer.
     * Throws 404 if the contract does not belong to this contact.
     */
    public PaymentScheduleResponse getPaymentSchedule(UUID contractId) {
        UUID tenantId  = requireTenantId();
        UUID contactId = getContactId();

        var contract = contractRepository.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!contract.getBuyerContact().getId().equals(contactId)) {
            throw new ContractNotFoundException(contractId);
        }

        return scheduleRepository.findWithTranches(tenantId, contractId)
                .map(PaymentScheduleResponse::from)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
    }

    // =========================================================================
    // Property
    // =========================================================================

    /**
     * Returns property details if the authenticated contact has a contract for it.
     * Throws 404 if no such contract exists.
     */
    public PortalPropertyResponse getProperty(UUID propertyId) {
        UUID tenantId  = requireTenantId();
        UUID contactId = getContactId();

        boolean hasContract = contractRepository.existsByTenant_IdAndProperty_IdAndBuyerContact_Id(
                tenantId, propertyId, contactId);
        if (!hasContract) {
            throw new PropertyNotFoundException(propertyId);
        }

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        return PortalPropertyResponse.from(property, property.getProject().getName());
    }

    // =========================================================================
    // Tenant info
    // =========================================================================

    /** Returns the tenant's display name for the portal shell header. */
    public PortalTenantInfoResponse getTenantInfo() {
        UUID tenantId = requireTenantId();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found in context"));
        return new PortalTenantInfoResponse(tenant.getName(), null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new IllegalStateException("Missing tenant context");
        return id;
    }

    /** The portal JWT stores contactId as the JWT subject → authentication principal. */
    private UUID getContactId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
