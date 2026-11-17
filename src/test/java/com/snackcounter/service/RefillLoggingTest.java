package com.snackcounter.service;

import static com.snackcounter.model.Office.OSLO;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.snackcounter.model.Refill;
import com.snackcounter.support.SnackFixture;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** The log tells each refill's story at INFO, flags trouble at WARN, and never contains receipt images. */
@ExtendWith(OutputCaptureExtension.class)
class RefillLoggingTest {

    private static final byte[] RECEIPT_IMAGE = {1, 2, 3}; // "AQID" in base64

    private final SnackFixture f = new SnackFixture();
    private final ch.qos.logback.classic.Logger appLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.snackcounter");
    private Level levelBefore;

    @BeforeEach
    void showInfo() {
        levelBefore = appLogger.getLevel();
        appLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void restoreLevel() {
        appLogger.setLevel(levelBefore);
    }

    @Test
    void oneRefillsStoryCanBeFollowedInTheLog(CapturedOutput output) {
        f.checkIn(OSLO, "E100", "E101");

        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E100");
        f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO), null, List.of("chips"), RECEIPT_IMAGE);
        f.hr.approve(refill.claimId());
        f.service.runScheduledChecks();

        assertThat(output.getOut()).containsSubsequence(
                "Refill #1 OSLO: reported empty by E102",
                "Refill #1 OSLO: offered to E100",
                "Refill #1 OSLO: committed by E100",
                "[chat -> #snacks-oslo] Ingrid is refilling the snack box",
                "Refill #1 OSLO: receipt for 450.00 NOK from E100, within the limit",
                "[HR] received claim HR-1 for E100 (450.00 NOK), pending",
                "Refill #1 OSLO: HR claim HR-1 filed",
                "[HR] claim HR-1 APPROVED",
                "Refill #1 OSLO: reimbursed, HR approved claim HR-1");
        assertThat(output.getOut()).as("receipt images never reach the log")
                .doesNotContain("AQID").doesNotContain("[B@");
    }

    @Test
    void troubleIsAWarningAndTheRetryIsVisible(CapturedOutput output) {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E100");
        f.hr.setAvailable(false);

        f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO), null, List.of(), RECEIPT_IMAGE);
        f.hr.setAvailable(true);
        f.service.runScheduledChecks();

        assertThat(output.getOut()).containsSubsequence(
                "WARN", "Refill #1 OSLO: HR unavailable, will retry on the next scheduled check",
                "INFO", "Refill #1 OSLO: HR claim HR-1 filed");
    }

    @Test
    void nobodyAtTheOfficeIsAWarning(CapturedOutput output) {
        f.service.reportEmpty(OSLO, "E102"); // nobody checked in

        assertThat(output.getOut()).containsSubsequence(
                "WARN", "Refill #1 OSLO: no volunteer available at the office, open call in #snacks-oslo");
    }
}
