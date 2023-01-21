package scg.fusion.cglib.core;

import org.objectweb.asm.Label;

public interface ProcessSwitchCallback {
    void processCase(int key, Label end) throws Exception;
    void processDefault() throws Exception;
}
