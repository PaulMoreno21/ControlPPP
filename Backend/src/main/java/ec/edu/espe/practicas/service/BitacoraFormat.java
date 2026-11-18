package ec.edu.espe.practicas.service;

import java.util.Locale;
import org.springframework.http.MediaType;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;

public enum BitacoraFormat {
    CSV("csv", "text/csv; charset=UTF-8"),
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String extension;
    private final String contentType;

    BitacoraFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() { return extension; }
    public MediaType mediaType() { return MediaType.parseMediaType(contentType); }

    public static BitacoraFormat parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new BusinessRuleViolationException("Formato no soportado. Use CSV, PDF, XLSX o DOCX.");
        }
    }
}
