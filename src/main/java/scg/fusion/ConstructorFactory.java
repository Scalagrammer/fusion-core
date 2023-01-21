package scg.fusion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

interface ConstructorFactory extends Function<Constructor<?>, Method> {

    @Override
    default Method apply(Constructor<?> constructor) {
        return toInitMethod(constructor);
    }

    Method toInitMethod(Constructor<?> constructor);

    Method toNewMethod(Class<?> componentType, int modifiers);

}
