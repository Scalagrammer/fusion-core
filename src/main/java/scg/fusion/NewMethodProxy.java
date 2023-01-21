package scg.fusion;

import scg.fusion.cglib.core.Signature;
import scg.fusion.cglib.proxy.MethodProxy;
import scg.fusion.exceptions.IllegalContractException;

import static java.util.Objects.nonNull;

final class NewMethodProxy extends MethodProxy {

    private final ComponentProvider allocator;

    NewMethodProxy(ComponentProvider allocator) {
        this.allocator = allocator;
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
    public Object invoke(Object obj, Object[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object invokeSuper(Object target, Object[] args) {

        if (nonNull(target)) {
            throw new IllegalContractException("target for allocation must be null");
        }

        if (nonNull(args) && args.length != 0) {
            throw new IllegalContractException("args for allocation must be null or empty");
        }

        return allocator.getComponent();

    }

}
