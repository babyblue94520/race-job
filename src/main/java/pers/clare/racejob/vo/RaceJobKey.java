package pers.clare.racejob.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class RaceJobKey {
    private String group;
    private String name;

    @Override
    public String toString() {
        return "EventJobKey{" +
               "group='" + group + '\'' +
               ", name='" + name + '\'' +
               '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof RaceJobKey)) return false;

        RaceJobKey that = (RaceJobKey) o;
        return Objects.equals(getGroup(), that.getGroup()) && Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getGroup());
        result = 31 * result + Objects.hashCode(getName());
        return result;
    }
}
