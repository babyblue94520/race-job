package pers.clare.racejob.impl;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pers.clare.h2.H2Application;
import pers.clare.racejob.RaceJobScheduler;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.test.ApplicationTest2;
import pers.clare.test.racejob.JobEventBusImpl;
import pers.clare.test.racejob.JobRegister;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("Scheduler Cluster")
@ActiveProfiles("cluster")
@SpringBootTest(classes = ApplicationTest2.class)
@Nested
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(JobEventBusImpl.class)
class RaceJobSchedulerClusterTest {

    private void assertZero(Integer count) {
        assertEquals(0, count, () -> String.format("count: %d", count));
    }

    private void assertGreaterZero(Integer count) {
        assertTrue(count > 0, () -> String.format("count: %d", count));
    }

    private void assertRange(Integer min, Integer max, Integer count) {
        assertTrue(count >= min && count <= max, () -> String.format("count: %d", count));
    }

    {
        H2Application.main(null);
    }


    private final String tag = "job";
    private final String afterTag = "after-" + System.currentTimeMillis();
    private final String afterTag2 = "after2-" + System.currentTimeMillis();
    private final Map<String, Object> map = Map.of("test", "test");

    private final RaceJob job = RaceJob.builder()
            .group(tag)
            .name(tag)
            .cron("* * * * * ?")
            .timezone("+00:00")
            .data(map)
            .build();
    private final RaceJob afterJob = RaceJob.builder()
            .group(afterTag)
            .name(afterTag)
            .afterGroup(tag)
            .afterName(tag)
            .timezone("+00:00")
            .data(map)
            .build();
    private final RaceJob afterJob2 = RaceJob.builder()
            .group(afterTag2)
            .name(afterTag2)
            .afterGroup(afterTag)
            .afterName(afterTag)
            .timezone("+00:00")
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
            Thread.sleep(2000);
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
        for (int i = 0; i < 3; i++) {
            ApplicationTest2.main(new String[]{"--spring.profiles.active=cluster", "--server.port=0"});
        }
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
        sleep();
    }

    private void reset() {
        JobRegister.reset();
    }

    private Integer getSumCount(RaceJob raceJob) {
        return JobRegister.getCount(raceJob);
    }

    @Test
    @Order(2)
    void count() throws InterruptedException {
        int target = 10;
        Thread.sleep(target * 1000);
        int min = target - 2;
        int max = target + 2;
        assertRange(min, max, getSumCount(job));
        assertRange(min, max, getSumCount(afterJob));
        assertRange(min, max, getSumCount(afterJob2));
    }

    @Test
    @Order(3)
    void disable() {
        doDisable();
        doEnable();
    }

    void doDisable() {
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
        doEnable();
        doDisable();
        doEnable();
    }

    void doEnable() {
        jobScheduler.enable(job);
        reset();
        sleep();
        assertGreaterZero(getSumCount(job));
        assertGreaterZero(getSumCount(afterJob));
        assertGreaterZero(getSumCount(afterJob2));
    }

    @Test
    @Order(8)
    void remove() {
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
    void execute() {
        jobScheduler.disable(job);
        delay();
        reset();
        jobScheduler.execute(job);
        sleep();
        assertGreaterZero(getSumCount(job));
        assertGreaterZero(getSumCount(afterJob));
        assertGreaterZero(getSumCount(afterJob2));
    }

}
