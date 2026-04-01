package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.media.service.MediaTooLargeException;
import com.yem.hlm.backend.media.service.MediaTypeNotAllowedException;
import com.yem.hlm.backend.property.api.dto.ImportResultResponse;
import com.yem.hlm.backend.property.api.dto.ImportResultResponse.RowError;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import org.springframework.beans.factory.annotation.Value;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Parses a CSV file and bulk-creates properties within a single transaction.
 *
 * <p>CSV columns (header row required):
 * {@code referenceCode, projectId, type, title, price, surfaceArea, landArea,
 * bedrooms, bathrooms, floor, building, status}
 *
 * <p>Transaction policy: all-or-nothing — if any row fails validation the entire
 * import is rejected and an error list is returned.
 */
@Service
public class PropertyImportService {

    /** Required CSV column headers. */
    private static final String[] HEADERS = {
            "referenceCode", "projectId", "type", "title", "price",
            "surfaceArea", "landArea", "bedrooms", "bathrooms",
            "floor", "building", "status"
    };
    private static final Set<String> ALLOWED_CSV_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "text/plain"
    );

    private final PropertyService propertyService;
    private final long maxFileSize;

    public PropertyImportService(
            PropertyService propertyService,
            @Value("${app.property-import.max-file-size:2097152}") long maxFileSize) {
        this.propertyService = propertyService;
        this.maxFileSize = maxFileSize;
    }

    /**
     * Parses the given CSV file and imports valid properties.
     *
     * @param file the multipart CSV file
     * @return result with count of imported rows and any row-level errors
     */
    @Transactional
    public ImportResultResponse importCsv(MultipartFile file) throws IOException {
        validateCsvFile(file);
        List<RowError> errors = new ArrayList<>();
        List<PropertyCreateRequest> validRequests = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            var format = CSVFormat.DEFAULT.builder()
                    .setHeader(HEADERS)
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build();

            Iterable<CSVRecord> records = format.parse(reader);
            int rowNum = 1;

            for (CSVRecord record : records) {
                rowNum++;
                try {
                    validRequests.add(parseRow(record, rowNum));
                } catch (ImportRowException e) {
                    errors.add(new RowError(rowNum, e.getMessage()));
                }
            }
        }

        // All-or-nothing: reject entire file if any errors
        if (!errors.isEmpty()) {
            return new ImportResultResponse(0, errors);
        }

        // Import all valid rows
        int imported = 0;
        for (PropertyCreateRequest req : validRequests) {
            propertyService.create(req);
            imported++;
        }

        return new ImportResultResponse(imported, List.of());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void validateCsvFile(MultipartFile file) {
        if (file.getSize() > maxFileSize) {
            throw new MediaTooLargeException(file.getSize(), maxFileSize);
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";

        boolean csvFilename = filename.endsWith(".csv");
        boolean csvContentType = contentType != null && ALLOWED_CSV_CONTENT_TYPES.contains(contentType);
        if (!csvFilename && !csvContentType) {
            throw new MediaTypeNotAllowedException(contentType != null ? contentType : "unknown");
        }
    }

    private PropertyCreateRequest parseRow(CSVRecord record, int rowNum) {
        String referenceCode = requireNonBlank(record, "referenceCode", rowNum);
        String projectIdStr  = requireNonBlank(record, "projectId",    rowNum);
        String typeStr       = requireNonBlank(record, "type",         rowNum);
        String title         = requireNonBlank(record, "title",        rowNum);
        String priceStr      = record.get("price");

        UUID projectId = parseUuid(projectIdStr, "projectId", rowNum);

        PropertyType type;
        try {
            type = PropertyType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ImportRowException(rowNum, "Invalid type '" + typeStr
                    + "'. Valid values: " + List.of(PropertyType.values()));
        }

        BigDecimal price = parseBigDecimal(priceStr, "price", rowNum);

        BigDecimal surfaceArea = parseBigDecimalOpt(record.get("surfaceArea"));
        BigDecimal landArea    = parseBigDecimalOpt(record.get("landArea"));
        Integer    bedrooms    = parseIntOpt(record.get("bedrooms"));
        Integer    bathrooms   = parseIntOpt(record.get("bathrooms"));
        Integer    floor       = parseIntOpt(record.get("floor"));
        String     building    = blankToNull(record.get("building"));
        String     statusStr   = blankToNull(record.get("status"));

        // Validate status if provided
        if (statusStr != null) {
            try {
                PropertyStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ImportRowException(rowNum, "Invalid status '" + statusStr + "'");
            }
        }

        return new PropertyCreateRequest(
                type, title, referenceCode, price, "MAD",
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                surfaceArea, landArea,
                bedrooms, bathrooms, null, null, null, null, null,
                floor, null, null, null, null, null,
                projectId, null, building
        );
    }

    private String requireNonBlank(CSVRecord record, String col, int row) {
        String val = record.get(col);
        if (val == null || val.isBlank()) {
            throw new ImportRowException(row, "Column '" + col + "' is required");
        }
        return val.trim();
    }

    private UUID parseUuid(String val, String col, int row) {
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            throw new ImportRowException(row, "Column '" + col + "' must be a valid UUID");
        }
    }

    private BigDecimal parseBigDecimal(String val, String col, int row) {
        if (val == null || val.isBlank()) {
            throw new ImportRowException(row, "Column '" + col + "' is required");
        }
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            throw new ImportRowException(row, "Column '" + col + "' must be a number");
        }
    }

    private BigDecimal parseBigDecimalOpt(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOpt(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String val) {
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    // =========================================================================
    // Inner exception (scoped to import, caught per-row)
    // =========================================================================

    static class ImportRowException extends RuntimeException {
        ImportRowException(int row, String message) {
            super("Row " + row + ": " + message);
        }
    }
}
