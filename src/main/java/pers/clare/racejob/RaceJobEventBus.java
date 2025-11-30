package pers.clare.racejob;

import java.util.function.Consumer;

@SuppressWarnings("UnusedReturnValue")
public interface RaceJobEventBus {

    void send(String body);

    void listen(Consumer<String> listener);

}
