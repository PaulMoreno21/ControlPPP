package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Optional<AuditLog> findFirstByActionAndEntityIdOrderByIdDesc(String action, Long entityId);
    List<AuditLog> findByUserIdOrderByOccurredAtDesc(Long userId);
    List<AuditLog> findByEntityNameAndEntityIdOrderByOccurredAtDesc(String entityName, Long entityId);
    @EntityGraph(attributePaths = "user")
    @Query(value = "select a from AuditLog a where a.occurredAt >= :from and a.occurredAt < :to "
            + "and a.action <> 'IMPORTAR_FERIADOS'",
            countQuery = "select count(a) from AuditLog a where a.occurredAt >= :from and a.occurredAt < :to "
                    + "and a.action <> 'IMPORTAR_FERIADOS'")
    Page<AuditLog> findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
