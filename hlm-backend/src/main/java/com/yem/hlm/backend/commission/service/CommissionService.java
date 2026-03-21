package com.yem.hlm.backend.commission.service;

import com.yem.hlm.backend.commission.api.dto.CommissionDTO;
import com.yem.hlm.backend.commission.api.dto.CommissionRuleRequest;
import com.yem.hlm.backend.commission.api.dto.CommissionRuleResponse;
import com.yem.hlm.backend.commission.domain.CommissionRule;
import com.yem.hlm.backend.commission.repo.CommissionRuleRepository;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.user.service.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Commission calculation and rule management.
 *
 * <h3>Rule priority</h3>
 * Project-specific rule → société-wide default. If no rule exists, commissionAmount = 0.
 *
 * <h3>Commission formula</h3>
 * commissionAmount = agreedPrice × ratePercent / 100 + fixedAmount (0 when null).
 */
@Service
@Transactional(readOnly = true)
public class CommissionService {

    private final CommissionRuleRepository ruleRepository;
    private final SaleContractRepository   contractRepository;
    private final ProjectRepository        projectRepository;
    private final UserRepository           userRepository;

    public CommissionService(CommissionRuleRepository ruleRepository,
                             SaleContractRepository contractRepository,
                             ProjectRepository projectRepository,
                             UserRepository userRepository) {
        this.ruleRepository     = ruleRepository;
        this.contractRepository = contractRepository;
        this.projectRepository  = projectRepository;
        this.userRepository     = userRepository;
    }

    // =========================================================================
    // Rule CRUD (ADMIN only)
    // =========================================================================

    public List<CommissionRuleResponse> listRules(UUID societeId) {
        return ruleRepository.findBySocieteIdOrderByEffectiveFromDesc(societeId)
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional
    public CommissionRuleResponse createRule(UUID societeId, CommissionRuleRequest req) {
        com.yem.hlm.backend.project.domain.Project project = null;
        if (req.projectId() != null) {
            project = projectRepository.findBySocieteIdAndId(societeId, req.projectId())
                    .orElseThrow(() -> new ProjectNotFoundException(req.projectId()));
        }

        CommissionRule rule = new CommissionRule(
                societeId, project,
                req.ratePercent(), req.fixedAmount(),
                req.effectiveFrom(), req.effectiveTo()
        );
        return toRuleResponse(ruleRepository.save(rule));
    }

    @Transactional
    public CommissionRuleResponse updateRule(UUID societeId, UUID ruleId, CommissionRuleRequest req) {
        CommissionRule rule = ruleRepository.findBySocieteIdAndId(societeId, ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(ruleId));

        com.yem.hlm.backend.project.domain.Project project = null;
        if (req.projectId() != null) {
            project = projectRepository.findBySocieteIdAndId(societeId, req.projectId())
                    .orElseThrow(() -> new ProjectNotFoundException(req.projectId()));
        }

        rule.setProject(project);
        rule.setRatePercent(req.ratePercent());
        rule.setFixedAmount(req.fixedAmount());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        return toRuleResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID societeId, UUID ruleId) {
        CommissionRule rule = ruleRepository.findBySocieteIdAndId(societeId, ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(ruleId));
        ruleRepository.delete(rule);
    }

    // =========================================================================
    // Commission calculation
    // =========================================================================

    /** Calculates commission for a single SIGNED contract. */
    public CommissionDTO calculateCommission(UUID societeId, UUID contractId) {
        SaleContract contract = contractRepository.findBySocieteIdAndId(societeId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
        return buildCommissionDTO(societeId, contract);
    }

    /**
     * Returns commission entries for all SIGNED contracts of a given agent in a date range.
     * Contracts without a matching rule return commissionAmount = 0.
     */
    public List<CommissionDTO> getAgentCommissions(UUID societeId, UUID agentId,
                                                   LocalDate from, LocalDate to) {
        if (agentId != null) {
            userRepository.findById(agentId)
                    .orElseThrow(() -> new UserNotFoundException(agentId));
        }

        return contractRepository.filter(societeId, SaleContractStatus.SIGNED,
                        null, agentId,
                        from != null ? from.atStartOfDay() : null,
                        to   != null ? to.atTime(23, 59, 59) : null)
                .stream()
                .map(c -> buildCommissionDTO(societeId, c))
                .toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private CommissionDTO buildCommissionDTO(UUID societeId, SaleContract contract) {
        LocalDate onDate = contract.getSignedAt() != null
                ? contract.getSignedAt().toLocalDate()
                : LocalDate.now();
        UUID projectId = contract.getProject() != null ? contract.getProject().getId() : null;

        CommissionRule rule = resolveRule(societeId, projectId, onDate);

        BigDecimal rate       = rule != null ? rule.getRatePercent() : BigDecimal.ZERO;
        BigDecimal fixed      = (rule != null && rule.getFixedAmount() != null)
                                    ? rule.getFixedAmount() : BigDecimal.ZERO;
        BigDecimal commission = contract.getAgreedPrice()
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .add(fixed);

        return new CommissionDTO(
                contract.getId(),
                contract.getAgent().getId(),
                contract.getAgent().getEmail(),
                contract.getProject().getName(),
                contract.getProperty().getReferenceCode(),
                contract.getSignedAt(),
                contract.getAgreedPrice(),
                rate, fixed, commission
        );
    }

    private CommissionRule resolveRule(UUID societeId, UUID projectId, LocalDate onDate) {
        if (projectId != null) {
            List<CommissionRule> projectRules =
                    ruleRepository.findProjectRule(societeId, projectId, onDate);
            if (!projectRules.isEmpty()) return projectRules.get(0);
        }
        List<CommissionRule> defaults = ruleRepository.findTenantDefaultRule(societeId, onDate);
        return defaults.isEmpty() ? null : defaults.get(0);
    }

    private CommissionRuleResponse toRuleResponse(CommissionRule r) {
        return new CommissionRuleResponse(
                r.getId(),
                r.getSocieteId(),
                r.getProject() != null ? r.getProject().getId() : null,
                r.getProject() != null ? r.getProject().getName() : null,
                r.getRatePercent(),
                r.getFixedAmount(),
                r.getEffectiveFrom(),
                r.getEffectiveTo()
        );
    }
}
