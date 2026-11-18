package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.User;
import java.time.LocalDateTime;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/** Validate both the list and its matching count against the real Hibernate mappings. */
class AuditListingQueryTest {
    @Test void auditListAndCountCompileAgainstOracleDialect() throws Exception {
        var query = AuditLogRepository.class.getMethod("findByOccurredAtGreaterThanEqualAndOccurredAtLessThan",
                LocalDateTime.class, LocalDateTime.class, Pageable.class).getAnnotation(Query.class);
        var configuration = new Configuration().addAnnotatedClass(AuditLog.class).addAnnotatedClass(User.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var from = LocalDateTime.of(2026, 11, 1, 0, 0);
            var to = from.plusMonths(1);
            session.createSelectionQuery(query.value(), AuditLog.class).setParameter("from", from).setParameter("to", to);
            session.createSelectionQuery(query.countQuery(), Long.class).setParameter("from", from).setParameter("to", to);
        }
    }
}
