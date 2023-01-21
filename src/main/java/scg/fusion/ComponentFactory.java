package scg.fusion;

import java.lang.annotation.Annotation;
import java.util.Map;

import java.util.stream.Stream;

public interface ComponentFactory extends AutoCloseable, Iterable<Class<?>> {

    @Override
    void close();

    boolean hasComponent(Class<?> expectedType);

    boolean hasSubtypeComponents(Class<?> expectedSuperType);

    <T> ComponentProvider<T> getProvider(Class<T> componentType);

    <T> T get(Class<T> componentType);

    Object[] listAll(Class<?>[] expectedTypes);

    Iterable<Object> listByAnnotation(Class<? extends Annotation> expectedAnnotationType);

    <T> Stream<T> streamAllSubtypes(Class<T> expectedSuperType);

    ComponentFactory swap(Object...components);

    Map<Joint, AutowiringHook> autowiringBy(String pointcut, Object...args);

}
