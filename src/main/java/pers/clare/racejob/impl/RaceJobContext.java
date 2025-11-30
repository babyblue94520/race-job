package pers.clare.racejob.impl;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import pers.clare.racejob.vo.RaceJob;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

@Log4j2
@Getter
class RaceJobContext {
    private RaceJob job;
    private ScheduledFuture<?> future;
    private volatile String cron;
    private volatile String timezone;
    private volatile boolean running = false;
    private volatile long scheduleVersion = 0;

    void updateJob(@NonNull RaceJob job) {
        this.job = job;
        if (Objects.equals(this.cron, this.job.getCron())
            && Objects.equals(this.timezone, this.job.getTimezone())
            && this.job.getEnabled()
        ) {
            return;
        }
        this.stop();
        this.cron = this.job.getCron();
        this.timezone = this.job.getTimezone();
        this.scheduleVersion = System.currentTimeMillis();
    }

    boolean needSchedule() {
        if (this.job == null || !this.job.getEnabled() || this.cron == null || this.cron.isEmpty()) return false;
        return this.future == null;
    }

    void stop() {
        ScheduledFuture<?> temp;
        synchronized (this) {
            temp = this.future;
            this.future = null;
            this.cron = null;
            this.timezone = null;
        }
        if (temp == null) return;

        try {
            temp.cancel(false);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        } finally {
            log.debug("Stopped old task.");
        }
    }

    void setFuture(ScheduledFuture<?> future) {
        this.future = future;
    }

    void start() {
        running = true;
    }

    void end() {
        running = false;
    }
}
