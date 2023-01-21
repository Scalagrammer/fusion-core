package scg.fusion.annotation;

import scg.fusion.ComponentScopeServiceDecorator;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface Scope {
    Class<? extends ComponentScopeServiceDecorator> decorator();
}
