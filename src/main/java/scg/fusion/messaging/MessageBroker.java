package scg.fusion.messaging;

public interface MessageBroker {
   MessagePublisher forall(String...topics);
}
