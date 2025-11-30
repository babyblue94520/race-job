package pers.clare.racejob;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = RaceJobProperties.PREFIX)
public class RaceJobProperties {
    public static final String PREFIX = "race-job";

    private String instance = "raceJobScheduler";

    private Integer threadCount = 1;

    /**
     * Setting it to false will cause the scheduler to not execute any tasks. default true.
     */
    private Boolean executionEnabled = true;

    /**
     * Reload all job intervals.
     */
    private Duration reloadInterval = Duration.parse("PT60S");

    /**
     * The time is to check that the job is actually being executed.
     */
    private Long checkWaitTime = 1000L;

    /**
     * The running job periodically updates its last active timestamp.
     */
    private Duration updateActiveInterval = Duration.parse("PT60S");

    /**
     *  If true, aborts the task on exception; if false, exceptions are caught and execution continues.
     */
    private Boolean abortOnError = true;
}
