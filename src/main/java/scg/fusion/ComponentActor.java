package scg.fusion;

public interface ComponentActor {
    Runnable receive(MessageImpl message);
}
