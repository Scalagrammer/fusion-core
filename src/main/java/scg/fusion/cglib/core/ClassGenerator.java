package scg.fusion.cglib.core;

import org.objectweb.asm.ClassVisitor;

public interface ClassGenerator {
    void generateClass(ClassVisitor v) throws Exception;
}
