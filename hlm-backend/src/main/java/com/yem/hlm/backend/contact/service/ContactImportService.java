package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.contact.api.dto.ContactImportReport;
import com.yem.hlm.backend.contact.api.dto.ContactImportReport.RowIssue;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.domain.ConsentMethod;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CSV contact import (finding #002 — Excel migration path for new sociétés).
 *
 * <p>Accepts UTF-8 CSV with {@code ;} or {@code ,} delimiter (auto-detected on the header),
 * quoted fields, and French or English header names (accents ignored). Required columns:
 * prénom + nom. Recognised optional columns: téléphone, email, CIN, adresse, notes.
 *
 * <p>Each valid row goes through {@link ContactService#create(CreateContactRequest)} so the
 * import enforces exactly the same rules as manual entry: contact quota, e-mail uniqueness,
 * phone-or-email requirement, Loi 09-08 consent/legal-basis guard, timeline + audit events.
 * Rows that fail are reported with their line number; the import is best-effort, not
 * all-or-nothing — re-running the same file later only creates what is still missing
 * (duplicates are skipped).
 *
 * <p><b>Not</b> {@code @Transactional} by design: each created contact commits on its own,
 * so one bad row never rolls back the 199 good ones.
 */
@Service
public class ContactImportService {

    private static final Logger log = LoggerFactory.getLogger(ContactImportService.class);

    static final int MAX_ROWS = 2000;

    /** Canonical field → accepted header spellings (lowercase, accents stripped). */
    private static final Map<String, Set<String>> HEADER_ALIASES = Map.of(
            "firstName",  Set.of("prenom", "firstname", "first_name", "first name"),
            "lastName",   Set.of("nom", "lastname", "last_name", "last name", "nom_famille", "nom de famille"),
            "phone",      Set.of("telephone", "phone", "tel", "gsm", "mobile", "portable"),
            "email",      Set.of("email", "e-mail", "mail", "courriel"),
            "nationalId", Set.of("cin", "nationalid", "national_id", "piece_identite", "piece d'identite"),
            "address",    Set.of("adresse", "address"),
            "notes",      Set.of("notes", "note", "commentaire", "commentaires"));

    private final ContactService contactService;
    private final ContactRepository contactRepository;
    private final Validator validator;
    private final SocieteContextHelper societeCtx;

    public ContactImportService(ContactService contactService,
                                ContactRepository contactRepository,
                                Validator validator,
                                SocieteContextHelper societeCtx) {
        this.contactService = contactService;
        this.contactRepository = contactRepository;
        this.validator = validator;
        this.societeCtx = societeCtx;
    }

    public ContactImportReport importCsv(byte[] csvBytes, boolean dryRun,
                                         boolean consentGiven, ProcessingBasis processingBasis) {
        UUID societeId = societeCtx.requireSocieteId();

        // Loi 09-08: same guard as manual entry, checked once for the whole file.
        if (!consentGiven && processingBasis == null) {
            throw new BusinessRuleException(ErrorCode.CONSENT_REQUIRED,
                    "Le consentement ou une base juridique est requis pour importer des contacts (Loi 09-08 Art. 4).");
        }

        List<List<String>> records = parseCsv(csvBytes);
        if (records.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.IMPORT_VALIDATION_ERROR,
                    "Le fichier CSV est vide.");
        }
        if (records.size() - 1 > MAX_ROWS) {
            throw new BusinessRuleException(ErrorCode.IMPORT_VALIDATION_ERROR,
                    "Le fichier dépasse la limite de " + MAX_ROWS + " lignes. Découpez-le en plusieurs fichiers.");
        }

        HeaderMapping header = mapHeader(records.get(0));

        List<RowIssue> duplicates = new ArrayList<>();
        List<RowIssue> errors = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenPhones = new HashSet<>();
        int created = 0;
        boolean truncated = false;

        for (int i = 1; i < records.size(); i++) {
            int line = i + 1; // header = line 1
            List<String> cells = records.get(i);
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;

            CreateContactRequest req = header.toRequest(cells, consentGiven, processingBasis);
            String identity = identityOf(req);

            // Bean validation — same constraints as the manual form (incl. phone-or-email).
            Set<ConstraintViolation<CreateContactRequest>> violations = validator.validate(req);
            if (!violations.isEmpty()) {
                truncated |= addIssue(errors, new RowIssue(line, identity, violationSummary(violations)));
                continue;
            }

            // Dedup — inside the file, then against the société's existing contacts.
            String emailKey = req.email();
            String phoneKey = normalizePhone(req.phone());
            if ((emailKey != null && !seenEmails.add(emailKey))
                    || (phoneKey != null && !seenPhones.add(phoneKey))) {
                truncated |= addIssue(duplicates, new RowIssue(line, identity, "Doublon dans le fichier"));
                continue;
            }
            if (emailKey != null && contactRepository.existsBySocieteIdAndEmail(societeId, emailKey)) {
                truncated |= addIssue(duplicates, new RowIssue(line, identity, "E-mail déjà présent dans vos contacts"));
                continue;
            }
            if (phoneKey != null && req.phone() != null
                    && contactRepository.existsBySocieteIdAndPhone(societeId, req.phone())) {
                truncated |= addIssue(duplicates, new RowIssue(line, identity, "Téléphone déjà présent dans vos contacts"));
                continue;
            }

            if (dryRun) {
                created++;
                continue;
            }
            try {
                contactService.create(req);
                created++;
            } catch (ContactEmailAlreadyExistsException e) {
                truncated |= addIssue(duplicates, new RowIssue(line, identity, "E-mail déjà présent dans vos contacts"));
            } catch (BusinessRuleException e) {
                truncated |= addIssue(errors, new RowIssue(line, identity, e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("Contact import: unexpected failure at line {} for societe {}", line, societeId, e);
                truncated |= addIssue(errors, new RowIssue(line, identity, "Erreur inattendue — ligne ignorée"));
            }
        }

        log.info("Contact import societe={} dryRun={} rows={} created={} duplicates={} errors={}",
                societeId, dryRun, records.size() - 1, created, duplicates.size(), errors.size());
        return new ContactImportReport(dryRun, records.size() - 1, created,
                duplicates.size(), errors.size(), truncated,
                header.ignoredColumns, duplicates, errors);
    }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    /** Minimal RFC-4180 parser: quoted fields, "" escapes, ; or , delimiter, BOM strip. */
    static List<List<String>> parseCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);

        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd >= 0 ? text.substring(0, firstLineEnd) : text;
        char delimiter = firstLine.chars().filter(c -> c == ';').count()
                       > firstLine.chars().filter(c -> c == ',').count() ? ';' : ',';

        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else field.append(c);
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                current.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                current.add(field.toString()); field.setLength(0);
                if (!(current.size() == 1 && current.get(0).isBlank())) records.add(current);
                current = new ArrayList<>();
            } else field.append(c);
        }
        current.add(field.toString());
        if (!(current.size() == 1 && current.get(0).isBlank())) records.add(current);
        return records;
    }

    // ── Header mapping ────────────────────────────────────────────────────────

    private static HeaderMapping mapHeader(List<String> headerCells) {
        Map<String, Integer> indexByField = new HashMap<>();
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String raw = headerCells.get(i);
            String key = stripAccents(raw == null ? "" : raw.trim().toLowerCase());
            String field = HEADER_ALIASES.entrySet().stream()
                    .filter(e -> e.getValue().contains(key))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (field != null) indexByField.putIfAbsent(field, i);
            else if (!key.isBlank()) ignored.add(raw.trim());
        }
        if (!indexByField.containsKey("firstName") || !indexByField.containsKey("lastName")) {
            throw new BusinessRuleException(ErrorCode.IMPORT_VALIDATION_ERROR,
                    "Colonnes obligatoires introuvables : le fichier doit contenir « prénom » et « nom » "
                    + "(en-têtes acceptés : prénom/firstName, nom/lastName).");
        }
        return new HeaderMapping(indexByField, ignored);
    }

    private record HeaderMapping(Map<String, Integer> indexByField, List<String> ignoredColumns) {

        CreateContactRequest toRequest(List<String> cells, boolean consentGiven, ProcessingBasis basis) {
            return new CreateContactRequest(
                    cell(cells, "firstName"),
                    cell(cells, "lastName"),
                    cell(cells, "phone"),
                    cell(cells, "email"),
                    cell(cells, "nationalId"),
                    cell(cells, "address"),
                    cell(cells, "notes"),
                    consentGiven,
                    consentGiven ? ConsentMethod.PAPER : null,
                    basis);
        }

        private String cell(List<String> cells, String field) {
            Integer idx = indexByField.get(field);
            if (idx == null || idx >= cells.size()) return null;
            return cells.get(idx);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean addIssue(List<RowIssue> list, RowIssue issue) {
        if (list.size() >= ContactImportReport.MAX_ISSUES) return true;
        list.add(issue);
        return false;
    }

    private static String identityOf(CreateContactRequest req) {
        String name = ((req.firstName() != null ? req.firstName() : "") + " "
                     + (req.lastName() != null ? req.lastName() : "")).trim();
        String contact = req.email() != null ? req.email() : req.phone();
        return contact != null ? name + " (" + contact + ")" : name;
    }

    private static String violationSummary(Set<ConstraintViolation<CreateContactRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString().isEmpty()
                        ? v.getMessage()
                        : v.getPropertyPath() + " : " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(" ; "));
    }

    /** Phone comparison key: digits only, so "06 12-34" and "061234" collide. */
    private static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
