package pers.clare.test.racejob;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import pers.clare.racejob.EnableRaceJob;
import pers.clare.racejob.RaceJobScheduler;
import pers.clare.racejob.vo.RaceJob;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@EnableRaceJob
@Log4j2
@Configuration
@RequiredArgsConstructor
public class JobRegister {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<RaceJob, AtomicInteger>> jobCountMap = new ConcurrentHashMap<>();

    private final String service = UUID.randomUUID().toString();

    private final RaceJobScheduler jobScheduler;

    private final ConcurrentHashMap<RaceJob, AtomicInteger> countMap = new ConcurrentHashMap<>();

    {
        jobCountMap.put(service, countMap);
    }

    public void registerHandlers() {
        List<RaceJob> jobs = jobScheduler.findAll();

        log.info("job count: {}", jobs.size());

        jobs.forEach(job -> {
            countMap.put(job, new AtomicInteger(0));

            jobScheduler.registerHandler(job, (inner) -> {
                countMap.computeIfAbsent(inner, (key) -> new AtomicInteger(0))
                        .incrementAndGet();
            });
        });
    }

    public int getSelfCount(RaceJob job) {
        return jobCountMap.get(service).get(job).get();
    }

    public static int getCount(RaceJob job) {
        int count = 0;
        for (ConcurrentHashMap<RaceJob, AtomicInteger> map : jobCountMap.values()) {
            var increment = map.get(job);
            if (increment != null) {
                count += increment.get();
            }
        }
        return count;
    }

    public static void reset() {
        for (ConcurrentHashMap<RaceJob, AtomicInteger> map : jobCountMap.values()) {
            for (AtomicInteger value : map.values()) {
                value.set(0);
            }
        }
    }
}
