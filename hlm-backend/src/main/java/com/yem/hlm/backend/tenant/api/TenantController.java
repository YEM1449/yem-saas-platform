package com.yem.hlm.backend.tenant.api;

import com.yem.hlm.backend.tenant.api.dto.TenantCreateRequest;
import com.yem.hlm.backend.tenant.api.dto.TenantResponse;
import com.yem.hlm.backend.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/tenants")
public class TenantController {
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        TenantResponse response = tenantService.createTenantWithOwner(request);
        URI location = URI.create("/tenants/" + response.tenant().id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public TenantResponse getTenant(@PathVariable("id") UUID id) {
        return tenantService.getTenant(id);
    }
}
