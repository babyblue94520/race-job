package pers.clare.test.racejob;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import pers.clare.racejob.EnableRaceJob;
import pers.clare.racejob.RaceJobScheduler;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.racejob.vo.RaceJobKey;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@EnableRaceJob
@Log4j2
@Configuration
@RequiredArgsConstructor
public class JobRegister {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<RaceJobKey, AtomicInteger>> jobCountMap = new ConcurrentHashMap<>();

    private final String service = UUID.randomUUID().toString();

    private final RaceJobScheduler jobScheduler;

    private final ConcurrentHashMap<RaceJobKey, AtomicInteger> countMap = new ConcurrentHashMap<>();

    {
        jobCountMap.put(service, countMap);
    }

    public void registerHandlers() {
        List<RaceJob> jobs = jobScheduler.findAll();

        log.info("job count: {}", jobs.size());

        jobs.forEach(job -> {
            countMap.put(job.toKey(), new AtomicInteger(0));

            jobScheduler.registerHandler(job.getKey(), (inner) -> {
                countMap.computeIfAbsent(inner.toKey(), (key) -> new AtomicInteger(0))
                        .incrementAndGet();
            });
        });
    }

    public static int getCount(RaceJob job) {
        int count = 0;
        for (ConcurrentHashMap<RaceJobKey, AtomicInteger> map : jobCountMap.values()) {
            var increment = map.get(job.toKey());
            if (increment != null) {
                count += increment.get();
            }
        }
        return count;
    }

    public static void reset() {
        for (ConcurrentHashMap<RaceJobKey, AtomicInteger> map : jobCountMap.values()) {
            for (AtomicInteger value : map.values()) {
                value.set(0);
            }
        }
    }
}
