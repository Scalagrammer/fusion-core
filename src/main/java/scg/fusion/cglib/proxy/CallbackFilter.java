package scg.fusion.cglib.proxy;

import java.lang.reflect.Method;

public interface CallbackFilter {
    /**
     * Map a method to a callback.
     * @param method the intercepted method
     * @return the index into the array of callbacks (as specified by {@link Enhancer#setCallbacks}) to use for the method,
     */
    int accept(Method method);

    /**
     * The <code>CallbackFilter</code> in use affects which cached class
     * the <code>Enhancer</code> will use, so this is a reminder that
     * you should correctly implement <code>equals</code> and
     * <code>hashCode</code> for custom <code>CallbackFilter</code>
     * implementations in order to improve performance.
     */
    boolean equals(Object o);
}
