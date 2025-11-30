package pers.clare.racejob;

import org.springframework.lang.NonNull;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.racejob.vo.RaceJobKey;
import pers.clare.racejob.vo.RaceJobStatus;

import java.util.List;

@SuppressWarnings("UnusedReturnValue")
public interface RaceJobStore {
    @NonNull
    List<RaceJob> findAll(String instance);

    @NonNull
    List<RaceJob> findAll(String instance, String group);

    RaceJob find(@NonNull String instance, @NonNull RaceJobKey jobKey);

    void insert(@NonNull String instance, @NonNull RaceJob job, @NonNull long nextTime);

    void update(@NonNull String instance, @NonNull RaceJob job, @NonNull long nextTime);

    void updateActive(@NonNull String instance, @NonNull RaceJob job, @NonNull long activeTime);

    void delete(@NonNull String instance, @NonNull RaceJobKey jobKey);

    void enable(@NonNull String instance, @NonNull RaceJobKey jobKey);

    void disable(@NonNull String instance, @NonNull RaceJobKey jobKey);

    RaceJobStatus getStatus(@NonNull String instance, @NonNull RaceJobKey jobKey);

    @NonNull
    int release(@NonNull String instance, @NonNull RaceJobKey jobKey, @NonNull long nextTime);

    @NonNull
    int compete(@NonNull String instance, @NonNull RaceJobKey jobKey
            , @NonNull long nextTime, @NonNull long startTime);

    /**
     * Used to execute instructions.
     */
    int compete(
            String instance, RaceJobKey jobKey
            , long startTime
    );

    @NonNull
    int finish(@NonNull String instance, @NonNull RaceJobKey jobKey, @NonNull long endTime);

}
