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
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
    private final TenantRepository              tenantRepo;

    public PaymentScheduleService(PaymentScheduleItemRepository itemRepo,
                                  SchedulePaymentRepository paymentRepo,
                                  SaleContractRepository contractRepo,
                                  TenantRepository tenantRepo) {
        this.itemRepo     = itemRepo;
        this.paymentRepo  = paymentRepo;
        this.contractRepo = contractRepo;
        this.tenantRepo   = tenantRepo;
    }

    // ── List ────────────────────────────────────────────────────────────────

    public List<PaymentScheduleItemResponse> listByContract(UUID contractId) {
        UUID tenantId = requireTenantId();
        // Verify contract belongs to tenant (also enforces tenant isolation)
        requireContract(tenantId, contractId);
        return itemRepo.findByTenant_IdAndContractIdOrderBySequenceAsc(tenantId, contractId)
                .stream()
                .map(item -> toResponse(item, tenantId))
                .toList();
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public PaymentScheduleItemResponse create(UUID contractId, CreateScheduleItemRequest req) {
        UUID tenantId = requireTenantId();
        UUID actorId  = requireUserId();
        SaleContract contract = requireContract(tenantId, contractId);

        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentInvalidAmountException("Amount must be greater than zero");
        }

        int nextSeq = itemRepo.maxSequence(tenantId, contractId) + 1;

        PaymentScheduleItem item = new PaymentScheduleItem(
                tenantRepo.getReferenceById(tenantId),
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
        return toResponse(itemRepo.save(item), tenantId);
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public PaymentScheduleItemResponse update(UUID itemId, UpdateScheduleItemRequest req) {
        UUID tenantId = requireTenantId();
        PaymentScheduleItem item = requireItem(tenantId, itemId);

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

        return toResponse(itemRepo.save(item), tenantId);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID itemId) {
        UUID tenantId = requireTenantId();
        PaymentScheduleItem item = requireItem(tenantId, itemId);

        if (item.getStatus() == PaymentScheduleStatus.PAID) {
            throw new InvalidPaymentScheduleStateException(
                    "Cannot delete a fully PAID schedule item");
        }
        itemRepo.delete(item);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    PaymentScheduleItem requireItem(UUID tenantId, UUID itemId) {
        return itemRepo.findByTenant_IdAndId(tenantId, itemId)
                .orElseThrow(() -> new PaymentScheduleItemNotFoundException(itemId));
    }

    SaleContract requireContract(UUID tenantId, UUID contractId) {
        return contractRepo.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
    }

    PaymentScheduleItemResponse toResponse(PaymentScheduleItem item, UUID tenantId) {
        BigDecimal paid = paymentRepo.sumPaidForItem(tenantId, item.getId());
        BigDecimal remaining = item.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
        return PaymentScheduleItemResponse.from(item, paid, remaining);
    }

    UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new CrossTenantAccessException("Missing tenant context");
        return id;
    }

    UUID requireUserId() {
        UUID id = TenantContext.getUserId();
        if (id == null) throw new CrossTenantAccessException("Missing user context");
        return id;
    }

    boolean isAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }
}
