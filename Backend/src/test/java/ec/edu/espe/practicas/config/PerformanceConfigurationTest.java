package ec.edu.espe.practicas.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PerformanceConfigurationTest {
    @Test
    void invalidationWaitsForCommit() {
        var cache = new PerformanceConfiguration().cacheManager().getCache("holidayRanges");
        assertNotNull(cache);
        cache.put("range", "previous");
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.clear();
            assertNotNull(cache.get("range"));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertNull(cache.get("range"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackKeepsPreviousCache() {
        var cache = new PerformanceConfiguration().cacheManager().getCache("holidayRanges");
        assertNotNull(cache);
        cache.put("range", "previous");
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.clear();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertNotNull(cache.get("range"));
    }
}
