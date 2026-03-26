package com.yem.hlm.backend.reservation.api;

import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.reservation.api.dto.ConvertReservationToDepositRequest;
import com.yem.hlm.backend.reservation.api.dto.CreateReservationRequest;
import com.yem.hlm.backend.reservation.api.dto.ReservationResponse;
import com.yem.hlm.backend.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Reservations", description = "Property reservation (hold) management")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Create a reservation — ADMIN/MANAGER only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(request);
    }

    /** Get a single reservation by ID. */
    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return reservationService.get(id);
    }

    /** List all reservations for the current tenant. */
    @GetMapping
    public List<ReservationResponse> list() {
        return reservationService.list();
    }

    /** Cancel an ACTIVE reservation — ADMIN/MANAGER only. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return reservationService.cancel(id);
    }

    /**
     * Convert an ACTIVE reservation into a formal Deposit — ADMIN/MANAGER only.
     * Returns the newly created DepositResponse.
     */
    @PostMapping("/{id}/convert-to-deposit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DepositResponse convertToDeposit(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertReservationToDepositRequest request
    ) {
        return reservationService.convertToDeposit(id, request);
    }
}
