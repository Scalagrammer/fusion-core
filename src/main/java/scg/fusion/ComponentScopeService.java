package scg.fusion;

import scg.fusion.exceptions.IllegalContractException;

import static scg.fusion.Utils.isSingletonOrPrototype;

abstract class ComponentScopeService implements LifecycleListener, ComponentService, ComponentWiring {

    @Override
    public void onLoad() {

    }

    @Override
    public void onClose() {

    }

    static ComponentScopeServiceDecorator newAspectScope(Class<?> componentType) {

        if (isSingletonOrPrototype(componentType)) {
            throw new IllegalContractException("aspect [%s] must have @Lazy (explicitly or applied by default)", componentType);
        }

        return new LazyComponentScopeServiceDecorator();

    }

}
