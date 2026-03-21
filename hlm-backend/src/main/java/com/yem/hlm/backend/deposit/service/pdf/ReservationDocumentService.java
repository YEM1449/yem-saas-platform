package com.yem.hlm.backend.deposit.service.pdf;

import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.deposit.service.DepositNotFoundException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates PDF generation for a reservation/deposit.
 *
 * <h3>RBAC</h3>
 * <ul>
 *   <li>ADMIN / MANAGER — any deposit in the tenant.</li>
 *   <li>AGENT — own deposits only (deposit.agent.id == caller's userId);
 *       cross-ownership → 404 (avoids information leak).</li>
 * </ul>
 *
 * <h3>Query optimisation</h3>
 * Deposit is loaded with a JOIN FETCH covering tenant, contact, and agent
 * to avoid N+1. Property is loaded in a second query (tenant-scoped).
 *
 * <h3>Where to add template fields</h3>
 * <ol>
 *   <li>Add the new field to {@link ReservationDocumentModel}.</li>
 *   <li>Populate it in {@link #buildModel(Deposit, Property)}.</li>
 *   <li>Render it in {@code templates/documents/reservation.html}.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class ReservationDocumentService {

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DepositRepository        depositRepository;
    private final PropertyRepository       propertyRepository;
    private final DocumentGenerationService documentGenerationService;
    private final SocieteRepository        societeRepository;

    public ReservationDocumentService(DepositRepository depositRepository,
                                      PropertyRepository propertyRepository,
                                      DocumentGenerationService documentGenerationService,
                                      SocieteRepository societeRepository) {
        this.depositRepository        = depositRepository;
        this.propertyRepository       = propertyRepository;
        this.documentGenerationService = documentGenerationService;
        this.societeRepository         = societeRepository;
    }

    /**
     * Generates a PDF reservation certificate for the given deposit.
     *
     * @param depositId the deposit UUID
     * @return PDF bytes (starts with {@code %PDF})
     * @throws DepositNotFoundException     if deposit not found for this tenant (or AGENT
     *                                      tries to access another agent's deposit — 404)
     * @throws PropertyNotFoundException    if the linked property was hard-deleted
     */
    public byte[] generate(UUID depositId) {
        UUID societeId = SocieteContext.getSocieteId();

        Deposit deposit = depositRepository.findForPdf(societeId, depositId)
                .orElseThrow(() -> new DepositNotFoundException(depositId));

        // RBAC: AGENT callers may only access their own deposits.
        enforceAgentOwnership(depositId, deposit);

        // Load property (may be null for legacy deposits without a linked property).
        Property property = null;
        if (deposit.getPropertyId() != null) {
            property = propertyRepository
                    .findBySocieteIdAndIdAndDeletedAtIsNull(societeId, deposit.getPropertyId())
                    .orElse(null); // withdrawn/archived property shouldn't block PDF download
        }

        String societeName = societeRepository.findById(societeId)
                .map(Societe::getNom).orElse("—");
        ReservationDocumentModel model = buildModel(deposit, property, societeName);
        return documentGenerationService.renderToPdf(
                "documents/reservation",
                Map.of("model", model));
    }

    // =========================================================================
    // Model builder
    // =========================================================================

    private ReservationDocumentModel buildModel(Deposit deposit, Property property, String societeName) {
        // Property fields
        String projectName   = "—";
        String propertyRef   = "—";
        String propertyTitle = "—";
        String propertyType  = "—";
        String propertyPrice = null;

        if (property != null) {
            projectName   = property.getProject() != null ? property.getProject().getName() : "—";
            propertyRef   = nvl(property.getReferenceCode(), "—");
            propertyTitle = nvl(property.getTitle(), "—");
            propertyType  = property.getType().name();
            if (property.getPrice() != null) {
                propertyPrice = property.getPrice().toPlainString()
                        + " " + deposit.getCurrency();
            }
        }

        // Buyer (contact)
        String buyerFullName = nvl(deposit.getContact().getFullName(), "—");
        String buyerPhone    = blankToNull(deposit.getContact().getPhone());
        String buyerEmail    = blankToNull(deposit.getContact().getEmail());

        // Deposit fields
        String depositReference = nvl(deposit.getReference(), "—");
        String depositStatus    = deposit.getStatus().name();
        String depositAmount    = deposit.getAmount().toPlainString()
                + " " + deposit.getCurrency();
        String depositDate      = deposit.getDepositDate().format(DATE_FMT);
        String dueDate          = formatDateTime(deposit.getDueDate());
        String confirmedAt      = formatDateTime(deposit.getConfirmedAt());
        String cancelledAt      = formatDateTime(deposit.getCancelledAt());
        String notes            = blankToNull(deposit.getNotes());

        return new ReservationDocumentModel(
                nvl(societeName, "—"),
                projectName, propertyRef, propertyTitle, propertyType, propertyPrice,
                buyerFullName, buyerPhone, buyerEmail,
                depositReference, depositStatus, depositAmount,
                depositDate, dueDate, confirmedAt, cancelledAt, notes,
                deposit.getAgent().getEmail(),
                LocalDateTime.now().format(DATETIME_FMT));
    }

    // =========================================================================
    // RBAC helper
    // =========================================================================

    private void enforceAgentOwnership(UUID depositId, Deposit deposit) {
        boolean isAgent = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
        if (isAgent) {
            UUID callerId = (UUID) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            if (!callerId.equals(deposit.getAgent().getId())) {
                // Return 404 instead of 403 to avoid leaking deposit existence to other agents.
                throw new DepositNotFoundException(depositId);
            }
        }
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    private static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME_FMT) : null;
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
