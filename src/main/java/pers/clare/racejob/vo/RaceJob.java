package pers.clare.racejob.vo;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.Map;

@Getter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class RaceJob extends RaceJobKey {
    @NonNull
    private String group;
    @NonNull
    private String name;
    @NonNull
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
