package scg.fusion.messaging;

public interface DeadLetter {

    Message<?> getMessage();
    Throwable    getCause();

}
