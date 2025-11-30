package pers.clare.racejob;


import org.springframework.lang.NonNull;
import pers.clare.racejob.function.RaceJobHandler;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.racejob.vo.RaceJobKey;

import java.util.List;

@SuppressWarnings("unused")
public interface RaceJobScheduler {

    String getInstance();

    @NonNull
    List<RaceJob> findAll();

    @NonNull
    List<RaceJob> findAll(@NonNull String group);

    RaceJob find(@NonNull RaceJobKey jobKey);

    /**
     * create or modify job
     */
    void add(@NonNull RaceJob job);

    void remove(@NonNull RaceJobKey jobKey);

    /**
     * start job
     */
    void enable(@NonNull RaceJobKey jobKey);

    /**
     * stop job
     */
    void disable(@NonNull RaceJobKey jobKey);

    /**
     * add job event executor
     */
    RaceJobHandler registerHandler(@NonNull RaceJobKey jobKey, @NonNull RaceJobHandler handler);

    /**
     * remove job event executor
     */
    void unregisterHandler(@NonNull RaceJobKey jobKey);

    /**
     * executor job
     */
    void execute(@NonNull RaceJobKey jobKey);

}

