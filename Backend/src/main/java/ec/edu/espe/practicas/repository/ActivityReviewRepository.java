package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.ActivityReview;
import ec.edu.espe.practicas.model.ReviewDecision;
import ec.edu.espe.practicas.dto.ActivityReviewSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityReviewRepository extends JpaRepository<ActivityReview, Long> {
    @Query("""
            select new ec.edu.espe.practicas.dto.ActivityReviewSummary(
                s.activity.id, r.observation, r.reviewedAt)
            from ActivityReview r join r.submission s
            where s.activity.id in :activityIds
              and s.submissionNumber = (
                  select max(latest.submissionNumber) from ActivitySubmission latest
                  where latest.activity = s.activity
              )
            """)
    List<ActivityReviewSummary> findLatestForActivityIds(@Param("activityIds") List<Long> activityIds);

    Optional<ActivityReview> findBySubmissionId(Long submissionId);
    List<ActivityReview> findByTutorIdAndDecisionOrderByReviewedAtDesc(
            Long tutorId, ReviewDecision decision);
}
