package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.vente.domain.VenteStatut;

public class InvalidVenteTransitionException extends RuntimeException {
    public InvalidVenteTransitionException(VenteStatut from, VenteStatut to) {
        super("Transition impossible : " + from + " → " + to);
    }
}
