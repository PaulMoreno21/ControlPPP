package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.ActivitySubmission;
import ec.edu.espe.practicas.model.ActivityStatus;
import ec.edu.espe.practicas.dto.TraineePendingCount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivitySubmissionRepository extends JpaRepository<ActivitySubmission, Long> {
    @Query("""
            select new ec.edu.espe.practicas.dto.TraineePendingCount(
                s.activity.trainee.id, count(s.id))
            from ActivitySubmission s
            where s.tutor.id = :tutorId
              and s.activity.status = :pendingStatus
              and s.submissionNumber = (
                  select max(latest.submissionNumber) from ActivitySubmission latest
                  where latest.activity = s.activity
              )
              and not exists (select r.id from ActivityReview r where r.submission = s)
              and exists (
                  select a.id from Assignment a
                  where a.trainee = s.activity.trainee and a.tutor.id = :tutorId and a.current = 1
              )
            group by s.activity.trainee.id
            """)
    List<TraineePendingCount> findPendingCountsByTutorId(
            @Param("tutorId") Long tutorId, @Param("pendingStatus") ActivityStatus pendingStatus);

    List<ActivitySubmission> findByActivityIdOrderBySubmissionNumberDesc(Long activityId);
    @EntityGraph(attributePaths = {"activity", "activity.trainee"})
    Optional<ActivitySubmission> findFirstByActivityIdOrderBySubmissionNumberDesc(Long activityId);
    List<ActivitySubmission> findByTutorIdOrderBySubmittedAtAsc(Long tutorId);

    @EntityGraph(attributePaths = {"activity", "activity.trainee"})
    @Query("""
            select submission from ActivitySubmission submission
            where submission.tutor.id = :tutorId
              and not exists (
                  select review.id from ActivityReview review
                  where review.submission = submission
              )
            order by submission.submittedAt asc
            """)
    List<ActivitySubmission> findPendingByTutorId(@Param("tutorId") Long tutorId);

    @EntityGraph(attributePaths = {"activity", "activity.trainee"})
    @Query("select s from ActivitySubmission s where s.tutor.id = :tutorId "
            + "and not exists (select r.id from ActivityReview r where r.submission = s) "
            + "order by s.submittedAt asc")
    Page<ActivitySubmission> findPendingByTutorId(
            @Param("tutorId") Long tutorId, Pageable pageable);
}
