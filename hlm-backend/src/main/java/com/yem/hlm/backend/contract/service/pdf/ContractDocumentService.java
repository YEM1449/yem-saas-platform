package com.yem.hlm.backend.contract.service.pdf;

import com.yem.hlm.backend.contract.domain.BuyerType;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates PDF generation for a sales contract.
 *
 * <h3>RBAC</h3>
 * <ul>
 *   <li>ADMIN / MANAGER — any contract in the tenant.</li>
 *   <li>AGENT — own contracts only (contract.agent.id == caller's userId);
 *       cross-ownership → 404 (avoids information leak).</li>
 * </ul>
 *
 * <h3>Buyer data</h3>
 * Prefers the immutable snapshot fields (captured at SIGNED time via
 * {@code SaleContractService.captureBuyerSnapshot()}) and falls back to the
 * live Contact fields for DRAFT contracts where the snapshot is not yet set.
 */
@Service
@Transactional(readOnly = true)
public class ContractDocumentService {

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaleContractRepository    contractRepository;
    private final DocumentGenerationService documentGenerationService;

    public ContractDocumentService(SaleContractRepository contractRepository,
                                   DocumentGenerationService documentGenerationService) {
        this.contractRepository        = contractRepository;
        this.documentGenerationService = documentGenerationService;
    }

    /**
     * Generates a PDF contract for the given contract ID.
     *
     * @param contractId the contract UUID
     * @return PDF bytes (starts with {@code %PDF})
     * @throws ContractNotFoundException if contract not found for this tenant (or AGENT
     *                                   tries to access another agent's contract — 404)
     */
    public byte[] generate(UUID contractId) {
        UUID tenantId = TenantContext.getTenantId();

        SaleContract contract = contractRepository.findForPdf(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        enforceAgentOwnership(contractId, contract);

        ContractDocumentModel model = buildModel(contract);
        return documentGenerationService.renderToPdf("documents/contract", Map.of("model", model));
    }

    // =========================================================================
    // Model builder
    // =========================================================================

    private ContractDocumentModel buildModel(SaleContract contract) {
        var property = contract.getProperty();
        var buyer    = contract.getBuyerContact();

        // Buyer info: prefer immutable snapshot (set at SIGNED time), fall back to live Contact
        String buyerDisplayName = firstNonBlank(contract.getBuyerDisplayName(),
                buyer.getFullName(), "—");
        String buyerPhone    = blankToNull(firstNonBlank(contract.getBuyerPhone(),   buyer.getPhone()));
        String buyerEmail    = blankToNull(firstNonBlank(contract.getBuyerEmail(),   buyer.getEmail()));
        String buyerAddress  = blankToNull(firstNonBlank(contract.getBuyerAddress(), buyer.getAddress()));
        String buyerIce      = blankToNull(firstNonBlank(contract.getBuyerIce(),     buyer.getNationalId()));
        String buyerTypeLabel = contract.getBuyerType() == BuyerType.COMPANY
                ? "Personne morale" : "Personne physique";

        // Prices
        String agreedPriceStr = contract.getAgreedPrice().toPlainString();
        String listPriceStr   = contract.getListPrice() != null
                ? contract.getListPrice().toPlainString() : null;

        return new ContractDocumentModel(
                nvl(contract.getTenant().getName(), "—"),
                nvl(contract.getProject().getName(), "—"),
                nvl(property.getReferenceCode(), "—"),
                nvl(property.getTitle(), "—"),
                property.getType().name(),
                agreedPriceStr,
                listPriceStr,
                buyerDisplayName,
                buyerPhone,
                buyerEmail,
                buyerAddress,
                buyerIce,
                buyerTypeLabel,
                contract.getAgent().getEmail(),
                contract.getId().toString().substring(0, 8).toUpperCase(),
                contract.getStatus().name(),
                formatDateTime(contract.getSignedAt()),
                formatDateTime(contract.getCanceledAt()),
                contract.getCreatedAt().format(DATE_FMT),
                LocalDateTime.now().format(DATETIME_FMT));
    }

    // =========================================================================
    // RBAC helper
    // =========================================================================

    private void enforceAgentOwnership(UUID contractId, SaleContract contract) {
        boolean isAgent = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
        if (isAgent) {
            UUID callerId = (UUID) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            if (!callerId.equals(contract.getAgent().getId())) {
                // Return 404 instead of 403 to avoid leaking contract existence to other agents.
                throw new ContractNotFoundException(contractId);
            }
        }
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    private static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME_FMT) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
