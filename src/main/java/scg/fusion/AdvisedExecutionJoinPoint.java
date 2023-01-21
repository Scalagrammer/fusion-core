package scg.fusion;

import scg.fusion.aop.ExecutionJoinPoint;
import scg.fusion.cglib.proxy.ExecutionInterceptor;
import scg.fusion.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

import static java.util.Objects.isNull;

final class AdvisedExecutionJoinPoint implements ExecutionJoinPoint {

    private final Class<?>        callSide;
    private final Object         component;
    private final Method            method;
    private final MethodProxy proxy;
    private final Object[]            args;
    private final ExecutionInterceptor advice;

    public AdvisedExecutionJoinPoint(Class<?> callSide, Object component, Method method, MethodProxy proxy, ExecutionInterceptor advice, Object[] args) {
        this.callSide  =  callSide;
        this.component = component;
        this.method    =    method;
        this.proxy     =     proxy;
        this.args      =      args;
        this.advice    =    advice;
    }

    @Override
    public MethodJoint at() {
        return new MethodJoint(method);
    }

    @Override
    public Object getComponent() {
        return component;
    }

    public Class<?> getCallSide() {
        return callSide;
    }

    public Object[] getArgs() {
        return args;
    }

    public <T> T getArg(int index) {
        return (T) args[index];
    }

    @Override
    public <R> R proceed() throws Throwable {
        return (R) advice.intercept(callSide, component, method, args, proxy);
    }

    public <R> R proceed(Object... args) throws Throwable {

        if (isNull(args) || args.length == 0) {
            args = this.args;
        }

        return (R) advice.intercept(callSide, component, method, args, proxy);
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return method.toString();
    }

}
