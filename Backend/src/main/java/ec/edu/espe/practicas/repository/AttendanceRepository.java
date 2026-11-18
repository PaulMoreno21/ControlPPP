package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.Attendance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findFirstByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
            Long traineeId, LocalDate from, LocalDate to);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attendance a where a.trainee.id = :traineeId "
            + "and a.date >= :from and a.date < :to")
    Optional<Attendance> findLockedByTraineeIdAndDateRange(
            @Param("traineeId") Long traineeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attendance a where a.checkOut is null and a.checkIn <= :cutoff")
    List<Attendance> findOpenAttendanceAtOrBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
    List<Attendance> findByTraineeIdAndDateBetweenOrderByDateAsc(
            Long traineeId, LocalDate from, LocalDate to);
    List<Attendance> findByTraineeIdOrderByDateDesc(Long traineeId);
    Page<Attendance> findByTraineeId(Long traineeId, Pageable pageable);
    @EntityGraph(attributePaths = {"trainee"})
    List<Attendance> findByTraineeIdInAndDateBetweenOrderByDateAsc(List<Long> traineeIds, LocalDate from, LocalDate to,
            org.springframework.data.domain.Limit limit);
}
