package ec.edu.espe.practicas.service.holidays;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class HolidaySynchronizationScheduler {
    private final TaskExecutor executor;
    private final HolidaySynchronizationService synchronization;

    public HolidaySynchronizationScheduler(@Qualifier("holidaySyncExecutor") TaskExecutor executor,
            HolidaySynchronizationService synchronization) {
        this.executor = executor;
        this.synchronization = synchronization;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { schedule(); }

    @Scheduled(cron = "${app.holidays.sync.cron:0 15 3 1 1 *}", zone = "America/Guayaquil")
    public void schedule() {
        executor.execute(synchronization::synchronize);
    }
}
