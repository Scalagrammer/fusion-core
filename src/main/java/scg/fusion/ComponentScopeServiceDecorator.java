package scg.fusion;

import java.lang.annotation.Annotation;

import static java.util.Objects.nonNull;

public abstract class ComponentScopeServiceDecorator<A extends Annotation> extends ComponentScopeService implements ComponentScope {

    protected A annotation;

    ComponentService    componentService = null;
    ComponentProvider componentAllocator = null;
    ComponentWiring  componentAutowiring = null;

    @Override
    public Object init(Object component, Object[]... args) {
        if (nonNull(componentService)) {
            return componentService.init(component, args);
        } else {
            return component;
        }
    }

    @Override
    public Object init(Object component) {
        if (nonNull(componentService)) {
            return componentService.init(component);
        } else {
            return component;
        }
    }

    @Override
    public Object nеw() {
        if (nonNull(componentAllocator)) {
            return componentAllocator.getComponent();
        } else {
            return (null);
        }
    }

    @Override
    public void initialize(Object component) {
        if (nonNull(componentService)) {
            componentService.initialize(component);
        }
    }

    @Override
    public void resolve(Object component) {
        if (nonNull(componentService)) {
            componentService.resolve(component);
        }
    }

    @Override
    public void utilize(Object component) {
        if (nonNull(componentService)) {
            componentService.utilize(component);
        }
    }

    @Override
    public void wire(Object component) {
        if (nonNull(componentAutowiring)) {
            componentAutowiring.wire(component);
        }
    }

    static ComponentScopeServiceDecorator instance(Object instance) {
        return new ComponentScopeServiceDecorator() {
            @Override
            public Object getComponent() {
                return instance;
            }
        };
    }
}