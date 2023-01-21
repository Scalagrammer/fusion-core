package scg.fusion.aop;

import scg.fusion.FieldJoint;

public interface AutowireJoinPoint extends JoinPoint {
    @Override
    FieldJoint at();
}
