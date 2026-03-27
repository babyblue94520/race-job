package pers.clare.racejob.vo;

import lombok.*;

import java.util.Collections;
import java.util.Map;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class RaceJob {
    @NonNull
    private String group;
    @NonNull
    private String name;
    @NonNull
    private String key;
    @NonNull
    @Builder.Default
    private Integer version = 1;
    @Builder.Default
    private String timezone = "";
    @NonNull
    @Builder.Default
    private String description = "";
    @NonNull
    @Builder.Default
    private String cron = "";
    @NonNull
    @Builder.Default
    private String dependsKey = "";
    @NonNull
    @Builder.Default
    private Boolean enabled = true;
    @NonNull
    @Builder.Default
    private Map<String, Object> data = Collections.emptyMap();

    public RaceJobKey toKey() {
        return new RaceJobKey(group, name);
    }

    @Override
    public String toString() {
        return "RaceJob{" +
                "group=\"" + group + '\"' +
                ", name=\"" + name + '\"' +
                '}';
    }
}