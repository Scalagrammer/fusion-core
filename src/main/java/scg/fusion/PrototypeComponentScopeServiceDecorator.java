package scg.fusion;

import scg.fusion.annotation.Prototype;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PrototypeComponentScopeServiceDecorator extends ComponentScopeServiceDecorator<Prototype> {

    private boolean utilizable;

    private Set<Object> componentStore;

    @Override
    public void onLoad() {
        this.utilizable     = annotation.value();
        this.componentStore = utilizable ? (new LinkedHashSet<>()) : (null);
    }

    @Override
    public Object getComponent() {

        Object component = init(nеw());

        resolve(component);
        wire(component);
        initialize(component);
        store(component);

        return component;

    }

    @Override
    public void onClose() {
        if (utilizable) {
            for (Object component : componentStore) {
                utilize(component);
            }
        }
    }

    private void store(Object component) {
        if (utilizable) {
            componentStore.add(component);
        }
    }

}
