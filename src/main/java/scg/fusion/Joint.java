package scg.fusion;

import java.lang.reflect.AnnotatedElement;

public interface Joint extends AnnotatedElement {

    int getModifiers();

    String getName();

    Class<?> getComponentType();

}
