package com.yem.hlm.backend.payments.service;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.deposit.service.ReservationPdfGenerationException;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a call-for-funds PDF for a payment schedule item.
 * The PDF is rendered on demand; it is NOT stored in the database.
 */
@Service
@Transactional(readOnly = true)
public class CallForFundsPdfService {

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PaymentScheduleItemRepository itemRepo;
    private final SchedulePaymentRepository     paymentRepo;
    private final SaleContractRepository        contractRepo;
    private final DocumentGenerationService     docService;

    public CallForFundsPdfService(PaymentScheduleItemRepository itemRepo,
                                  SchedulePaymentRepository paymentRepo,
                                  SaleContractRepository contractRepo,
                                  DocumentGenerationService docService) {
        this.itemRepo     = itemRepo;
        this.paymentRepo  = paymentRepo;
        this.contractRepo = contractRepo;
        this.docService   = docService;
    }

    /**
     * Generates and returns the PDF bytes for the given schedule item.
     *
     * @param itemId schedule item UUID
     * @return PDF bytes (starts with {@code %PDF})
     * @throws PaymentScheduleItemNotFoundException if item is not found for this tenant
     * @throws ContractNotFoundException            if the linked contract is not found
     * @throws ReservationPdfGenerationException    on rendering failure
     */
    public byte[] generate(UUID itemId) {
        UUID tenantId = TenantContext.getTenantId();

        PaymentScheduleItem item = itemRepo.findByTenant_IdAndId(tenantId, itemId)
                .orElseThrow(() -> new PaymentScheduleItemNotFoundException(itemId));

        SaleContract contract = contractRepo.findByTenant_IdAndId(tenantId, item.getContractId())
                .orElseThrow(() -> new ContractNotFoundException(item.getContractId()));

        BigDecimal paid      = paymentRepo.sumPaidForItem(tenantId, itemId);
        BigDecimal remaining = item.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        CallForFundsDocumentModel model = buildModel(item, contract, paid, remaining);
        return docService.renderToPdf("documents/call_for_funds", Map.of("model", model));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private CallForFundsDocumentModel buildModel(PaymentScheduleItem item,
                                                  SaleContract contract,
                                                  BigDecimal paid,
                                                  BigDecimal remaining) {
        String projectName   = contract.getProject() != null
                ? nvl(contract.getProject().getName(), "—") : "—";
        String propertyRef   = contract.getProperty() != null
                ? nvl(contract.getProperty().getReferenceCode(), "—") : "—";
        String propertyTitle = contract.getProperty() != null
                ? nvl(contract.getProperty().getTitle(), "—") : "—";

        String agreedPrice = contract.getAgreedPrice() != null
                ? contract.getAgreedPrice().toPlainString() : "—";

        return new CallForFundsDocumentModel(
                nvl(contract.getTenant().getName(), "—"),
                projectName, propertyRef, propertyTitle,
                nvl(contract.getBuyerDisplayName(), "—"),
                blankToNull(contract.getBuyerPhone()),
                blankToNull(contract.getBuyerEmail()),
                blankToNull(contract.getBuyerAddress()),
                agreedPrice,
                item.getSequence(),
                item.getLabel(),
                item.getAmount(),
                item.getDueDate().format(DATE_FMT),
                blankToNull(item.getNotes()),
                item.getStatus().name(),
                paid,
                remaining,
                contract.getAgent() != null ? contract.getAgent().getEmail() : null,
                LocalDateTime.now().format(DATETIME_FMT)
        );
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
