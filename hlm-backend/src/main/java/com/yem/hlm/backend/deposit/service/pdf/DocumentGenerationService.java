package com.yem.hlm.backend.deposit.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.yem.hlm.backend.deposit.service.ReservationPdfGenerationException;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Low-level PDF rendering service.
 *
 * <ol>
 *   <li>Renders a Thymeleaf HTML template to a UTF-8 HTML string.</li>
 *   <li>Passes the HTML string to OpenHTMLToPDF (which uses PDFBox 2.x internally)
 *       to produce a binary PDF.</li>
 * </ol>
 *
 * <h3>Template location</h3>
 * Templates are resolved from {@code classpath:/templates/<name>.html}
 * (standard Spring Boot Thymeleaf auto-configuration).
 *
 * <h3>CSS support</h3>
 * OpenHTMLToPDF supports CSS 2.1 + partial CSS 3 (no flexbox/grid).
 * All styles should be inlined in the template's {@code <style>} block for
 * reliable classpath resolution in the PDF context.
 */
@Service
public class DocumentGenerationService {

    private final TemplateEngine templateEngine;

    public DocumentGenerationService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Renders {@code templateName} with {@code variables} and returns PDF bytes.
     *
     * @param templateName Thymeleaf template path (without prefix/suffix),
     *                     e.g. {@code "documents/reservation"}
     * @param variables    variables to expose in the template context
     * @return PDF byte array (starts with {@code %PDF})
     * @throws ReservationPdfGenerationException on any rendering failure
     */
    public byte[] renderToPdf(String templateName, Map<String, Object> variables) {
        String html = renderHtml(templateName, variables);
        return convertToPdf(html);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String renderHtml(String templateName, Map<String, Object> variables) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariables(variables);
        return templateEngine.process(templateName, ctx);
    }

    private byte[] convertToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // baseUri null — template uses only inlined styles, no external resources.
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReservationPdfGenerationException("PDF rendering failed", e);
        }
    }
}
