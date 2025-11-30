package pers.clare.racejob.function;

import pers.clare.racejob.vo.RaceJob;

public interface RaceJobHandler {
    void execute(RaceJob raceJob) throws InterruptedException;
}
