package com.yem.hlm.backend.tranche.service;

import com.yem.hlm.backend.tranche.domain.TrancheStatut;

public class InvalidTrancheTransitionException extends RuntimeException {
    public InvalidTrancheTransitionException(TrancheStatut from, TrancheStatut to) {
        super("Transition de tranche invalide : " + from + " → " + to);
    }
}
