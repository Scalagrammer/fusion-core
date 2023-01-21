package scg.fusion.cglib.proxy;

import java.lang.reflect.Method;

public interface ExecutionInterceptor extends Callback {
    /**
     * All generated proxied methods call this method instead of the original method.
     * The original method may either be invoked by normal reflection using the Method object,
     * or by using the MethodProxy (faster).
     * @param callSide caller class
     * @param enhancer "this", the enhanced object
     * @param method intercepted Method
     * @param args argument array; primitive types are wrapped
     * @param proxy used to invoke super (non-intercepted method); may be called
     * as many times as needed
     * @throws Throwable any exception may be thrown; if so, super method will not be invoked
     * @return any value compatible with the signature of the proxied method. Method returning void will ignore this value.
     * @see MethodProxy
     */
    Object intercept(Class<?> callSide, Object enhancer, Method method, Object[] args, MethodProxy proxy) throws Throwable;
}
