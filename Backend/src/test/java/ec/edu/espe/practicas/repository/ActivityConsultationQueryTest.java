package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.dto.ActivityReviewSummary;
import ec.edu.espe.practicas.dto.TraineePendingCount;
import ec.edu.espe.practicas.model.*;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** Validate the actual JPQL against Hibernate mappings without opening a database connection. */
class ActivityConsultationQueryTest {
    @Test void newQueriesCompileAgainstOracleDialectAndEntityMappings() throws Exception {
        var configuration = new Configuration()
                .addAnnotatedClass(Activity.class).addAnnotatedClass(ActivitySubmission.class)
                .addAnnotatedClass(ActivityReview.class).addAnnotatedClass(User.class)
                .addAnnotatedClass(Assignment.class).addAnnotatedClass(Laboratory.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var reviews = ActivityReviewRepository.class.getMethod("findLatestForActivityIds", List.class)
                    .getAnnotation(Query.class).value();
            session.createSelectionQuery(reviews, ActivityReviewSummary.class).setParameter("activityIds", List.of(11L));
            var firstDate = ActivityRepository.class.getMethod("findFirstActivityDateByTraineeId", Long.class)
                    .getAnnotation(Query.class).value();
            session.createSelectionQuery(firstDate, LocalDate.class).setParameter("traineeId", 3L);
            var pendingCounts = ActivitySubmissionRepository.class.getMethod("findPendingCountsByTutorId", Long.class, ActivityStatus.class)
                    .getAnnotation(Query.class).value();
            session.createSelectionQuery(pendingCounts, TraineePendingCount.class)
                    .setParameter("tutorId", 6L).setParameter("pendingStatus", ActivityStatus.ENVIADA);
        }
    }
}
