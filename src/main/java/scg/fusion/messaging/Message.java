package scg.fusion.messaging;

public interface Message<T> extends Iterable<String>, MessageBroker {

    String getHeader(String key);

    T payload();

    long timestamp();

    boolean reply(Object payload);

}
