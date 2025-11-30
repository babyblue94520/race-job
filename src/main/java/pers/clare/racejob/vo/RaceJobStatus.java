package pers.clare.racejob.vo;

import lombok.Getter;

@Getter
public class RaceJobStatus {
    private final Integer state;

    private final Long nextTime;

    private final Long lastActiveTime;

    private final Boolean enabled;

    public RaceJobStatus(Integer state, Long nextTime, Long lastActiveTime, Boolean enabled) {
        this.state = state;
        this.nextTime = nextTime;
        this.lastActiveTime = lastActiveTime;
        this.enabled = enabled;
    }

}
