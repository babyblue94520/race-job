package pers.clare.racejob.vo;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class RaceJob extends RaceJobKey {
    @NonNull
    private String group;
    @NonNull
    private String name;
    @NonNull
    private String key;
    @NonNull
    @Builder.Default
    private Integer version = 1;
    @NonNull
    @Builder.Default
    private String timezone = ZoneId.systemDefault().toString();
    @NonNull
    @Builder.Default
    private String description = "";
    @NonNull
    @Builder.Default
    private String cron = "";
    @NonNull
    @Builder.Default
    private String afterGroup = "";
    @NonNull
    @Builder.Default
    private String afterName = "";
    @NonNull
    @Builder.Default
    private Boolean enabled = true;
    @NonNull
    @Builder.Default
    private Map<String, Object> data = Collections.emptyMap();

    @Override
    public String toString() {
        return "RaceJob{" +
               "group=\"" + group + '\"' +
               ", name=\"" + name + '\"' +
               '}';
    }
}