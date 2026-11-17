package com.snackcounter.scheduler;

import com.snackcounter.service.RefillService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The clock-driven half of the lifecycle: hand off missed deadlines, retry and follow up HR claims. */
@Component
public class RefillScheduler {

    private final RefillService refills;

    public RefillScheduler(RefillService refills) {
        this.refills = refills;
    }

    @Scheduled(fixedDelayString = "${snack.escalation-interval-ms:60000}")
    public void tick() {
        refills.runScheduledChecks();
    }
}
