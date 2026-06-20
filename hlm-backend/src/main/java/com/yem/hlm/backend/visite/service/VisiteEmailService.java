package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.visite.domain.DestinataireRappel;
import com.yem.hlm.backend.visite.domain.TypeRappel;
import com.yem.hlm.backend.visite.domain.Visite;
import com.yem.hlm.backend.visite.domain.VisiteRappel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds and sends the visite emails (rappels + annulation) via the configured {@link EmailSender}
 * (Brevo in production). All times are rendered in Africa/Casablanca (RG-V10) — never UTC.
 */
@Service
public class VisiteEmailService {

    /** Morocco is UTC+1 year-round (no DST). */
    static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH);

    private final EmailSender emailSender;

    public VisiteEmailService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    /** Human-readable Casablanca rendering of an instant (RG-V10). */
    public static String formatCasablanca(Instant instant) {
        return FMT.format(instant.atZone(CASABLANCA));
    }

    /**
     * Resolves the recipient email for a reminder, or {@code null} if unknown
     * (e.g. the prospect has no email on file). A null recipient is treated as "nothing to do".
     */
    public String destinataireEmail(Visite visite, DestinataireRappel cible) {
        if (cible == DestinataireRappel.AGENT) {
            return visite.getAgent() == null ? null : visite.getAgent().getEmail();
        }
        return visite.getContact() == null ? null : visite.getContact().getEmail();
    }

    /** Sends one reminder email (RG-V07). Throws on provider error so the job can retry. */
    public void envoyerRappel(Visite visite, VisiteRappel rappel, String to) {
        String quand = formatCasablanca(visite.getDateHeure());
        String contactNom = visite.getContact() == null ? "" : visite.getContact().getFullName();
        String horizon = rappel.getType() == TypeRappel.H24 ? "demain" : "dans 1 heure";

        String subject = "Rappel de visite — " + quand;
        String body;
        if (rappel.getDestinataire() == DestinataireRappel.AGENT) {
            body = "Bonjour,\n\nRappel : vous avez une visite prévue " + horizon + ", le " + quand
                    + ", avec " + contactNom + "."
                    + (visite.getLieu() != null && !visite.getLieu().isBlank() ? "\nLieu : " + visite.getLieu() : "")
                    + "\n\n— YEM HLM";
        } else {
            body = "Bonjour,\n\nNous vous rappelons votre rendez-vous de visite prévu le " + quand + "."
                    + (visite.getLieu() != null && !visite.getLieu().isBlank() ? "\nLieu : " + visite.getLieu() : "")
                    + "\n\nÀ très bientôt,\n— YEM HLM";
        }
        emailSender.send(to, subject, body);
    }

    /** Sends a cancellation email to the prospect when a CONFIRMEE visite is cancelled (RG-V08). */
    public void envoyerAnnulation(Visite visite, String raison) {
        if (visite.getContact() == null || visite.getContact().getEmail() == null
                || visite.getContact().getEmail().isBlank()) {
            return;
        }
        String quand = formatCasablanca(visite.getDateHeure());
        String subject = "Annulation de votre visite — " + quand;
        String body = "Bonjour,\n\nVotre rendez-vous de visite prévu le " + quand + " a été annulé."
                + (raison != null && !raison.isBlank() ? "\nMotif : " + raison : "")
                + "\n\nNous reviendrons vers vous pour convenir d'un nouveau créneau.\n— YEM HLM";
        emailSender.send(visite.getContact().getEmail(), subject, body);
    }
}
