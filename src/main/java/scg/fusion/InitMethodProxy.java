package scg.fusion;

import scg.fusion.cglib.core.Signature;
import scg.fusion.cglib.proxy.MethodProxy;
import scg.fusion.exceptions.InitMethodError;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

final class InitMethodProxy extends MethodProxy {

    private final int parameterCount;
    private final ComponentConstructor init;

    private final AtomicBoolean invoked = new AtomicBoolean(false);

    InitMethodProxy(ComponentConstructor constructor, int parameterCount) {
        this.init = constructor;
        this.parameterCount = parameterCount;
    }

    @Override
    public Signature getSignature() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSuperName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSuperIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object invoke(Object component, Object[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object invokeSuper(Object component, Object[] args) {

        if (nonNull(args) && args.length != parameterCount) {
            throw new InitMethodError(new ArrayIndexOutOfBoundsException(format("<init> method args has illegal length [%d], expected [%s]", args.length, parameterCount)));
        } else if (isNull(args)) {
            throw new InitMethodError(new NullPointerException(format("<init> method args is null, but expected [%d] count", parameterCount)));
        }

        this.init.invoke(component, args);

        return (null);

    }
}
