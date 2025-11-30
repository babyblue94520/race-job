package pers.clare.racejob.exception;

@SuppressWarnings("unused")
public class RaceJobException extends RuntimeException {
    public RaceJobException(String message) {
        super(message);
    }

    public RaceJobException(Throwable cause) {
        super(cause);
    }
}
