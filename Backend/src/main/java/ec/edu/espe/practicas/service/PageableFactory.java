package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableFactory {
    public static final int DEFAULT_SIZE = 15;
    public static final int MAX_SIZE = 100;

    private PageableFactory() { }

    public static Pageable create(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessRuleViolationException(
                    "La página debe ser cero o mayor y el tamaño debe estar entre 1 y 100.");
        }
        return PageRequest.of(page, size, sort);
    }
}
