package com.settl.backend.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseScheduler.class);

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseScheduler(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    /**
     * Runs periodically to process due recurring expenses.
     * Default: every hour (3600000 ms) with initial delay of 30 seconds.
     */
    @Scheduled(fixedRateString = "${app.recurring.check-rate-ms:3600000}", initialDelay = 30000)
    public void runRecurringExpenseJob() {
        log.debug("Executing scheduled recurring expenses job...");
        try {
            int processed = recurringExpenseService.processDueRecurringExpenses();
            if (processed > 0) {
                log.info("Processed and generated {} due recurring expenses", processed);
            }
        } catch (Exception e) {
            log.error("Error occurred while executing scheduled recurring expenses job", e);
        }
    }
}
