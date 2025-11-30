package pers.clare.racejob.impl;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.StringUtils;
import pers.clare.racejob.RaceJobEventBus;
import pers.clare.racejob.RaceJobProperties;
import pers.clare.racejob.RaceJobScheduler;
import pers.clare.racejob.RaceJobStore;
import pers.clare.racejob.constant.RaceEventType;
import pers.clare.racejob.constant.RaceJobState;
import pers.clare.racejob.exception.RaceJobException;
import pers.clare.racejob.function.RaceJobHandler;
import pers.clare.racejob.util.JobUtil;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.racejob.vo.RaceJobKey;
import pers.clare.racejob.vo.RaceJobStatus;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
@SuppressWarnings("unused")
public class RaceJobSchedulerImpl implements RaceJobScheduler, InitializingBean, DisposableBean, CommandLineRunner {
    protected static final String EVENT_SPLIT = "\n";

    private final ConcurrentMap<RaceJobKey, RaceJobContext> jobContextMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<RaceJobKey, RaceJobHandler> jobHandlerMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<RaceJobKey, ConcurrentMap<RaceJobKey, RaceJobKey>> afterJobsMap = new ConcurrentHashMap<>();

    private final AtomicInteger executingCount = new AtomicInteger();

    private final RaceJobProperties properties;

    private final RaceJobStore jobStore;

    private final RaceJobEventBus eventBus;

    private ScheduledExecutorService executor;

    private volatile boolean destroyed = false;

    public RaceJobSchedulerImpl(@NonNull RaceJobProperties properties, @NonNull RaceJobStore jobStore) {
        this(properties, jobStore, null);
    }

    public RaceJobSchedulerImpl(@NonNull RaceJobProperties properties, @NonNull RaceJobStore jobStore, RaceJobEventBus eventBus) {
        this.properties = properties;
        this.jobStore = jobStore;
        this.eventBus = eventBus;
    }

    @Override
    public void afterPropertiesSet() {
        if (eventBus != null) {
            eventBus.listen(this::handleEvent);
        }
    }

    @Override
    public void destroy() {
        destroyed = true;
        if (executor == null) return;
        log.info("Shutdown...");
        executor.shutdownNow();
        log.info("Shutdown completed");
    }

