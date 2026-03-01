package com.yem.hlm.backend.payment.service;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.payment.domain.PaymentCall;
import com.yem.hlm.backend.payment.domain.PaymentTranche;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Generates an "Appel de Fonds" PDF for a payment call.
 *
 * <h3>RBAC</h3>
 * <ul>
 *   <li>ADMIN / MANAGER — any call in the tenant.</li>
 *   <li>AGENT — own calls only (contract.agent == caller); cross-ownership → 404.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PaymentCallDocumentService {

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PaymentCallRepository    callRepository;
    private final DocumentGenerationService documentGenerationService;

    public PaymentCallDocumentService(PaymentCallRepository callRepository,
                                      DocumentGenerationService documentGenerationService) {
        this.callRepository            = callRepository;
        this.documentGenerationService = documentGenerationService;
    }

    /**
     * Generates a PDF for the given payment call.
     *
     * @param callId the payment call UUID
     * @return PDF bytes (starts with {@code %PDF})
     * @throws PaymentCallNotFoundException if call not found in tenant (or AGENT cross-ownership)
     */
    public byte[] generate(UUID callId) {
        UUID tenantId = TenantContext.getTenantId();

        PaymentCall call = callRepository.findForPdf(tenantId, callId)
                .orElseThrow(() -> new PaymentCallNotFoundException(callId));

        enforceAgentOwnership(callId, call);

        PaymentCallDocumentModel model = buildModel(call);
        return documentGenerationService.renderToPdf(
                "documents/appel-de-fonds", Map.of("model", model));
    }

    // =========================================================================
    // Model builder
    // =========================================================================

    private PaymentCallDocumentModel buildModel(PaymentCall call) {
        PaymentTranche tranche  = call.getTranche();
        SaleContract   contract = tranche.getSchedule().getSaleContract();
        var            property = contract.getProperty();
        var            buyer    = contract.getBuyerContact();

        // Buyer info: prefer snapshot, fall back to live contact
        String buyerName    = firstNonBlank(contract.getBuyerDisplayName(), buyer.getFullName(), "—");
        String buyerPhone   = blankToNull(firstNonBlank(contract.getBuyerPhone(),   buyer.getPhone()));
        String buyerEmail   = blankToNull(firstNonBlank(contract.getBuyerEmail(),   buyer.getEmail()));
        String buyerAddress = blankToNull(firstNonBlank(contract.getBuyerAddress(), buyer.getAddress()));

        return new PaymentCallDocumentModel(
                nvl(contract.getTenant().getName(), "—"),
                nvl(contract.getProject().getName(), "—"),
                nvl(property.getReferenceCode(), "—"),
                nvl(property.getTitle(), "—"),
                buyerName,
                buyerPhone,
                buyerEmail,
                buyerAddress,
                contract.getId().toString().substring(0, 8).toUpperCase(),
                contract.getAgreedPrice().toPlainString(),
                tranche.getLabel(),
                tranche.getPercentage().toPlainString(),
                tranche.getAmount().toPlainString(),
                formatDate(tranche.getDueDate()),
                tranche.getTriggerCondition(),
                call.getCallNumber(),
                call.getAmountDue().toPlainString(),
                formatDateTime(call.getIssuedAt()),
                call.getStatus().name(),
                contract.getAgent().getEmail(),
                LocalDateTime.now().format(DATETIME_FMT)
        );
    }

    // =========================================================================
    // RBAC helper
    // =========================================================================

    private void enforceAgentOwnership(UUID callId, PaymentCall call) {
        boolean isAgent = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
        if (!isAgent) return;

        UUID callerId    = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID contractAgent = call.getTranche().getSchedule().getSaleContract().getAgent().getId();
        if (!callerId.equals(contractAgent)) {
            throw new PaymentCallNotFoundException(callId);
        }
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    private static String formatDate(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : null;
    }

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
