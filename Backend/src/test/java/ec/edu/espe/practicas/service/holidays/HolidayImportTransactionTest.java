package ec.edu.espe.practicas.service.holidays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import ec.edu.espe.practicas.config.PerformanceConfiguration;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

/** Real Spring transaction/cache advice; repositories and the transaction resource are simulated. */
class HolidayImportTransactionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @EnableTransactionManagement
    static class Advice { }

    static class Transactions extends AbstractPlatformTransactionManager {
        boolean committed;
        boolean rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }

    private AnnotationConfigApplicationContext context;
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private Transactions transactions;
    private Cache cache;
    private HolidayImportService importer;

    @BeforeEach void setup() {
        var users = mock(UserRepository.class);
        var roles = mock(UserRoleRepository.class);
        var user = mock(User.class);
        when(user.getActive()).thenReturn(1);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        var role = mock(Role.class); when(role.getName()).thenReturn(RoleName.ADMIN_GENERAL);
        when(roles.findByUserId(1L)).thenReturn(List.of(UserRole.assign(user, role, null)));
        when(holidays.save(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.getArgument(0);
        });
        context = new AnnotationConfigApplicationContext();
        context.register(Advice.class, PerformanceConfiguration.class, HolidayImportService.class);
        context.registerBean(HolidayRepository.class, () -> holidays);
        context.registerBean(AuditLogRepository.class, () -> audits);
        context.registerBean(UserRepository.class, () -> users);
        context.registerBean(UserRoleRepository.class, () -> roles);
        context.registerBean(Clock.class, () -> Clock.systemUTC());
        context.registerBean(Transactions.class, Transactions::new);
        context.refresh();
        importer = context.getBean(HolidayImportService.class);
        transactions = context.getBean(Transactions.class);
        cache = context.getBean(CacheManager.class).getCache("holidayRanges");
        cache.put("before", "cached list");
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test void commitsAuditAndInvalidatesTheListAfterSuccess() {
        importer.importYear(2026, List.of(new NationalHoliday(LocalDate.of(2026, 1, 1), "Año Nuevo")), 1L);
        assertTrue(transactions.committed); assertFalse(transactions.rolledBack);
        assertNull(cache.get("before"));
        verify(audits).save(any());
    }

    @Test void failedHolidayWriteRollsBackWithoutRecordingCompletion() {
        doThrow(new IllegalStateException("simulated write failure")).when(holidays).save(any());
        assertThrows(IllegalStateException.class, () -> importer.importYear(2026,
                List.of(new NationalHoliday(LocalDate.of(2026, 1, 1), "Año Nuevo")), 1L));
        assertTrue(transactions.rolledBack); assertFalse(transactions.committed);
        assertEquals("cached list", cache.get("before").get());
        verify(audits, never()).save(any());
    }

    @Test void failedCompletionMarkerAlsoRollsBackAndPreservesCache() {
        when(audits.save(any())).thenAnswer(invocation -> {
            var audit = (AuditLog) invocation.getArgument(0);
            if (HolidayImportService.COMPLETED_ACTION.equals(audit.getAction())) {
                throw new IllegalStateException("simulated marker failure");
            }
            return audit;
        });
        assertThrows(IllegalStateException.class, () -> importer.importYear(2026,
                List.of(new NationalHoliday(LocalDate.of(2026, 1, 1), "Año Nuevo")), 1L));
        assertTrue(transactions.rolledBack); assertFalse(transactions.committed);
        assertEquals("cached list", cache.get("before").get());
    }
}
