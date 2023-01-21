package scg.fusion;

import scg.fusion.aop.ExecutionJoinPoint;
import scg.fusion.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

final class ExecutionJoinPointImpl implements ExecutionJoinPoint {

    private final Class<?> callSide;
    private final Object  component;
    private final Method     method;
    private final MethodProxy proxy;
    private final Object[]     args;

    public ExecutionJoinPointImpl(Class<?> callSide, Object component, Method method, MethodProxy proxy, Object[] args) {
        this.callSide = callSide;
        this.component = component;
        this.method = method;
        this.proxy  =  proxy;
        this.args   =   args;
    }

    public Class<?> getCallSide() {
        return callSide;
    }

    @Override
    public MethodJoint at() {
        return new MethodJoint(method);
    }

    @Override
    public Object[] getArgs() {
        return args;
    }

    @Override
    public <R> R proceed(Object... args) throws Throwable {

        if (args.length == 0) {
            args = this.args;
        }

        return (R) proxy.invokeSuper(component, args);

    }

    @Override
    public Object getComponent() {
        return component;
    }

    @Override
    public <R> R proceed() throws Throwable {
        return (R) proxy.invokeSuper(component, args);
    }

    @Override
    public String toString() {
        return method.toString();
    }

}
