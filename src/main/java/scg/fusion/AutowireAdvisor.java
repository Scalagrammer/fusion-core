package scg.fusion;

import scg.fusion.aop.AutowireJoinPoint;

import static java.lang.Integer.compare;

public interface AutowireAdvisor extends PrivilegedAdvisor {
    Object advise(AutowireJoinPoint joinPoint);
}
