package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.ActivityStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    @Query("select min(a.date) from Activity a where a.trainee.id = :traineeId")
    java.util.Optional<LocalDate> findFirstActivityDateByTraineeId(@Param("traineeId") Long traineeId);

    List<Activity> findByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByIdAsc(
            Long traineeId, LocalDate fromInclusive, LocalDate toExclusive);
    List<Activity> findByTraineeIdAndStatus(Long traineeId, ActivityStatus status);
    List<Activity> findByTraineeIdAndStatusOrderByDateDescIdDesc(Long traineeId, ActivityStatus status);
    @EntityGraph(attributePaths = {"trainee"})
    List<Activity> findByTraineeIdAndStatusNotOrderByDateAscIdAsc(Long traineeId, ActivityStatus status);
    List<Activity> findByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
            Long traineeId, LocalDate fromInclusive, LocalDate toExclusive);
    List<Activity> findByTraineeIdAndDateGreaterThanEqualAndDateLessThanAndStatusOrderByDateAscIdAsc(
            Long traineeId, LocalDate fromInclusive, LocalDate toExclusive, ActivityStatus status,
            org.springframework.data.domain.Limit limit);
    @Query("select a from Activity a where a.trainee.id = :traineeId "
            + "and ((a.date >= :fromInclusive and a.date < :toExclusive) or a.status = :pendingStatus) "
            + "order by a.date asc, a.id asc")
    Page<Activity> findPageForTraineeWithPending(
            @Param("traineeId") Long traineeId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive,
            @Param("pendingStatus") ActivityStatus pendingStatus,
            Pageable pageable);
    @EntityGraph(attributePaths = {"trainee"})
    Page<Activity> findByTraineeIdAndStatusNot(Long traineeId, ActivityStatus status, Pageable pageable);
    @Query("select coalesce(sum(a.durationHours), 0) from Activity a "
            + "where a.trainee.id = :traineeId and a.status = :status")
    java.math.BigDecimal sumDurationHoursByTraineeIdAndStatus(
            @Param("traineeId") Long traineeId, @Param("status") ActivityStatus status);
    long countByTraineeIdAndStatus(Long traineeId, ActivityStatus status);
    @EntityGraph(attributePaths = {"trainee"})
    List<Activity> findByTraineeIdInAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
            List<Long> traineeIds, LocalDate fromInclusive, LocalDate toExclusive,
            org.springframework.data.domain.Limit limit);
}
