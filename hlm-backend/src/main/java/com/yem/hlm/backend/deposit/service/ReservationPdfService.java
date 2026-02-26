package com.yem.hlm.backend.deposit.service;

import com.yem.hlm.backend.contact.service.PropertyNotFoundException;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a minimal PDF reservation certificate for a given deposit.
 * <p>
 * Uses Apache PDFBox 3.x (Apache 2.0 licence).
 * Output is deterministic for a fixed dataset: no random assets, no system fonts.
 * Only built-in PDF Type 1 fonts are used (Helvetica / Helvetica-Bold).
 */
@Service
@Transactional(readOnly = true)
public class ReservationPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Page geometry
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float LINE_HEIGHT = 16f;
    private static final float SECTION_GAP = 10f;

    // Fonts
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final DepositRepository depositRepository;
    private final PropertyRepository propertyRepository;

    public ReservationPdfService(DepositRepository depositRepository,
                                  PropertyRepository propertyRepository) {
        this.depositRepository = depositRepository;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Generates a PDF reservation certificate for the given deposit.
     * Tenant-scoped: rejects cross-tenant access (404).
     *
     * @param depositId the deposit UUID
     * @return PDF bytes (starts with {@code %PDF})
     * @throws DepositNotFoundException  if deposit not found for this tenant
     * @throws ReservationPdfGenerationException if PDF generation fails
     */
    public byte[] generate(UUID depositId) {
        UUID tenantId = TenantContext.getTenantId();

        Deposit deposit = depositRepository.findByTenant_IdAndId(tenantId, depositId)
                .orElseThrow(() -> new DepositNotFoundException(depositId));

        // Load property if linked
        Property property = null;
        if (deposit.getPropertyId() != null) {
            property = propertyRepository
                    .findByTenant_IdAndIdAndDeletedAtIsNull(tenantId, deposit.getPropertyId())
                    .orElseThrow(() -> new PropertyNotFoundException(deposit.getPropertyId()));
        }

        return buildPdf(deposit, property);
    }

    // =========================================================================
    // PDF generation
    // =========================================================================

    private byte[] buildPdf(Deposit deposit, Property property) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float startY = PDRectangle.A4.getHeight() - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = startY;

                // ── Header ──────────────────────────────────────────────────
                y = writeTitle(cs, "ATTESTATION DE RÉSERVATION", y);
                y -= SECTION_GAP;

                // ── Company / Tenant ─────────────────────────────────────────
                y = writeSectionHeader(cs, "SOCIÉTÉ / PROMOTEUR", y);
                y = writeField(cs, "Société", deposit.getTenant().getName(), y);
                y -= SECTION_GAP;

                // ── Project & Property ───────────────────────────────────────
                y = writeSectionHeader(cs, "BIEN IMMOBILIER", y);
                if (property != null) {
                    y = writeField(cs, "Projet", property.getProject() != null
                            ? property.getProject().getName() : "—", y);
                    y = writeField(cs, "Référence", property.getReferenceCode(), y);
                    y = writeField(cs, "Désignation", property.getTitle(), y);
                    y = writeField(cs, "Type", property.getType().name(), y);
                    if (property.getPrice() != null) {
                        y = writeField(cs, "Prix de vente",
                                property.getPrice().toPlainString() + " " + deposit.getCurrency(), y);
                    }
                } else {
                    y = writeField(cs, "Bien", "Non renseigné", y);
                }
                y -= SECTION_GAP;

                // ── Buyer (Contact) ───────────────────────────────────────────
                y = writeSectionHeader(cs, "ACQUÉREUR", y);
                y = writeField(cs, "Nom complet", deposit.getContact().getFullName(), y);
                y -= SECTION_GAP;

                // ── Deposit / Reservation ─────────────────────────────────────
                y = writeSectionHeader(cs, "RÉSERVATION", y);
                y = writeField(cs, "Référence réservation", deposit.getReference(), y);
                y = writeField(cs, "Statut", deposit.getStatus().name(), y);
                y = writeField(cs, "Acompte versé",
                        deposit.getAmount().toPlainString() + " " + deposit.getCurrency(), y);
                y = writeField(cs, "Date de réservation", deposit.getDepositDate().format(DATE_FMT), y);
                if (deposit.getDueDate() != null) {
                    y = writeField(cs, "Échéance", deposit.getDueDate().format(DATETIME_FMT), y);
                }
                if (deposit.getConfirmedAt() != null) {
                    y = writeField(cs, "Confirmée le", deposit.getConfirmedAt().format(DATETIME_FMT), y);
                }
                if (deposit.getCancelledAt() != null) {
                    y = writeField(cs, "Annulée le", deposit.getCancelledAt().format(DATETIME_FMT), y);
                }
                if (deposit.getNotes() != null && !deposit.getNotes().isBlank()) {
                    y = writeField(cs, "Notes", deposit.getNotes(), y);
                }
                y -= SECTION_GAP;

                // ── Agent ─────────────────────────────────────────────────────
                y = writeSectionHeader(cs, "AGENT COMMERCIAL", y);
                y = writeField(cs, "Agent", deposit.getAgent().getEmail(), y);
                y -= SECTION_GAP;

                // ── Footer ────────────────────────────────────────────────────
                y = writeSectionHeader(cs, "DOCUMENT", y);
                writeField(cs, "Généré le", LocalDateTime.now().format(DATETIME_FMT), y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ReservationPdfGenerationException("Failed to generate PDF for deposit " + depositId(deposit), e);
        }
    }

    // =========================================================================
    // Rendering helpers
    // =========================================================================

    private float writeTitle(PDPageContentStream cs, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(FONT_BOLD, 16);
        float textWidth = FONT_BOLD.getStringWidth(text) / 1000f * 16;
        cs.newLineAtOffset((PAGE_WIDTH - textWidth) / 2f, y);
        cs.showText(text);
        cs.endText();
        drawHorizontalLine(cs, y - 4);
        return y - LINE_HEIGHT - 8;
    }

    private float writeSectionHeader(PDPageContentStream cs, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(FONT_BOLD, 11);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(text);
        cs.endText();
        drawHorizontalLine(cs, y - 2);
        return y - LINE_HEIGHT;
    }

    /** Writes a label–value pair. Label is bold, value is regular. */
    private float writeField(PDPageContentStream cs, String label, String value, float y) throws IOException {
        float labelWidth = 130f; // fixed column width for alignment
        String safeValue = sanitize(value);

        // Label
        cs.beginText();
        cs.setFont(FONT_BOLD, 10);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(label + " :");
        cs.endText();

        // Value — wrap if needed
        List<String> lines = wrapText(safeValue, FONT_REGULAR, 10, CONTENT_WIDTH - labelWidth);
        for (int i = 0; i < lines.size(); i++) {
            cs.beginText();
            cs.setFont(FONT_REGULAR, 10);
            cs.newLineAtOffset(MARGIN + labelWidth, y - i * LINE_HEIGHT);
            cs.showText(lines.get(i));
            cs.endText();
        }

        return y - Math.max(1, lines.size()) * LINE_HEIGHT;
    }

    private void drawHorizontalLine(PDPageContentStream cs, float y) throws IOException {
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_WIDTH - MARGIN, y);
        cs.setLineWidth(0.5f);
        cs.stroke();
    }

    /** Splits text into lines that fit within {@code maxWidth} pixels at given font/size. */
    private List<String> wrapText(String text, PDType1Font font, float size, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("—");
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float w = font.getStringWidth(candidate) / 1000f * size;
            if (w > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    /** Strips non-ASCII / control chars that PDFBox Type1 fonts cannot render. */
    private String sanitize(String s) {
        if (s == null) return "—";
        return s.chars()
                .filter(c -> c >= 0x20 && c <= 0x7E)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private UUID depositId(Deposit d) {
        return d.getId();
    }
}
