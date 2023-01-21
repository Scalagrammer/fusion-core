package scg.fusion;

import java.lang.reflect.Type;

import java.util.*;

import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

final class ComponentActorImpl implements ComponentActor {


    private final ReentrantLock receiveLock = new ReentrantLock();

    private final Queue<MessageImpl> messages = new ConcurrentLinkedQueue<>();


    private final Set<DeadLetterImpl> dlq;

    private final MessageListenerHandle dlqHandle;

    private final Map<String, Map<Type, Set<MessageListenerHandle>>> handles;


    ComponentActorImpl(Map<String, Map<Type, Set<MessageListenerHandle>>> handles, MessageListenerHandle dlqHandle) {
        this.handles = handles;
        this.dlqHandle = dlqHandle;
        this.dlq = isNull(dlqHandle) ? (null) : (new LinkedHashSet<>());
    }

    @Override
    public Runnable receive(MessageImpl message) {

        this.appendMessage(message);

        return () -> {

            if (receiveLock.tryLock()) try {

                for (MessageImpl receivedMessage : drainQueue()) {

                    for (MessageListenerHandle handle : listHandles(receivedMessage)) {
                        try {
                            handle.notify(receivedMessage);
                        } catch (Throwable cause) {
                            this.appendDlq(receivedMessage, cause);
                        }
                    }
                }

                for (DeadLetterImpl deadLetter : drainDlq()) {
                    try {
                        dlqHandle.notify(deadLetter);
                    } catch (Throwable cause) {
                        cause.printStackTrace();
                    }
                }

            } finally {
                receiveLock.unlock();
            }
        };
    }

    private Set<MessageListenerHandle> listHandles(MessageImpl message) {
        return message.tags.stream().flatMap(streamHandlesByTag(message.type)).collect(toSet());
    }

    private Iterable<MessageImpl> drainQueue() {
        return new Iterable<MessageImpl>() {
            @Override
            public Iterator<MessageImpl> iterator() {
                return new Iterator<MessageImpl>() {

                    MessageImpl next;

                    @Override
                    public boolean hasNext() {
                        return nonNull(next = messages.poll());
                    }

                    @Override
                    public MessageImpl next() {
                        return next;
                    }

                };
            }
        };
    }

    private Iterable<DeadLetterImpl> drainDlq() {

        if (nonNull(dlqHandle) && !dlq.isEmpty()) {
            return new Iterable<DeadLetterImpl>() {
                @Override
                public Iterator<DeadLetterImpl> iterator() {
                    return new Iterator<DeadLetterImpl>() {

                        final Iterator<DeadLetterImpl> underlying = dlq.iterator();

                        @Override
                        public boolean hasNext() {

                            boolean hasNext = underlying.hasNext();

                            if (!hasNext) {
                                dlq.clear();
                            }

                            return hasNext;

                        }

                        @Override
                        public DeadLetterImpl next() {
                            return underlying.next();
                        }

                    };
                }
            };
        }

        return Collections::emptyIterator;

    }

    private void appendMessage(MessageImpl message) {
        if (nonNull(message)) {
            messages.add(message);
        }
    }

    private void appendDlq(MessageImpl message, Throwable cause) {
        if (nonNull(dlqHandle)) {
            dlq.add(new DeadLetterImpl(cause, message));
        }
    }

    private Function<String, Stream<MessageListenerHandle>> streamHandlesByTag(Type messageType) {
        return tag -> {

            Map<Type, Set<MessageListenerHandle>> byTypeHandles = handles.getOrDefault(tag, emptyMap());

            return concat(byTypeHandles.getOrDefault(messageType, emptySet()).stream(), byTypeHandles.getOrDefault(Object.class, emptySet()).stream());

        };
    }

}
