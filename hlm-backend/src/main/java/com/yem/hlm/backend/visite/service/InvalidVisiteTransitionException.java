package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.visite.domain.StatutVisite;

/** Thrown when a visite status transition is not allowed by RG-V02 (409). */
public class InvalidVisiteTransitionException extends RuntimeException {
    public InvalidVisiteTransitionException(StatutVisite from, StatutVisite to) {
        super("Transition de visite impossible : " + from + " → " + to);
    }
}
