package scg.fusion.aop;

import scg.fusion.MethodJoint;

public interface ExecutionJoinPoint extends JoinPoint {

    @Override
    MethodJoint at();

    Object[] getArgs();

    <R> R proceed(Object...args) throws Throwable;

    default <A> A getArg() {
        return getArg(0);
    }

    default <A> A getArg(int index) {

        Object[] args = getArgs();

        try {
            return (A) args[index];
        } catch (ArrayIndexOutOfBoundsException $) {
            return null;
        }

    }

}
