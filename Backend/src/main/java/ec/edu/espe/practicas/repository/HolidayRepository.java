package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    Optional<Holiday> findByDate(LocalDate date);
    boolean existsByDateAndActive(LocalDate date, Integer active);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "registeredBy")
    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "registeredBy")
    Page<Holiday> findByDateBetween(LocalDate from, LocalDate to, Pageable pageable);
}
