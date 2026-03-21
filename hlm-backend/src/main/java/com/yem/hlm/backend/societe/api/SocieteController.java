package com.yem.hlm.backend.societe.api;

import com.yem.hlm.backend.societe.SocieteService;
import com.yem.hlm.backend.societe.annotation.RequiresSuperAdmin;
import com.yem.hlm.backend.societe.api.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/societes")
public class SocieteController {

    private final SocieteService societeService;

    public SocieteController(SocieteService societeService) {
        this.societeService = societeService;
    }

    @GetMapping
    @RequiresSuperAdmin
    public ResponseEntity<List<SocieteDto>> list() {
        return ResponseEntity.ok(societeService.listSocietes());
    }

    @GetMapping("/{id}")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(societeService.getSociete(id));
    }

    @PostMapping
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDto> create(@Valid @RequestBody CreateSocieteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(societeService.createSociete(req));
    }

    @PutMapping("/{id}")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDto> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateSocieteRequest req) {
        return ResponseEntity.ok(societeService.updateSociete(id, req));
    }

    @DeleteMapping("/{id}/deactivate")
    @RequiresSuperAdmin
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        societeService.deactivateSociete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/users")
    @RequiresSuperAdmin
    public ResponseEntity<AppUserSocieteDto> addUser(@PathVariable UUID id,
                                                      @Valid @RequestBody AddUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(societeService.addUserToSociete(id, req));
    }

    @DeleteMapping("/{id}/users/{userId}")
    @RequiresSuperAdmin
    public ResponseEntity<Void> removeUser(@PathVariable UUID id, @PathVariable UUID userId) {
        societeService.removeUserFromSociete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
