package scg.fusion.cglib.core;

import org.objectweb.asm.Type;

public interface Customizer extends KeyFactoryCustomizer {
    void customize(CodeEmitter e, Type type);
}
