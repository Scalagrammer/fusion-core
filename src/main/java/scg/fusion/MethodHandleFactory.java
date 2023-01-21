package scg.fusion;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

public interface MethodHandleFactory {
    MethodHandle bind(Method method);
}
