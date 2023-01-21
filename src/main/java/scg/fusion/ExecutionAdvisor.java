package scg.fusion;

import scg.fusion.aop.ExecutionJoinPoint;

interface ExecutionAdvisor extends ExecutionPointcut, PrivilegedAdvisor {
    Object advise(ExecutionJoinPoint joinPoint);
}
