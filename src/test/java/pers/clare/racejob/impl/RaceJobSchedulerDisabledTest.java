package pers.clare.racejob.impl;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pers.clare.h2.H2Application;
import pers.clare.racejob.RaceJobScheduler;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.test.ApplicationTest2;
import pers.clare.test.racejob.JobRegister;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("RaceJobSchedulerDisabledTest")
@ActiveProfiles("disabled")
@SpringBootTest(classes = ApplicationTest2.class)
@TestInstance(PER_CLASS)
class RaceJobSchedulerDisabledTest {

    private final String tag = String.valueOf(System.currentTimeMillis());
    private final Map<String, Object> data = Collections.singletonMap("test", "test");

    private final RaceJob job = RaceJob.builder()
            .group(tag).name(tag + "-1").key(tag + "-1")
            .cron("*/1 * * * * ?") // 1s interval
            .data(data).build();

    private final RaceJob afterJob = RaceJob.builder()
            .group(tag).name(tag + "-2").key(tag + "-2")
            .dependsKey(job.getKey())
            .data(data).build();

    @Autowired
    private RaceJobScheduler jobScheduler;

    @Autowired
    private JobRegister jobRegister;

    @BeforeAll
    void setup() {
        // Ensure H2 Server is running if the profile requires it
        H2Application.main(null);
        
        jobScheduler.add(job);
        jobScheduler.add(afterJob);
        jobRegister.registerHandlers();
    }

    @BeforeEach
    void reset() {
        JobRegister.reset();
    }

    @Test
    @DisplayName("Verify no scheduled execution occurs when scheduler is disabled")
    void testNoScheduledExecution() throws InterruptedException {
        // Job is enabled by default in RaceJob.builder()
        // Wait for scheduled execution (cron is 1s)
        log.info("Waiting for scheduled execution (should not occur)...");
        Thread.sleep(2500); // Wait > 2s to allow for cron and potential delays
        
        assertEquals(0, JobRegister.getCount(job), "Job should not have executed via scheduler");
        assertEquals(0, JobRegister.getCount(afterJob), "Dependent job should not have executed");
    }

    @Test
    @DisplayName("Verify no manual execution occurs when scheduler is disabled")
    void testNoManualExecution() throws InterruptedException {
        log.info("Attempting manual execution (should be blocked)...");
        jobScheduler.execute(job.toKey());
        
        Thread.sleep(1000); // Brief wait to ensure no background execution started
        
        assertEquals(0, JobRegister.getCount(job), "Job should not have executed manually");
    }

    @Test
    @DisplayName("Verify operations (disable/remove) don't trigger execution or cause errors")
    void testOperationsWhenDisabled() throws InterruptedException {
        // These operations should succeed but not trigger any execution
        jobScheduler.disable(job.toKey());
        jobScheduler.enable(job.toKey());
        jobScheduler.remove(job.toKey());
        
        Thread.sleep(1000);
        
        assertEquals(0, JobRegister.getCount(job), "Operations should not trigger execution");
    }
}
