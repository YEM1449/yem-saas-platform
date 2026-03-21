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
import com.yem.hlm.backend.societe.SocieteContext;
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
 */
@Service
@Transactional(readOnly = true)
public class SaleContractService {

    private final SaleContractRepository contractRepository;
    private final ProjectActiveGuard projectActiveGuard;
    private final PropertyRepository propertyRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final DepositRepository depositRepository;
    private final PropertyCommercialWorkflowService propertyWorkflow;
    private final CommercialAuditService auditService;

    public SaleContractService(
            SaleContractRepository contractRepository,
            ProjectActiveGuard projectActiveGuard,
            PropertyRepository propertyRepository,
            ContactRepository contactRepository,
            UserRepository userRepository,
            DepositRepository depositRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            CommercialAuditService auditService) {
        this.contractRepository = contractRepository;
        this.projectActiveGuard = projectActiveGuard;
        this.propertyRepository = propertyRepository;
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
        this.depositRepository = depositRepository;
        this.propertyWorkflow = propertyWorkflow;
        this.auditService = auditService;
    }

    // ===== Create =====

    @Transactional
    public ContractResponse create(CreateContractRequest request) {
        UUID societeId = requireSocieteId();
        UUID callerId = requireUserId();

        UUID effectiveAgentId = resolveAgentId(request.agentId(), callerId);

        // 1. Assert project exists in société and is ACTIVE
        Project project = projectActiveGuard.requireActive(societeId, request.projectId());

        // 2. Load property — must exist in société
        Property property = propertyRepository.findBySocieteIdAndId(societeId, request.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(request.propertyId()));

        // 3. Property must belong to the given project
        if (!property.getProject().getId().equals(project.getId())) {
            throw new PropertyNotFoundException(request.propertyId());
        }

        // 4. Load buyer contact — must exist in société
        Contact buyer = contactRepository.findBySocieteIdAndId(societeId, request.buyerContactId())
                .orElseThrow(() -> new ContactNotFoundException(request.buyerContactId()));

        // 5. Load agent — global lookup (user is not société-scoped at entity level)
        User agent = userRepository.findById(effectiveAgentId)
                .orElseThrow(() -> new ContactNotFoundException(effectiveAgentId));

        // 6. Validate sourceDepositId if provided
        if (request.sourceDepositId() != null) {
            validateSourceDeposit(societeId, request.sourceDepositId(),
                    request.propertyId(), request.buyerContactId(), effectiveAgentId);
        }

        // 7. Build and persist
        SaleContract contract = new SaleContract(societeId, project, property, buyer, agent);
        contract.setAgreedPrice(request.agreedPrice());
        contract.setListPrice(request.listPrice());
        contract.setSourceDepositId(request.sourceDepositId());

        contract = contractRepository.save(contract);
        auditService.record(societeId, AuditEventType.CONTRACT_CREATED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== Sign =====

    @Transactional
    public ContractResponse sign(UUID contractId) {
        UUID societeId = requireSocieteId();
        UUID callerId = requireUserId();

        SaleContract contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (contract.getStatus() != SaleContractStatus.DRAFT) {
            throw new InvalidContractStateException(
                    "Only DRAFT contracts can be signed; current status: " + contract.getStatus());
        }

        projectActiveGuard.requireActive(societeId, contract.getProject().getId());

        UUID propertyId = contract.getProperty().getId();
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        if (contractRepository.existsBySocieteIdAndProperty_IdAndStatusAndCanceledAtIsNull(
                societeId, propertyId, SaleContractStatus.SIGNED)) {
            throw new PropertyAlreadySoldException(propertyId);
        }

        LocalDateTime signedAt = LocalDateTime.now();
        contract.setStatus(SaleContractStatus.SIGNED);
        contract.setSignedAt(signedAt);

        captureBuyerSnapshot(contract);

        try {
            contract = contractRepository.save(contract);
            contractRepository.flush();
            propertyWorkflow.sell(property, signedAt);
        } catch (DataIntegrityViolationException e) {
            throw new PropertyAlreadySoldException(propertyId);
        }

        auditService.record(societeId, AuditEventType.CONTRACT_SIGNED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== Cancel =====

    @Transactional
    public ContractResponse cancel(UUID contractId) {
        UUID societeId = requireSocieteId();
        UUID callerId = requireUserId();

        SaleContract contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (contract.getStatus() == SaleContractStatus.CANCELED) {
            throw new InvalidContractStateException("Contract is already CANCELED");
        }

        boolean wasSigned = contract.getStatus() == SaleContractStatus.SIGNED;

        Property property = null;
        boolean hasActiveDeposit = false;
        if (wasSigned) {
            UUID propertyId = contract.getProperty().getId();
            property = propertyRepository.findByTenantIdAndIdForUpdate(societeId, propertyId)
                    .orElseThrow(() -> new PropertyNotFoundException(propertyId));
            hasActiveDeposit = depositRepository.existsActiveConfirmedDepositForProperty(societeId, propertyId);
        }

        contract.setStatus(SaleContractStatus.CANCELED);
        contract.setCanceledAt(LocalDateTime.now());
        contract = contractRepository.save(contract);

        if (wasSigned) {
            if (hasActiveDeposit) {
                propertyWorkflow.cancelSaleToReserved(property);
            } else {
                propertyWorkflow.cancelSaleToAvailable(property);
            }
        }

        auditService.record(societeId, AuditEventType.CONTRACT_CANCELED, callerId,
                "CONTRACT", contract.getId(), null);
        return ContractResponse.from(contract);
    }

    // ===== List / Get =====

    public ContractResponse getById(UUID contractId) {
        UUID societeId = requireSocieteId();
        SaleContract contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
        if (callerIsAgent()) {
            UUID callerId = requireUserId();
            if (!callerId.equals(contract.getAgent().getId())) {
                throw new ContractNotFoundException(contractId);
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

        UUID societeId = requireSocieteId();

        UUID effectiveAgentFilter = callerIsAgent()
                ? requireUserId()
                : agentId;

        return contractRepository
                .filter(societeId, status, projectId, effectiveAgentFilter, from, to)
                .stream()
                .map(ContractResponse::from)
                .toList();
    }

    // ===== Private helpers =====

    private void captureBuyerSnapshot(SaleContract contract) {
        Contact buyer = contract.getBuyerContact();
        contract.setBuyerType(BuyerType.PERSON);
        contract.setBuyerDisplayName(buyer.getFullName());
        contract.setBuyerPhone(buyer.getPhone());
        contract.setBuyerEmail(buyer.getEmail());
        contract.setBuyerIce(buyer.getNationalId());
        contract.setBuyerAddress(buyer.getAddress());
    }

    private UUID resolveAgentId(UUID requestedAgentId, UUID callerId) {
        if (callerIsAgent()) {
            if (requestedAgentId != null && !requestedAgentId.equals(callerId)) {
                throw new ContractDepositMismatchException(
                        "AGENT callers may only create contracts for themselves");
            }
            return callerId;
        }
        if (requestedAgentId == null) {
            throw new ContractDepositMismatchException("agentId is required for ADMIN/MANAGER callers");
        }
        return requestedAgentId;
    }

    private void validateSourceDeposit(UUID societeId, UUID depositId,
                                       UUID propertyId, UUID buyerContactId, UUID agentId) {
        Deposit deposit = depositRepository.findBySocieteIdAndId(societeId, depositId)
                .orElseThrow(() -> new ContractDepositMismatchException(
                        "deposit " + depositId + " not found in société"));

        if (deposit.getStatus() != DepositStatus.CONFIRMED) {
            throw new ContractDepositMismatchException(
                    "deposit must be CONFIRMED; current status: " + deposit.getStatus());
        }
        if (!propertyId.equals(deposit.getPropertyId())) {
            throw new ContractDepositMismatchException("deposit propertyId does not match contract propertyId");
        }
        if (!buyerContactId.equals(deposit.getContact().getId())) {
            throw new ContractDepositMismatchException("deposit contactId does not match contract buyerContactId");
        }
        if (!agentId.equals(deposit.getAgent().getId())) {
            throw new ContractDepositMismatchException("deposit agentId does not match contract agentId");
        }
    }

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AGENT"));
    }

    private UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) throw new IllegalStateException("Missing société context");
        return societeId;
    }

    private UUID requireUserId() {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) throw new IllegalStateException("Missing user context");
        return userId;
    }
}
