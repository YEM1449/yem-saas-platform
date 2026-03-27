package com.yem.hlm.backend.contract.template.repo;

import com.yem.hlm.backend.contract.template.domain.ContractTemplate;
import com.yem.hlm.backend.contract.template.domain.TemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {

    Optional<ContractTemplate> findBySocieteIdAndTemplateType(UUID societeId, TemplateType templateType);

    List<ContractTemplate> findBySocieteId(UUID societeId);

    boolean existsBySocieteIdAndTemplateType(UUID societeId, TemplateType templateType);

    void deleteBySocieteIdAndTemplateType(UUID societeId, TemplateType templateType);
}
