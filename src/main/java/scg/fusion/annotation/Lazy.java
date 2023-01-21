package scg.fusion.annotation;

import scg.fusion.LazyComponentScopeServiceDecorator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope(decorator = LazyComponentScopeServiceDecorator.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Lazy {}
