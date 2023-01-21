package scg.fusion;

import scg.fusion.exceptions.IllegalContractException;
import scg.fusion.messaging.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

import java.util.function.Function;

import static java.lang.String.format;

import static java.lang.System.currentTimeMillis;

import static java.lang.reflect.Modifier.isStatic;
import static java.util.Collections.*;
import static java.util.Objects.isNull;

import static java.util.Objects.nonNull;

import static java.util.concurrent.ForkJoinPool.commonPool;

import static scg.fusion.OnTheFlyFactory.newInstanceMessageListenerHandle;
import static scg.fusion.OnTheFlyFactory.newStaticMessageListenerHandle;
import static scg.fusion.Utils.*;
import static scg.fusion.Verification.*;
import static scg.fusion.annotation.MessageTopics.DEFAULT_TOPIC_NAME;

abstract class MessageBrokerImpl implements MessageBroker, MessagePublisher {

    private static final Executor dispatcher = commonPool();

    private final Map<String, Set<ComponentActor>> byTopicActors = new HashMap<>();

    @Override
    public MessagePublisher forall(String... tags) {
        return payload -> {

            Map<String, String> headers;

            if (payload instanceof PayloadWithHeaders) {

                PayloadWithHeaders payloadWithHeaders = (PayloadWithHeaders) payload;

                headers = payloadWithHeaders.headers;
                payload = payloadWithHeaders.payload;

            } else {
                headers = emptyMap();
            }

            if (isNull(payload)) {
                return false;
            }

            MessageImpl message = new MessageImpl(this::publish0, payload, headers);

            if (tags.length == 0) {
                message.tags.add(DEFAULT_TOPIC_NAME);
            } else {
                addAll(message.tags, tags);
            }

            return publish0(message);

        };
    }

    @Override
    public boolean publish(Object payload) {
        return this.forall(DEFAULT_TOPIC_NAME).publish(payload);
    }

    void registerActor(Class<?> componentType, ComponentProvider<?> provider) {
        if (hasMessageListeners(componentType)) {

            MessageListenerHandle dlqHandle = (null);

            Map<String, Map<Type, Set<MessageListenerHandle>>> mappedHandles = new HashMap<>();

            for (Method method : listVerifiedMessageListeners(componentType)) {

                if (isDlqListener(method, false)) {
                    if (isNull(dlqHandle)) {
                        if (isStatic(method.getModifiers())) {
                            dlqHandle = newStaticMessageListenerHandle(method);
                        } else {
                            dlqHandle = newInstanceMessageListenerHandle(method, provider);
                        }
                    } else {
                        throw new IllegalContractException("message listener component must have only single defined @DlqListener");
                    }
                }

                if (isMessageListener(method, false)) {

                    MessageListenerHandle handle = (null);

                    if (isStatic(method.getModifiers())) {
                        handle = newStaticMessageListenerHandle(method);
                    } else {
                        handle = newInstanceMessageListenerHandle(method, provider);
                    }

                    Type messageType = getMessageType(method);

                    for (String messageTopic : listMessageTopics(method)) {
                        mappedHandles.compute(messageTopic, appendMessageListenerHandle(messageType, handle));
                    }
                }
            }

            if (!mappedHandles.isEmpty()) {

                ComponentActor actor = new ComponentActorImpl(mappedHandles, dlqHandle);

                for (String topic : mappedHandles.keySet()) {

                    Map<Type, Set<MessageListenerHandle>> byTypeMessageListenerHandles = mappedHandles.get(topic);

                    for (Type messageType : byTypeMessageListenerHandles.keySet()) {
                        for (MessageListenerHandle handle : byTypeMessageListenerHandles.get(messageType)) {
                            byTopicActors.compute(topic, appendActor(actor));
                        }
                    }
                }
            } else if (nonNull(dlqHandle)) {
                throw new IllegalContractException("@DlqListener definition in [%s] is illegal without at least one @MessageListener", componentType);
            }
        }
    }

