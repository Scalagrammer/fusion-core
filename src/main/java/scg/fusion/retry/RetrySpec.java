package scg.fusion.retry;

import scg.fusion.MethodJoint;
import scg.fusion.annotation.Around;
import scg.fusion.annotation.Privileged;
import scg.fusion.annotation.Retryable;
import scg.fusion.aop.ExecutionJoinPoint;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static scg.fusion.annotation.Privileged.HIGHEST_PRIORITY;

public interface RetrySpec {

    @Around("@execution(scg.fusion.annotation.Retryable)")
    default Object aroundRetryable(ExecutionJoinPoint joinPoint) throws Throwable {

        MethodJoint joint = joinPoint.at();

        Retryable annotation = joint.getAnnotation(Retryable.class);

        int attempts = annotation.attempts();

        long interval = annotation.interval();

        Throwable executionCause = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable cause) {
            executionCause = cause;
        }

        while ((attempts -= 1) > 0) {

            parkNanos(MILLISECONDS.toNanos(interval));

            try {
                return joinPoint.proceed();
            } catch (Throwable cause) {
                executionCause = cause;
            }
        }

        throw executionCause;

    }
}
