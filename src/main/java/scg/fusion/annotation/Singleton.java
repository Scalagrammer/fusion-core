package scg.fusion.annotation;

import scg.fusion.SingletonComponentScopeServiceDecorator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope(decorator = SingletonComponentScopeServiceDecorator.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Singleton {}