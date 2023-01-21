package scg.fusion.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
public @interface Privileged {

    int HIGHEST_PRIORITY = 0;
    int LOWEST_PRIORITY = 0x7FFFFFFF;

    int value() default HIGHEST_PRIORITY;

}
