package com.yem.hlm.backend.vente.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates temporal ordering constraints across the vente pipeline.
 *
 * <h3>Rules enforced:</h3>
 * <ul>
 *   <li>dateReservation ≤ dateCompromis</li>
 *   <li>dateCompromis ≤ dateActeNotarie</li>
 *   <li>dateActeNotarie ≤ dateLivraisonPrevue</li>
 *   <li>dateEcheance ≥ dateCompromis (when set)</li>
 *   <li>datePaiement ≥ dateEcheance (when both set)</li>
 * </ul>
 */
@Component
public class DateCoherenceValidator {

    /**
     * Validates dates supplied when creating or updating a vente.
     *
     * @param dateReservation    the reservation date (lower bound — may be null)
     * @param dateCompromis      the compromis date (may be null)
     * @param dateActeNotarie    the notarial deed date (may be null)
     * @param dateLivraisonPrevue the planned delivery date (may be null)
     * @throws DateCoherenceException if any rule is violated
     */
    public void validateVenteDates(
            LocalDate dateReservation,
            LocalDate dateCompromis,
            LocalDate dateActeNotarie,
            LocalDate dateLivraisonPrevue
    ) {
        List<DateCoherenceViolation> violations = new ArrayList<>();

        if (dateReservation != null && dateCompromis != null
                && dateCompromis.isBefore(dateReservation)) {
            violations.add(new DateCoherenceViolation(
                    "dateCompromis",
                    "La date de compromis ne peut pas être antérieure à la date de réservation",
                    "dateReservation"
            ));
        }

        if (dateCompromis != null && dateActeNotarie != null
                && dateActeNotarie.isBefore(dateCompromis)) {
            violations.add(new DateCoherenceViolation(
                    "dateActeNotarie",
                    "La date de l'acte notarié ne peut pas être antérieure à la date de compromis",
                    "dateCompromis"
            ));
        }

        if (dateActeNotarie != null && dateLivraisonPrevue != null
                && dateLivraisonPrevue.isBefore(dateActeNotarie)) {
            violations.add(new DateCoherenceViolation(
                    "dateLivraisonPrevue",
                    "La date de livraison prévue ne peut pas être antérieure à la date de l'acte notarié",
                    "dateActeNotarie"
            ));
        }

        if (!violations.isEmpty()) {
            throw new DateCoherenceException(violations);
        }
    }

    /**
     * Validates dates for a vente écheance (payment instalment).
     *
     * @param dateCompromis the vente's compromis date (lower bound — may be null)
     * @param dateEcheance  the instalment due date
     * @param datePaiement  the actual payment date (may be null if not yet paid)
     * @throws DateCoherenceException if any rule is violated
     */
    public void validateEcheanceDates(
            LocalDate dateCompromis,
            LocalDate dateEcheance,
            LocalDate datePaiement
    ) {
        List<DateCoherenceViolation> violations = new ArrayList<>();

        if (dateCompromis != null && dateEcheance != null
                && dateEcheance.isBefore(dateCompromis)) {
            violations.add(new DateCoherenceViolation(
                    "dateEcheance",
                    "La date d'échéance ne peut pas être antérieure à la date de compromis",
                    "dateCompromis"
            ));
        }

        if (dateEcheance != null && datePaiement != null
                && datePaiement.isBefore(dateEcheance)) {
            violations.add(new DateCoherenceViolation(
                    "datePaiement",
                    "La date de paiement ne peut pas être antérieure à la date d'échéance prévue",
                    "dateEcheance"
            ));
        }

        if (!violations.isEmpty()) {
            throw new DateCoherenceException(violations);
        }
    }
}
