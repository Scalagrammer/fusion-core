package scg.fusion.cglib.core;

import org.objectweb.asm.Type;

public interface ProcessArrayCallback {
    void processElement(Type type);
}
