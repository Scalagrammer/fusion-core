package scg.fusion.cglib.transform;

import org.objectweb.asm.ClassVisitor;
import scg.fusion.cglib.core.Constants;

public abstract class ClassTransformer extends ClassVisitor {
    public ClassTransformer() {
        super(Constants.ASM_API);
    }
    public ClassTransformer(int opcode) {
        super(opcode);
    }
    public abstract void setTarget(ClassVisitor target);
}
