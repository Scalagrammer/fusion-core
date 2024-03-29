package scg.fusion.cglib.core;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MethodWrapper {
    private static final MethodWrapperKey KEY_FACTORY =
            (MethodWrapperKey)KeyFactory.create(MethodWrapperKey.class);

    /** Internal interface, only public due to ClassLoader issues. */
    public interface MethodWrapperKey {
        public Object newInstance(String name, String[] parameterTypes, String returnType);
    }

    private MethodWrapper() {
    }

    public static Object create(Method method) {
        return KEY_FACTORY.newInstance(method.getName(),
                ReflectUtils.getNames(method.getParameterTypes()),
                method.getReturnType().getName());
    }

    public static Set createSet(Collection methods) {
        Set set = new HashSet();
        for (Object method : methods) {
            set.add(create((Method) method));
        }
        return set;
    }
}
