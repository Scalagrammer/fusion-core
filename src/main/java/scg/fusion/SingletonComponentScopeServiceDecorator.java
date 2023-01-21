package scg.fusion;

import scg.fusion.annotation.Singleton;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public final class SingletonComponentScopeServiceDecorator extends ComponentScopeServiceDecorator<Singleton> {

    private volatile Object component;

    @Override
    public Object getComponent() {

        if (isNull(component)) {
            synchronized (this) {
                if (isNull(component)) {
                    onLoad0();
                }
            }
        }

        return this.component;

    }

    @Override
    public void onLoad() {
        if (isNull(component)) {
            onLoad0();
        }
    }

    @Override
    public void onClose() {
        if (nonNull(component)) {
            utilize(component);
            component = (null);
        }
    }

    private void onLoad0() {

        Object component = init(nеw());

        resolve(component);
        wire(component);
        initialize(component);

        this.component = component;

    }

}
