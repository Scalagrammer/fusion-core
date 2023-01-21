package scg.fusion.aop;

import scg.fusion.Joint;

public interface JoinPoint {

    Joint at();

    Object getComponent();

    <R> R proceed() throws Throwable;

}
