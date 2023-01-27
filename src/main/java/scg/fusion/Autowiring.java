package scg.fusion;

import java.util.Map;

public interface Autowiring {
    Map<Joint, AutowiringHook> by(String pointcut, Object...args);
}
