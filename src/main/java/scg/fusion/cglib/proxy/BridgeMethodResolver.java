package scg.fusion.cglib.proxy;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import scg.fusion.cglib.core.Constants;
import scg.fusion.cglib.core.Signature;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

class BridgeMethodResolver {

    private final Map<Class<?>, Set<Signature>> declToBridge;
    private final ClassLoader classLoader;

    public BridgeMethodResolver(Map declToBridge, ClassLoader classLoader) {
        this.declToBridge = declToBridge;
        this.classLoader = classLoader;
    }

    /**
     * Finds all bridge methods that are being called with invokespecial &
     * returns them.
     */
    public Map<Signature, Signature> resolveAll() {

        Map resolved = new HashMap();

        for (Map.Entry<Class<?>, Set<Signature>> entry : declToBridge.entrySet()) {

            Class<?> owner = entry.getKey();
            Set<Signature> bridges = entry.getValue();

            try {
                InputStream is = classLoader.getResourceAsStream(owner.getName().replace('.', '/') + ".class");

                if (is == null) {
                    return resolved;
                }

                try {
                    new ClassReader(is).accept(new BridgedFinder(bridges, resolved), SKIP_FRAMES | SKIP_DEBUG);
                } finally {
                    is.close();
                }
            } catch (IOException $) {
            }
        }

        return resolved;
    }

    private static class BridgedFinder extends ClassVisitor {

        private Map<Signature, Signature> resolved;
        private Set<Signature> eligibleMethods;

        private Signature currentMethod = null;

        BridgedFinder(Set<Signature> eligibleMethods, Map<Signature, Signature> resolved) {
            super(Constants.ASM_API);
            this.resolved = resolved;
            this.eligibleMethods = eligibleMethods;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

            Signature sig = new Signature(name, desc);

            if (eligibleMethods.remove(sig)) {

                currentMethod = sig;

                return new MethodVisitor(Constants.ASM_API) {
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                        if ((opcode == INVOKESPECIAL || (isInterface && opcode == INVOKEINTERFACE)) && currentMethod != null) {

                            Signature target = new Signature(name, desc);
                            // If the target signature is the same as the current,
                            // we shouldn't change our bridge becaues invokespecial
                            // is the only way to make progress (otherwise we'll
                            // get infinite recursion).  This would typically
                            // only happen when a bridge method is created to widen
                            // the visibility of a superclass' method.
                            if (!target.equals(currentMethod)) {
                                resolved.put(currentMethod, target);
                            }

                            currentMethod = null;

                        }
                    }
                };

            } else {
                return null;
            }
        }
    }
}

