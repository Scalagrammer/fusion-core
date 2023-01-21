package scg.fusion.annotation;

import scg.fusion.PrototypeComponentScopeServiceDecorator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope(decorator = PrototypeComponentScopeServiceDecorator.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Prototype {
    boolean value() default false;
}
