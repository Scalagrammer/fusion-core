package scg.fusion;

import java.lang.reflect.Field;

public interface AutowireInterceptor {
    Object intercept(Object component, Field field, ComponentProvider<?> dependency);
}
