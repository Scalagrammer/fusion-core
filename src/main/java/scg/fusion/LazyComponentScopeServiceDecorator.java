package scg.fusion;

import scg.fusion.annotation.Lazy;

import static java.util.Objects.isNull;

public final class LazyComponentScopeServiceDecorator extends ComponentScopeServiceDecorator<Lazy> {

    private volatile Object component;

    @Override
    public Object getComponent() {

        if (isNull(component)) {
            synchronized (this) {
                if (isNull(component)) {
                    getComponent0();
                }
            }
        }

        return component;

    }

    @Override
    public void onClose() {
        if (isNull(component)) {
            utilize(component);
            component = (null);
        }
    }

    private void getComponent0() {

        Object component = init(nеw());

        resolve(component);
        wire(component);
        initialize(component);

        this.component = component;

    }
}