    private boolean publish0(MessageImpl message) {

        boolean success = false;

        for (String topic : message.tags) {

            Set<ComponentActor> actors = byTopicActors.getOrDefault(topic, emptySet());

            for (ComponentActor actor : actors) {
                dispatcher.execute(actor.receive(message));
            }

            success = success || !actors.isEmpty();

        }

        return success;

    }

    private static BiFunction<String, Set<ComponentActor>, Set<ComponentActor>> appendActor(ComponentActor actor) {
        return ($, actors) -> {

            if (isNull(actors)) {
                actors = new HashSet<>();
            }

            actors.add(actor);

            return actors;

        };
    }

    private BiFunction<Type, Set<MessageListenerHandle>, Set<MessageListenerHandle>> appendMessageListenerHandle(MessageListenerHandle handle) {
        return ($, handles) -> {

            if (isNull(handles)) {
                handles = new HashSet<>();
            }

            handles.add(handle);

            return handles;

        };
    }

    private BiFunction<String, Map<Type, Set<MessageListenerHandle>>, Map<Type, Set<MessageListenerHandle>>> appendMessageListenerHandle(Type messageType, MessageListenerHandle handle) {
        return ($, byTypeHandles) -> {

            if (isNull(byTypeHandles)) {
                byTypeHandles = new HashMap<>();
            }

            Set<MessageListenerHandle> handles = byTypeHandles.get(messageType);

            if (isNull(handles)) {

                handles = new HashSet<>();

                byTypeHandles.put(messageType, handles);

            }

            handles.add(handle);

            return byTypeHandles;

        };
    }

}

final class PayloadWithHeaders {

    final Map<String, String> headers;

    final Object payload;

    PayloadWithHeaders(Map<String, String> headers, Object payload) {
        this.headers = new HashMap<>(headers);
        this.payload = payload;
    }

}

final class DeadLetterImpl implements DeadLetter {

    final Throwable    cause;
    final MessageImpl message;

    DeadLetterImpl(Throwable cause, MessageImpl message) {
        this.cause   =   cause;
        this.message = message;
    }

    @Override
    public Throwable getCause() {
        return this.cause;
    }

    @Override
    public Message<?> getMessage() {
        return this.message;
    }

    @Override
    public String toString() {
        return this.cause.getMessage();
    }

}

final class MessageImpl implements Message<Object> {

    private final Function<MessageImpl, Boolean> reply;
    private final Map<String, String> headers;
    private final Object payload;

    final Type type;

    final Set<String> tags = new HashSet<>();

    private final long timestamp = currentTimeMillis();

    MessageImpl(Function<MessageImpl, Boolean> reply, Object payload, Map<String, String> headers) {
        this.reply   = reply;
        this.payload = payload;
        this.headers = headers;
        this.type    = payload.getClass();
    }

    @Override
    public String getHeader(String key) {
        return headers.get(key);
    }

    @Override
    public Object payload() {
        return payload;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public boolean reply(Object payload) {
        return forall(DEFAULT_TOPIC_NAME).publish(payload);
    }

    @Override
    public MessagePublisher forall(String... topics) {
        return payload -> {

            Map<String, String> headers;

            if (payload instanceof PayloadWithHeaders) {

                PayloadWithHeaders payloadWithHeaders = (PayloadWithHeaders) payload;

                headers = payloadWithHeaders.headers;
                payload = payloadWithHeaders.payload;

            } else {
                headers = emptyMap();
            }

            if (isNull(payload)) {
                return false;
            }

            MessageImpl message = new MessageImpl(reply, payload, headers);

            addAll(message.tags, topics);

            return reply.apply(message);

        };
    }

    @Override
    public Iterator<String> iterator() {
        return tags.iterator();
    }

    @Override
    public String toString() {
        return format("Message(payload=%s, tags=%s)", payload, tags);
    }

    @Override
    public boolean equals(Object that) {

        if (this == that) {
            return true;
        }

        if (that == null || getClass() != that.getClass()) {
            return false;
        }

        MessageImpl message = (MessageImpl) that;

        return Objects.equals(payload, message.payload) && tags.equals(message.tags);

    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, tags);
    }

}
