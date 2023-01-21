package scg.fusion;

import scg.fusion.annotation.ThreadLocal;

import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.ThreadLocal.withInitial;

public final class ThreadLocalComponentScopeServiceDecorator extends ComponentScopeServiceDecorator<ThreadLocal> {

    private final java.lang.ThreadLocal<Object> threadLocal = withInitial(this::onLoad0);

    private final List<Object> store = new CopyOnWriteArrayList<>();

    @Override
    public void onClose() {
        for (Object component : store) {
            utilize(component);
        }
    }

    @Override
    public Object getComponent() {
        return threadLocal.get();
    }

    private Object onLoad0() {

        Object component = init(nеw());

        resolve(component);
        wire(component);
        initialize(component);
        store.add(component);

        return component;

    }
}
