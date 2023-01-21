package scg.fusion.cglib.core;

import org.objectweb.asm.Type;

public interface FieldTypeCustomizer extends KeyFactoryCustomizer {
    /**
     * Customizes {@code this.FIELD_0 = ?} assignment in key constructor
     * @param e code emitter
     * @param index parameter index
     * @param type parameter type
     */
    void customize(CodeEmitter e, int index, Type type);

    /**
     * Computes type of field for storing given parameter
     * @param index parameter index
     * @param type parameter type
     */
    Type getOutType(int index, Type type);
}

