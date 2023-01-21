package scg.fusion;

import scg.fusion.aop.AutowireJoinPoint;

import java.lang.reflect.Field;

final class AdvisedAutowireJoinPoint implements AutowireJoinPoint {

    private final Object                component;
    private final ComponentProvider<?> dependency;
    private final Field                     field;
    private final AutowireInterceptor advice;

    AdvisedAutowireJoinPoint(Object component, Field field, ComponentProvider<?> dependency, AutowireInterceptor advice) {
        this.field      =      field;
        this.advice     =     advice;
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
        return (R) advice.intercept(component, field, dependency);
    }

    @Override
    public String toString() {
        return field.toString();
    }

}
