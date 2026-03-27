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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("SchedulerSingleTest")
@ActiveProfiles("single")
@SpringBootTest(classes = ApplicationTest2.class)
@Nested
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RaceJobSchedulerSingleTest {

    private void assertZero(Integer count) {
        assertEquals(0, count, () -> String.format("count: %d", count));
    }

    private void assertGreaterZero(Integer count) {
        assertTrue(count > 0, () -> String.format("count: %d", count));
    }

    {
        H2Application.main(null);
    }

    private final String tag = String.valueOf(System.currentTimeMillis());
    private final String afterTag = "after-" + System.currentTimeMillis();
    private final String afterTag2 = "after2-" + System.currentTimeMillis();
    private final Map<String, Object> map = java.util.Collections.singletonMap("test", "test");

    private final RaceJob job = RaceJob.builder()
            .group(tag)
            .name(tag)
            .key(tag)
            .cron("*/1 * * * * ?")
            .timezone("+00:00")
            .data(map)
            .build();
    private final RaceJob afterJob = RaceJob.builder()
            .group(afterTag)
            .name(afterTag)
            .key(afterTag)
            .dependsKey(tag)
            .data(map)
            .build();
    private final RaceJob afterJob2 = RaceJob.builder()
            .group(afterTag2)
            .name(afterTag2)
            .key(afterTag2)
            .dependsKey(afterTag)
            .data(map)
            .build();

    @Autowired
    private RaceJobScheduler jobScheduler;

    @Autowired
    private JobRegister jobRegister;

    private void delay() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @BeforeAll
    void beforeAll() {
        jobScheduler.add(job);
        jobScheduler.add(afterJob);
        jobScheduler.add(afterJob2);
        jobRegister.registerHandlers();
    }

    @BeforeEach
    void before() {
        jobScheduler.add(job);
        jobScheduler.add(afterJob);
        jobScheduler.add(afterJob2);
        reset();
    }

    @AfterEach
    void after() {
        jobScheduler.remove(job.toKey());
        jobScheduler.remove(afterJob.toKey());
        jobScheduler.remove(afterJob2.toKey());
    }

    private void reset() {
        JobRegister.reset();
    }

    private Integer getSumCount(RaceJob raceJob) {
        return JobRegister.getCount(raceJob);
    }

    @Test
    @Order(3)
    void disable() {
        jobScheduler.disable(job.toKey());
        delay();
        reset();
        sleep();
        assertZero(getSumCount(job));
        assertZero(getSumCount(afterJob));
        assertZero(getSumCount(afterJob2));
    }

    @Test
    @Order(4)
    void enable() {
        jobScheduler.enable(job.toKey());
        reset();
        sleep();
        assertGreaterZero(getSumCount(job));
        assertGreaterZero(getSumCount(afterJob));
        assertGreaterZero(getSumCount(afterJob2));
    }

    @Test
    @Order(8)
    void remove() {
        jobScheduler.remove(job.toKey());
        delay();
        reset();
        sleep();
        assertZero(getSumCount(job));
        assertZero(getSumCount(afterJob));
        assertZero(getSumCount(afterJob2));
    }

    @Test
    @Order(10)
    void execute() {
        jobScheduler.disable(job.toKey());
        delay();
        reset();
        jobScheduler.execute(job.toKey());
        sleep();
        assertGreaterZero(getSumCount(job));
        assertGreaterZero(getSumCount(afterJob));
        assertGreaterZero(getSumCount(afterJob2));
    }

    @Test
    @Order(13)
    void abortOnError() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();
        AtomicInteger count3 = new AtomicInteger();
        int target = 5;
        int target2 = 3;
        jobScheduler.registerHandler(job.getKey(), (inner) -> {
            if (count.incrementAndGet() == target) {
                throw new RuntimeException();
            }
        });

        jobScheduler.registerHandler(afterJob.getKey(), (inner) -> {
            count2.incrementAndGet();
        });

        jobScheduler.registerHandler(afterJob2.getKey(), (inner) -> {
            if (count3.incrementAndGet() == target2) {
                throw new RuntimeException();
            }
        });
        Thread.sleep(10000);
        assertEquals(target, count.get());
        assertEquals(target - 1, count2.get());
        assertEquals(target2, count3.get());
    }

    @Test
    @Order(14)
    void update() {
        RaceJob testJob = RaceJob.builder()
                .group("test")
                .name(String.valueOf(System.currentTimeMillis()))
                .key("test")
                .cron("*/3 * * * * ?")
                .timezone("+00:00")
                .version(1)
                .enabled(true)
                .build();
        jobScheduler.add(testJob);

        testJob.setDescription("new desc");
        testJob.setKey("new key");
        testJob.setTimezone("+08:00");
        testJob.setCron("0 0 * * * ?");
        testJob.setDependsKey("parent");
        testJob.setData(Map.of("key", "value"));
        testJob.setEnabled(false);
        jobScheduler.add(testJob);

        RaceJob notUpdated = jobScheduler.find(testJob.toKey());
        assertNotEquals(testJob.getDescription(), notUpdated.getDescription());
        assertNotEquals(testJob.getKey(), notUpdated.getKey());
        assertNotEquals(testJob.getTimezone(), notUpdated.getTimezone());
        assertNotEquals(testJob.getCron(), notUpdated.getCron());
        assertNotEquals(testJob.getDependsKey(), notUpdated.getDependsKey());
        assertNotEquals(testJob.getData(), notUpdated.getData());
        assertNotEquals(testJob.getEnabled(), notUpdated.getEnabled());

        testJob.setVersion(2);
        jobScheduler.add(testJob);

        RaceJob updated = jobScheduler.find(testJob.toKey());
        assertEquals(testJob.getDescription(), updated.getDescription());
        assertEquals(testJob.getKey(), updated.getKey());
        assertEquals(testJob.getTimezone(), updated.getTimezone());
        assertEquals(testJob.getCron(), updated.getCron());
        assertEquals(testJob.getDependsKey(), updated.getDependsKey());
        assertEquals(testJob.getData(), updated.getData());
        assertNotEquals(testJob.getEnabled(), updated.getEnabled());
    }

    @Test
    @Order(15)
    @DisplayName("Verify job can be repeatedly enabled and disabled")
    void enableAndDisable() throws InterruptedException {
        RaceJob testJob = RaceJob.builder()
                .group("test")
                .name("enable-disable-" + System.currentTimeMillis())
                .key("test")
                .cron("*/1 * * * * ?") // 1s interval
                .timezone("+00:00")
                .enabled(false)
                .build();
        AtomicInteger count = new AtomicInteger();
        jobScheduler.registerHandler(testJob.getKey(), (inner) -> {
            count.incrementAndGet();
        });
        jobScheduler.add(testJob);

        log.info("Starting repeated enable/disable cycles...");
        for (int i = 0; i < 3; i++) {
            jobScheduler.enable(testJob.toKey());
            log.debug("Cycle {}: Enabled, waiting for execution...", i);
            Thread.sleep(2500); // Allow ~2 executions
            int countAfterEnable = count.get();
            assertTrue(countAfterEnable > 0, "Job should have executed when enabled in cycle " + i);

            jobScheduler.disable(testJob.toKey());
            log.debug("Cycle {}: Disabled, verifying no further execution...", i);
            Thread.sleep(2000);
            assertEquals(countAfterEnable, count.get(), "Job should not execute when disabled in cycle " + i);
        }

        log.info("Verifying normal operation after repeated cycles...");
        jobScheduler.enable(testJob.toKey());
        int countBeforeFinal = count.get();
        Thread.sleep(2500);
        assertTrue(count.get() > countBeforeFinal, "Job should still be executing normally after multiple toggles");

        jobScheduler.disable(testJob.toKey());
    }

    @Test
    @Order(16)
    @DisplayName("Verify job can be repeatedly added and removed")
    void addAndRemove() throws InterruptedException {
        RaceJob testJob = RaceJob.builder()
                .group("test")
                .name("add-remove-" + System.currentTimeMillis())
                .key("test")
                .cron("*/1 * * * * ?")
                .timezone("+00:00")
                .build();
        AtomicInteger count = new AtomicInteger();
        jobScheduler.registerHandler(testJob.getKey(), (inner) -> {
            count.incrementAndGet();
        });

        log.info("Starting repeated add/remove cycles...");
        for (int i = 0; i < 3; i++) {
            jobScheduler.add(testJob);
            log.debug("Cycle {}: Added, waiting for execution...", i);
            Thread.sleep(2500);
            int countAfterAdd = count.get();
            assertTrue(countAfterAdd > 0, "Job should have executed after being added in cycle " + i);

            jobScheduler.remove(testJob.toKey());
            log.debug("Cycle {}: Removed, verifying no further execution...", i);
            Thread.sleep(2000);
            assertEquals(countAfterAdd, count.get(), "Job should not execute after being removed in cycle " + i);
        }
    }


    @Test
    @Order(17)
    void test() throws InterruptedException, ExecutionException {
        int thread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(thread);

        int time = 30000;
        long endTime = System.currentTimeMillis() + time;
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < thread; i++) {
            futures.add(executor.submit(() -> {
                Random random = new Random();
                int range = 100;
                while (System.currentTimeMillis() < endTime) {
                    var job = getJob(random.nextInt(range));
                    jobScheduler.add(job);
                    job = getJob(random.nextInt(range));
                    jobScheduler.disable(job.toKey());
                    job = getJob(random.nextInt(range));
                    jobScheduler.enable(job.toKey());
                    job = getJob(random.nextInt(range));
                    jobScheduler.remove(job.toKey());
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
    }

    RaceJob getJob(int n) {
        return RaceJob.builder()
                .group("job")
                .name(n + "")
                .key(n + "")
                .cron("* * * * * ?")
                .timezone("+00:00")
                .build();
    }
}
