package scg.fusion.aop;

import scg.fusion.Joint;

public interface JoinPoint {

    Joint dissect();

    Object getComponent();

    <R> R proceed() throws Throwable;

}
