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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("SchedulerDisabledTest")
@ActiveProfiles("disabled")
@SpringBootTest(classes = ApplicationTest2.class)
@Nested
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RaceJobSchedulerDisabledTest {

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
        assertZero(getSumCount(job));
        assertZero(getSumCount(afterJob));
        assertZero(getSumCount(afterJob2));
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
        assertZero(getSumCount(job));
        assertZero(getSumCount(afterJob));
        assertZero(getSumCount(afterJob2));
    }


}
