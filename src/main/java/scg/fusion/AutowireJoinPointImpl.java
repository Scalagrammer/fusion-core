package scg.fusion;

import scg.fusion.aop.AutowireJoinPoint;

import static java.util.Objects.isNull;

import java.lang.reflect.Field;

final class AutowireJoinPointImpl implements AutowireJoinPoint {

    private final Field                     field;
    private final Object                component;
    private final ComponentProvider<?> dependency;

    private volatile Object dependencyComponent;

    AutowireJoinPointImpl(Object component, Field field, ComponentProvider<?> dependency) {
        this.field      =      field;
        this.component  =  component;
        this.dependency = dependency;
    }

    @Override
    public FieldJoint at() {
        return new FieldJoint(field);
    }

    @Override
    public Object getComponent() {
        return component;
    }

    @Override
    public <R> R proceed() {

        if (isNull(dependencyComponent)) {
            synchronized (this) {
                if (isNull(dependencyComponent)) {
                    dependencyComponent = dependency.getComponent();
                }
            }
        }

        return (R) dependencyComponent;

    }

    @Override
    public String toString() {
        return field.toString();
    }
}
