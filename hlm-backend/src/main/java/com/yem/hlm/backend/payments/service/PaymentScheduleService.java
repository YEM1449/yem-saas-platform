package com.yem.hlm.backend.payments.service;

import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.payments.api.dto.CreateScheduleItemRequest;
import com.yem.hlm.backend.payments.api.dto.PaymentScheduleItemResponse;
import com.yem.hlm.backend.payments.api.dto.UpdateScheduleItemRequest;
import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD operations for payment schedule items.
 * RBAC: ADMIN/MANAGER may create/update/delete; AGENT read-only.
 */
@Service
@Transactional(readOnly = true)
public class PaymentScheduleService {

    private final PaymentScheduleItemRepository itemRepo;
    private final SchedulePaymentRepository     paymentRepo;
    private final SaleContractRepository        contractRepo;

    public PaymentScheduleService(PaymentScheduleItemRepository itemRepo,
                                  SchedulePaymentRepository paymentRepo,
                                  SaleContractRepository contractRepo) {
        this.itemRepo     = itemRepo;
        this.paymentRepo  = paymentRepo;
        this.contractRepo = contractRepo;
    }

    // ── List ────────────────────────────────────────────────────────────────

    public List<PaymentScheduleItemResponse> listByContract(UUID contractId) {
        UUID societeId = requireTenantId();
        // Verify contract belongs to société (also enforces société isolation)
        requireContract(societeId, contractId);
        return itemRepo.findBySocieteIdAndContractIdOrderBySequenceAsc(societeId, contractId)
                .stream()
                .map(item -> toResponse(item, societeId))
                .toList();
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public PaymentScheduleItemResponse create(UUID contractId, CreateScheduleItemRequest req) {
        UUID societeId = requireTenantId();
        UUID actorId  = requireUserId();
        SaleContract contract = requireContract(societeId, contractId);

        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentInvalidAmountException("Amount must be greater than zero");
        }

        int nextSeq = itemRepo.maxSequence(societeId, contractId) + 1;

        PaymentScheduleItem item = new PaymentScheduleItem(
                societeId,
                contractId,
                contract.getProject().getId(),
                contract.getProperty().getId(),
                actorId,
                nextSeq,
                req.label().trim(),
                req.amount(),
                req.dueDate(),
                req.notes()
        );
        return toResponse(itemRepo.save(item), societeId);
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public PaymentScheduleItemResponse update(UUID itemId, UpdateScheduleItemRequest req) {
        UUID societeId = requireTenantId();
        PaymentScheduleItem item = requireItem(societeId, itemId);

        if (item.getStatus() != PaymentScheduleStatus.DRAFT) {
            throw new InvalidPaymentScheduleStateException(
                    "Only DRAFT items can be edited; current status: " + item.getStatus());
        }

        if (req.label() != null)   item.setLabel(req.label().trim());
        if (req.amount() != null) {
            if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentInvalidAmountException("Amount must be greater than zero");
            }
            item.setAmount(req.amount());
        }
        if (req.dueDate() != null) item.setDueDate(req.dueDate());
        if (req.notes()   != null) item.setNotes(req.notes());

        return toResponse(itemRepo.save(item), societeId);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID itemId) {
        UUID societeId = requireTenantId();
        PaymentScheduleItem item = requireItem(societeId, itemId);

        if (item.getStatus() == PaymentScheduleStatus.PAID) {
            throw new InvalidPaymentScheduleStateException(
                    "Cannot delete a fully PAID schedule item");
        }
        itemRepo.delete(item);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    PaymentScheduleItem requireItem(UUID societeId, UUID itemId) {
        return itemRepo.findBySocieteIdAndId(societeId, itemId)
                .orElseThrow(() -> new PaymentScheduleItemNotFoundException(itemId));
    }

    SaleContract requireContract(UUID societeId, UUID contractId) {
        return contractRepo.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
    }

    PaymentScheduleItemResponse toResponse(PaymentScheduleItem item, UUID societeId) {
        BigDecimal paid = paymentRepo.sumPaidForItem(societeId, item.getId());
        BigDecimal remaining = item.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
        return PaymentScheduleItemResponse.from(item, paid, remaining);
    }

    UUID requireTenantId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new CrossTenantAccessException("Missing société context");
        return id;
    }

    UUID requireUserId() {
        UUID id = SocieteContext.getUserId();
        if (id == null) throw new CrossTenantAccessException("Missing user context");
        return id;
    }

    boolean isAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }
}
