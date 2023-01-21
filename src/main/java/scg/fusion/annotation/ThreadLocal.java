package scg.fusion.annotation;

import scg.fusion.ThreadLocalComponentScopeServiceDecorator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope(decorator = ThreadLocalComponentScopeServiceDecorator.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ThreadLocal {}