    @Override
    public void run(String... args) {
        if (Boolean.FALSE.equals(properties.getExecutionEnabled())) return;
        executor = Executors.newScheduledThreadPool(properties.getThreadCount(), new CustomizableThreadFactory("race-job-"));
        executor.scheduleAtFixedRate(this::reload, 0, properties.getReloadInterval().toMillis(), TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::updateActiveTime, properties.getUpdateActiveInterval().toMillis(), properties.getUpdateActiveInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean isScheduleUnavailable() {
        if (destroyed) return true;
        if (executor == null || executor.isShutdown() || executor.isTerminated()) return true;
        return false;
    }

    /**
     * register job executor
     */
    public RaceJobHandler registerHandler(RaceJobKey jobKey, RaceJobHandler handler) {
        jobHandlerMap.put(jobKey, handler);
        return handler;
    }

    public void unregisterHandler(RaceJobKey jobKey) {
        jobHandlerMap.remove(jobKey);
    }

    @Override
    public String getInstance() {
        return properties.getInstance();
    }

    @Override
    public List<RaceJob> findAll() {
        return jobStore.findAll(getInstance());
    }

    @Override
    public List<RaceJob> findAll(String group) {
        return jobStore.findAll(getInstance(), group);
    }

    @Override
    public RaceJob find(RaceJobKey jobKey) {
        return jobStore.find(getInstance(), jobKey);
    }

    public void add(RaceJob job) {
        if (job == null) return;
        long nextTime = getNextTime(job);
        RaceJob oldJob = jobStore.find(getInstance(), job);
        if (oldJob == null) {
            try {
                jobStore.insert(getInstance(), job, nextTime);
            } catch (RaceJobException e) {
                try {
                    // retry update
                    jobStore.update(getInstance(), job, nextTime);
                } catch (RaceJobException ex) {
                    throw e;
                }
            }
        } else if (!equals(job, oldJob)) {
            jobStore.update(getInstance(), job, nextTime);
        } else {
            return;
        }
        job = jobStore.find(getInstance(), job);
        if (job == null) return;
        reload(job);
        publishJobChangeEvent(job);
    }

    public void remove(RaceJobKey jobKey) {
        try {
            jobStore.delete(getInstance(), jobKey);
            reload(jobKey);
            publishJobChangeEvent(jobKey);
        } catch (Exception e) {
            throw new RaceJobException(e);
        }
    }

    @Override
    public void enable(RaceJobKey jobKey) {
        jobStore.enable(getInstance(), jobKey);
        reload(jobKey);
        publishJobChangeEvent(jobKey);
    }


    @Override
    public void disable(RaceJobKey jobKey) {
        jobStore.disable(getInstance(), jobKey);
        reload(jobKey);
        publishJobChangeEvent(jobKey);
    }

    @Override
    public void execute(RaceJobKey jobKey) {
        if (eventBus == null) {
            handleLocalJobExecution(jobKey, System.currentTimeMillis());
        } else {
            publishJobExecutionEvent(jobKey);
        }
    }

    /**
     * Update running job active time.
     */
    private void updateActiveTime() {
        long now = System.currentTimeMillis();
        for (RaceJobContext jobContext : jobContextMap.values()) {
            if (!jobContext.isRunning()) continue;
            var job = jobContext.getJob();
            jobStore.updateActive(getInstance(), job, now);
        }
    }

    private void clearNotExists(List<RaceJob> jobs) {
        Set<RaceJobKey> exists = new HashSet<>(jobs);
        for (RaceJobKey jobKey : jobContextMap.keySet()) {
            if (!exists.contains(jobKey)) {
                clear(jobKey);
            }
        }
    }

    private void clear(RaceJobKey jobKey) {
        log.debug("clearing jobKey {}", jobKey);
        RaceJobContext jobContext = jobContextMap.remove(jobKey);
        if (jobContext != null) {
            jobContext.stop();
        }
        for (RaceJobKey key : afterJobsMap.keySet()) {
            afterJobsMap.computeIfPresent(key, (k, inner) -> {
                inner.remove(jobKey);
                return inner.isEmpty() ? null : inner;
            });
        }
    }

    private void reload() {
        log.debug("reloading jobs");
        try {
            List<RaceJob> jobs = jobStore.findAll(getInstance());
            for (RaceJob job : jobs) {
                reload(job);
            }
            clearNotExists(jobs);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void reload(RaceJobKey jobKey) throws RaceJobException {
        RaceJob job = jobStore.find(getInstance(), jobKey);
        if (job == null) {
            clear(jobKey);
        } else {
            reload(job);
        }
    }

    private void reload(RaceJob job) {
        var jobContext = jobContextMap
                .computeIfAbsent(job, key -> new RaceJobContext());

        jobContext.updateJob(job);

        addSchedule(jobContext);

        String afterGroup = job.getAfterGroup();
        String afterName = job.getAfterName();
        if (StringUtils.hasLength(afterGroup)
            && StringUtils.hasLength(afterName)
        ) {
            var eventJobKey = new RaceJobKey(afterGroup, afterName);
            afterJobsMap.compute(eventJobKey, (key, inner) -> {
                if (inner == null) inner = new ConcurrentHashMap<>();
                inner.put(job, job);
                return inner;
            });
        }
    }

    /**
     * add job to schedule
     */
    private void addSchedule(RaceJobContext jobContext) {
        if (isScheduleUnavailable()) return;
        if (!jobContext.needSchedule()) return;
        long version = jobContext.getScheduleVersion();
        long delay = JobUtil.getNextDelay(jobContext.getCron(), jobContext.getTimezone());
        var future = executor.schedule(() -> {
            try {
                if (discontinue(jobContext, version)) return;
                boolean next = doExecute(jobContext);
                if (!next) return;
                if (discontinue(jobContext, version)) return;
            } finally {
                jobContext.setFuture(null);
            }
            addSchedule(jobContext);

        }, delay, TimeUnit.MILLISECONDS);
        jobContext.setFuture(future);
    }

    private boolean discontinue(RaceJobContext jobContext, long version) {
        if (isScheduleUnavailable()) return true;
        RaceJobContext currentContext = jobContextMap.get(jobContext.getJob());
        if (currentContext == null) return true;
        if (!Objects.equals(currentContext.getScheduleVersion(), version)) {
            log.debug("Skip task execution.");
            return true;
        }
        return false;
    }

    private boolean doExecute(RaceJobContext jobContext) {
        return this.doExecute(jobContext, null);
    }

    /**
     * @param executeTime Execution command time. schedule job is null.
     */
    private boolean doExecute(RaceJobContext jobContext, Long executeTime) {
        if (Boolean.FALSE.equals(properties.getExecutionEnabled())) return false;
        if (jobContext.isRunning()) return true;
        RaceJob job = jobContext.getJob();
        RaceJobHandler jobHandler = jobHandlerMap.get(job);
        if (jobHandler == null) return true;

        delayExecute();

        executingCount.getAndIncrement();
        boolean executed = false;
        try {
            String instance = getInstance();

            RaceJobStatus jobStatus = jobStore.getStatus(instance, job);
            if (jobStatus == null) return false;

            int compete;
            if (executeTime == null) {
                long startTime = System.currentTimeMillis();
                long nextTime = getNextTime(job);
                if (Objects.equals(RaceJobState.EXECUTING, jobStatus.getState())) {
                    var activeInterval = properties.getUpdateActiveInterval().toMillis();
                    var checkTime = jobStatus.getLastActiveTime() + (activeInterval * 1.5);
                    if (startTime < checkTime) return true;
                    int count = jobStore.release(instance, job, nextTime);
                    if (count == 0) return true;
                }
                compete = jobStore.compete(instance, job, nextTime, startTime);
            } else {
                compete = jobStore.compete(instance, job, executeTime);
            }
            if (compete == 0) return true;

            jobContext.start();
            try {
                jobHandler.execute(job);
                executed = true;
            } catch (Exception e) {
                if (Boolean.TRUE.equals(properties.getAbortOnError())) {
                    jobHandlerMap.remove(job);
                }
                log.error(e.getMessage(), e);
            }

            jobStore.finish(instance, job, System.currentTimeMillis());
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            jobContext.end();
            executingCount.getAndDecrement();
            if (executed) handleJobCompletion(job);
        }
        return false;
    }

    private long getNextTime(RaceJob job) {
        if (StringUtils.hasLength(job.getCron())) {
            return JobUtil.getNextTime(job.getCron(), job.getTimezone());
        } else if (
                StringUtils.hasLength(job.getAfterGroup())
                && StringUtils.hasLength(job.getAfterName())
        ) {
            var key = new RaceJobKey(job.getAfterGroup(), job.getAfterName());
            RaceJobContext jobContext = jobContextMap.get(key);
            if (jobContext == null) return 0L;
            return getNextTime(jobContext.getJob());
        }
        return 0L;
    }

    private void handleLocalJobExecution(RaceJobKey key, Long time) {
        RaceJobContext jobContext = jobContextMap.get(key);
        if (jobContext == null) return;
        doExecute(jobContext, time);
    }

    private void handleJobCompletion(RaceJobKey key) {
        if (eventBus == null) {
            handleLocalJobCompletion(key);
        } else {
            publishJobCompletionEvent(key);
        }
    }

    private void handleLocalJobCompletion(RaceJobKey key) {
        if (isScheduleUnavailable()) return;
        Map<RaceJobKey, RaceJobKey> map = afterJobsMap.get(key);
        if (map == null) return;
        for (RaceJobKey value : map.values()) {
            RaceJobContext jobContext = jobContextMap.get(value);
            if (jobContext == null) continue;
            executor.submit(() -> {
                doExecute(jobContext);
            });
        }
    }

    private void publishJobChangeEvent(RaceJobKey jobKey) {
        publishEvent(RaceEventType.CHANGE, jobKey.getGroup(), jobKey.getName());
    }

    private void publishJobExecutionEvent(RaceJobKey jobKey) {
        publishEvent(RaceEventType.EXECUTE, jobKey.getGroup(), jobKey.getName(), String.valueOf(System.currentTimeMillis()));
    }

    private void publishJobCompletionEvent(RaceJobKey key) {
        publishEvent(RaceEventType.COMPLETE, key.getGroup(), key.getName());
    }

    private void handleEvent(String body) {
        String[] result = body.split(EVENT_SPLIT);
        String[] array = new String[4];
        System.arraycopy(result, 0, array, 0, result.length);
        int type = Integer.parseInt(array[0]);
        String group = array[1];
        String name = array[2];
        RaceJobKey jobKey = new RaceJobKey(group, name);
        switch (type) {
            case RaceEventType.CHANGE:
                reload(jobKey);
                break;
            case RaceEventType.EXECUTE:
                handleLocalJobExecution(jobKey, Long.valueOf(array[3]));
                break;
            case RaceEventType.COMPLETE:
                handleLocalJobCompletion(jobKey);
                break;
            default:
        }
    }

    /**
     * Calculate the delay time based on the CPU usage rate and the number of currently executed tasks
     */
    private void delayExecute() {
        int count = executingCount.get();
        if (count == 0) return;
        long delay = (long) (count * 10L + (getCpuUsage() * 100));
        if (delay > 100) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private double getCpuUsage() {
        return ((OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean()).getSystemCpuLoad();
    }

    private void publishEvent(int type, String... args) {
        if (eventBus == null) return;
        StringBuilder message = new StringBuilder();
        message.append(type);
        for (String arg : args) {
            message.append(EVENT_SPLIT).append(arg);
        }
        eventBus.send(message.toString());
    }

    private boolean equals(RaceJob source, RaceJob target) {
        return Objects.equals(source.getGroup(), target.getGroup())
               && Objects.equals(source.getName(), target.getName())
               && Objects.equals(source.getDescription(), target.getDescription())
               && Objects.equals(source.getTimezone(), target.getTimezone())
               && Objects.equals(source.getCron(), target.getCron())
               && Objects.equals(source.getEnabled(), target.getEnabled())
               && Objects.equals(source.getAfterGroup(), target.getAfterGroup())
               && Objects.equals(source.getAfterName(), target.getAfterName())
               && Objects.equals(source.getData(), target.getData());
    }
}

