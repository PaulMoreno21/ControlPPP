package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.HolidayResponse;
import ec.edu.espe.practicas.repository.HolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only presentation data: business rules must always read Oracle directly. */
@Service
public class HolidayQueryCache {
    private final HolidayRepository repository;

    public HolidayQueryCache(HolidayRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "holidayRanges", key = "{#from, #to}", unless = "#result.size() > 366")
    public List<HolidayResponse> list(LocalDate from, LocalDate to) {
        return repository.findByDateBetweenOrderByDateAsc(from, to).stream()
                .map(HolidayResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "holidayRanges", key = "{#from, #to, #page, #size}")
    public ec.edu.espe.practicas.dto.PagedResponse<HolidayResponse> page(
            LocalDate from, LocalDate to, int page, int size) {
        return ec.edu.espe.practicas.dto.PagedResponse.from(repository.findByDateBetween(from, to,
                PageableFactory.create(page, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("date"),
                        org.springframework.data.domain.Sort.Order.asc("id")))), HolidayResponse::from);
    }
}
