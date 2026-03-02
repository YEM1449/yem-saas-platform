package com.yem.hlm.backend.contract.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.domain.BuyerType;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.service.ProjectActiveGuard;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for the Sales Contract lifecycle.
 * <p>
 * Lifecycle: DRAFT → SIGNED → CANCELED (or DRAFT → CANCELED).
 * <p>
 * Integrity rules enforced here:
 * <ul>
 *   <li>Project must be ACTIVE (delegates to {@link ProjectActiveGuard}).</li>
 *   <li>Property must belong to the given project (tenant-scoped).</li>
 *   <li>Only one active SIGNED contract per property; service-layer check + DB partial unique index.</li>
 *   <li>If {@code sourceDepositId} provided: deposit must be CONFIRMED and match all IDs.</li>
 *   <li>AGENT callers may only create contracts where agentId = their own userId.</li>
 *   <li>AGENT callers only see their own contracts on list queries.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SaleContractService {

    private final SaleContractRepository contractRepository;
    private final ProjectActiveGuard projectActiveGuard;
    private final PropertyRepository propertyRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final DepositRepository depositRepository;
    private final PropertyCommercialWorkflowService propertyWorkflow;
    private final CommercialAuditService auditService;

    public SaleContractService(
            SaleContractRepository contractRepository,
            ProjectActiveGuard projectActiveGuard,
            PropertyRepository propertyRepository,
            ContactRepository contactRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            DepositRepository depositRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            CommercialAuditService auditService) {
        this.contractRepository = contractRepository;
        this.projectActiveGuard = projectActiveGuard;
        this.propertyRepository = propertyRepository;
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.depositRepository = depositRepository;
        this.propertyWorkflow = propertyWorkflow;
        this.auditService = auditService;
    }

    // ===== Create =====

    /**
     * Creates a DRAFT sales contract.
     *
     * @param request the creation request
     * @return the created contract
     */
    @Transactional
    public ContractResponse create(CreateContractRequest request) {
        UUID tenantId = requireTenantId();
        UUID callerId = requireUserId();

        // Resolve effective agentId — AGENT callers cannot delegate to another agent
        UUID effectiveAgentId = resolveAgentId(request.agentId(), callerId);

        // 1. Assert project exists in tenant and is ACTIVE
        Project project = projectActiveGuard.requireActive(tenantId, request.projectId());

        // 2. Load property — must exist in tenant
        Property property = propertyRepository.findByTenant_IdAndId(tenantId, request.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(request.propertyId()));

        // 3. Property must belong to the given project
        if (!property.getProject().getId().equals(project.getId())) {
            throw new PropertyNotFoundException(request.propertyId());
        }

        // 4. Load buyer contact — must exist in tenant
        Contact buyer = contactRepository.findByTenant_IdAndId(tenantId, request.buyerContactId())
                .orElseThrow(() -> new ContactNotFoundException(request.buyerContactId()));

        // 5. Load agent — must exist in tenant
        User agent = userRepository.findByTenant_IdAndId(tenantId, effectiveAgentId)
                .orElseThrow(() -> new ContactNotFoundException(effectiveAgentId));

        // 6. Validate sourceDepositId if provided
        if (request.sourceDepositId() != null) {
            validateSourceDeposit(tenantId, request.sourceDepositId(),
                    request.propertyId(), request.buyerContactId(), effectiveAgentId);
        }

        // 7. Load tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        // 8. Build and persist
        SaleContract contract = new SaleContract(tenant, project, property, buyer, agent);
        contract.setAgreedPrice(request.agreedPrice());
        contract.setListPrice(request.listPrice());
        contract.setSourceDepositId(request.sourceDepositId());

        contract = contractRepository.save(contract);
        auditService.record(tenantId, AuditEventType.CONTRACT_CREATED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== Sign =====

    /**
     * Transitions a DRAFT contract to SIGNED.
     * Side effect: marks the property as SOLD via {@link PropertyCommercialWorkflowService}.
     *
     * @param contractId the contract to sign
     * @return the updated contract
     */
    @Transactional
    public ContractResponse sign(UUID contractId) {
        UUID tenantId = requireTenantId();
        UUID callerId = requireUserId();

        SaleContract contract = contractRepository.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (contract.getStatus() != SaleContractStatus.DRAFT) {
            throw new InvalidContractStateException(
                    "Only DRAFT contracts can be signed; current status: " + contract.getStatus());
        }

        // Assert project still ACTIVE
        projectActiveGuard.requireActive(tenantId, contract.getProject().getId());

        // Lock ordering: Property first to avoid deadlocks with cancel() and DepositService flows.
        UUID propertyId = contract.getProperty().getId();
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // Service-layer guard against double signing
        if (contractRepository.existsByTenant_IdAndProperty_IdAndStatusAndCanceledAtIsNull(
                tenantId, propertyId, SaleContractStatus.SIGNED)) {
            throw new PropertyAlreadySoldException(propertyId);
        }

        LocalDateTime signedAt = LocalDateTime.now();
        contract.setStatus(SaleContractStatus.SIGNED);
        contract.setSignedAt(signedAt);

        // Capture buyer snapshot immutably — decouples legal/commercial record from future Contact edits
        captureBuyerSnapshot(contract);

        try {
            contract = contractRepository.save(contract);
            contractRepository.flush();

            // Move property to SOLD
            propertyWorkflow.sell(property, signedAt);

        } catch (DataIntegrityViolationException e) {
            // Partial unique index uk_sc_property_signed was violated — race condition caught
            throw new PropertyAlreadySoldException(propertyId);
        }

        auditService.record(tenantId, AuditEventType.CONTRACT_SIGNED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== Cancel =====

    /**
     * Transitions a DRAFT or SIGNED contract to CANCELED.
     * <p>
     * Side effect (only when canceling from SIGNED):
     * If an active CONFIRMED deposit still exists for the property, reverts to RESERVED;
     * otherwise reverts to AVAILABLE (PropertyStatus.ACTIVE).
     *
     * @param contractId the contract to cancel
     * @return the updated contract
     */
    @Transactional
    public ContractResponse cancel(UUID contractId) {
        UUID tenantId = requireTenantId();
        UUID callerId = requireUserId();

        SaleContract contract = contractRepository.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (contract.getStatus() == SaleContractStatus.CANCELED) {
            throw new InvalidContractStateException("Contract is already CANCELED");
        }

        boolean wasSigned = contract.getStatus() == SaleContractStatus.SIGNED;

        // Lock ordering: Property first to avoid deadlocks with sign() and DepositService flows.
        // Acquire property lock and evaluate the deposit check BEFORE saving the contract.
        Property property = null;
        boolean hasActiveDeposit = false;
        if (wasSigned) {
            UUID propertyId = contract.getProperty().getId();
            // Lock ordering: Property first to avoid deadlocks.
            property = propertyRepository.findByTenantIdAndIdForUpdate(tenantId, propertyId)
                    .orElseThrow(() -> new PropertyNotFoundException(propertyId));
            hasActiveDeposit = depositRepository.existsActiveConfirmedDepositForProperty(tenantId, propertyId);
        }

        contract.setStatus(SaleContractStatus.CANCELED);
        contract.setCanceledAt(LocalDateTime.now());
        contract = contractRepository.save(contract);

        // Revert property commercial status if the contract had been signed (property was SOLD)
        if (wasSigned) {
            if (hasActiveDeposit) {
                // [OPEN POINT] Keeping buyer association on existing deposit is out-of-scope MVP
                propertyWorkflow.cancelSaleToReserved(property);
            } else {
                propertyWorkflow.cancelSaleToAvailable(property);
            }
        }

        auditService.record(tenantId, AuditEventType.CONTRACT_CANCELED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== List =====

    /**
     * Returns contracts for the current tenant, optionally filtered.
     * AGENT callers automatically have their agentId injected (cannot see others' contracts).
     *
     * @param status    optional status filter
     * @param projectId optional project filter
     * @param agentId   optional agent filter (ignored/overridden for AGENT callers)
     * @param from      optional signedAt lower bound
     * @param to        optional signedAt upper bound
     */
    /**
     * Returns a single contract by ID (tenant-scoped).
     * AGENT callers may only access their own contracts (cross-ownership → 404).
     */
    public ContractResponse getById(UUID contractId) {
        UUID tenantId = requireTenantId();
        SaleContract contract = contractRepository.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
        if (callerIsAgent()) {
            UUID callerId = requireUserId();
            if (!callerId.equals(contract.getAgent().getId())) {
                throw new ContractNotFoundException(contractId); // 404 to avoid info leak
            }
        }
        return ContractResponse.from(contract);
    }

    public List<ContractResponse> list(
            SaleContractStatus status,
            UUID projectId,
            UUID agentId,
            LocalDateTime from,
            LocalDateTime to) {

        UUID tenantId = requireTenantId();

        // AGENT role: force agentId = caller, ignoring any provided agentId
        UUID effectiveAgentFilter = callerIsAgent()
                ? requireUserId()
                : agentId;

        return contractRepository
                .filter(tenantId, status, projectId, effectiveAgentFilter, from, to)
                .stream()
                .map(ContractResponse::from)
                .toList();
    }

    // ===== Private helpers =====

    /**
     * Captures an immutable buyer snapshot on the contract from the linked Contact.
     * Called once when the contract transitions to SIGNED.
     * Snapshot is independent of future edits to the Contact record.
     *
     * <p>BuyerType defaults to {@link BuyerType#PERSON} — all current ContactType values
     * (PROSPECT, TEMP_CLIENT, CLIENT) represent individuals.
     * TODO: derive COMPANY from Contact when company-contact support is introduced.
     */
    private void captureBuyerSnapshot(SaleContract contract) {
        Contact buyer = contract.getBuyerContact(); // lazy-loaded within @Transactional context
        contract.setBuyerType(BuyerType.PERSON);
        contract.setBuyerDisplayName(buyer.getFullName());
        contract.setBuyerPhone(buyer.getPhone());
        contract.setBuyerEmail(buyer.getEmail());
        contract.setBuyerIce(buyer.getNationalId());
        contract.setBuyerAddress(buyer.getAddress());
    }

    private UUID resolveAgentId(UUID requestedAgentId, UUID callerId) {
        if (callerIsAgent()) {
            // AGENT must not set another agent's ID
            if (requestedAgentId != null && !requestedAgentId.equals(callerId)) {
                throw new ContractDepositMismatchException(
                        "AGENT callers may only create contracts for themselves (agentId must be null or equal to your own user ID)");
            }
            return callerId;
        }
        // ADMIN / MANAGER: agentId is required
        if (requestedAgentId == null) {
            throw new ContractDepositMismatchException("agentId is required for ADMIN/MANAGER callers");
        }
        return requestedAgentId;
    }

    private void validateSourceDeposit(UUID tenantId, UUID depositId,
                                       UUID propertyId, UUID buyerContactId, UUID agentId) {
        Deposit deposit = depositRepository.findByTenant_IdAndId(tenantId, depositId)
                .orElseThrow(() -> new ContractDepositMismatchException(
                        "deposit " + depositId + " not found in tenant"));

        if (deposit.getStatus() != DepositStatus.CONFIRMED) {
            throw new ContractDepositMismatchException(
                    "deposit must be CONFIRMED; current status: " + deposit.getStatus());
        }
        if (!propertyId.equals(deposit.getPropertyId())) {
            throw new ContractDepositMismatchException(
                    "deposit propertyId does not match contract propertyId");
        }
        if (!buyerContactId.equals(deposit.getContact().getId())) {
            throw new ContractDepositMismatchException(
                    "deposit contactId does not match contract buyerContactId");
        }
        if (!agentId.equals(deposit.getAgent().getId())) {
            throw new ContractDepositMismatchException(
                    "deposit agentId does not match contract agentId");
        }
    }

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AGENT"));
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new IllegalStateException("Missing tenant context");
        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId = TenantContext.getUserId();
        if (userId == null) throw new IllegalStateException("Missing user context");
        return userId;
    }
}
