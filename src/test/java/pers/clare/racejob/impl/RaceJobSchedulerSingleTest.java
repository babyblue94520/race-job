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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final Map<String, Object> map = Map.of("test", "test");

    private final RaceJob job = RaceJob.builder()
            .group(tag)
            .name(tag)
            .cron("*/1 * * * * ?")
            .timezone("+00:00")
            .data(map)
            .build();
    private final RaceJob afterJob = RaceJob.builder()
            .group(afterTag)
            .name(afterTag)
            .afterGroup(tag)
            .afterName(tag)
            .data(map)
            .build();
    private final RaceJob afterJob2 = RaceJob.builder()
            .group(afterTag2)
            .name(afterTag2)
            .afterGroup(afterTag)
            .afterName(afterTag)
            .data(map)
            .build();

    @Autowired
    private RaceJobScheduler jobScheduler;

    @Autowired
    private JobRegister jobRegister;

    private void delay() throws InterruptedException {
        Thread.sleep(1000);
    }

    private void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @BeforeAll
    void beforeAll(){
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
        jobScheduler.remove(job);
        jobScheduler.remove(afterJob);
        jobScheduler.remove(afterJob2);
    }

    private void reset() {
        JobRegister.reset();
    }

    private Integer getSumCount(RaceJob raceJob) {
        return JobRegister.getCount(raceJob);
    }

    @Test
    @Order(3)
    void disable() throws InterruptedException {
        jobScheduler.disable(job);
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
        jobScheduler.enable(job);
        reset();
        sleep();
        assertGreaterZero(getSumCount(job));
        assertGreaterZero(getSumCount(afterJob));
        assertGreaterZero(getSumCount(afterJob2));
    }

    @Test
    @Order(8)
    void remove() throws InterruptedException {
        jobScheduler.remove(job);
        delay();
        reset();
        sleep();
        assertZero(getSumCount(job));
        assertZero(getSumCount(afterJob));
        assertZero(getSumCount(afterJob2));
    }


    @Test
    @Order(10)
    void execute() throws InterruptedException {
        jobScheduler.disable(job);
        delay();
        reset();
        jobScheduler.execute(job);
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
        jobScheduler.registerHandler(job, (inner) -> {
            if (count.incrementAndGet() == target) {
                throw new RuntimeException();
            }
        });

        jobScheduler.registerHandler(afterJob, (inner) -> {
            count2.incrementAndGet();
        });

        jobScheduler.registerHandler(afterJob2, (inner) -> {
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
    void updateJob() throws InterruptedException {
        RaceJob testJob = RaceJob.builder()
                .group("test")
                .name("test")
                .cron("*/3 * * * * ?")
                .timezone("+00:00")
                .build();
        AtomicInteger count = new AtomicInteger();
        jobScheduler.registerHandler(testJob, (inner) -> {
            count.incrementAndGet();
        });
        jobScheduler.add(testJob);
        int target = 5;

        updateTest(target, count, () -> {
            jobScheduler.disable(testJob);
        }, () -> {
            jobScheduler.enable(testJob);
        });

        updateTest(target, count, () -> {
            jobScheduler.remove(testJob);
        }, () -> {
            jobScheduler.add(testJob);
        });
    }

    void updateTest(int target, AtomicInteger count, Runnable before, Runnable after) throws InterruptedException {
        int next = 0;
        while (next < target) {
            int c = count.get();
            if (c > next) {
                next = c;
                before.run();
                Thread.sleep(1000);
                after.run();
            } else {
                Thread.sleep(500);
            }
        }
        assertEquals(target, next);
    }


    @Test
    @Order(14)
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
                    jobScheduler.disable(job);
                    job = getJob(random.nextInt(range));
                    jobScheduler.enable(job);
                    job = getJob(random.nextInt(range));
                    jobScheduler.remove(job);
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
                .cron("* * * * * ?")
                .timezone("+00:00")
                .build();
    }
}
