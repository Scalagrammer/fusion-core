package scg.fusion.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Repeatable(Crosscut.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface Around {
    String value();
}
