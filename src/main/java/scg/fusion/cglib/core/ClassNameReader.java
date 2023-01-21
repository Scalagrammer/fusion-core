package scg.fusion.cglib.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.util.ArrayList;
import java.util.List;

public class ClassNameReader {

    private ClassNameReader() {
        throw new UnsupportedOperationException();
    }

    private static final ClassNameReader.EarlyExitException EARLY_EXIT = new ClassNameReader.EarlyExitException();

    private static class EarlyExitException extends RuntimeException {
    }

    public static String getClassName(ClassReader r) {
        return getClassInfo(r)[0];
    }

    public static String[] getClassInfo(ClassReader r) {
        final List array = new ArrayList();
        try {
            r.accept(new ClassVisitor(Constants.ASM_API, null) {
                public void visit(int version,
                                  int access,
                                  String name,
                                  String signature,
                                  String superName,
                                  String[] interfaces) {

                    array.add(name.replace('/', '.'));

                    if (superName != null) {
                        array.add(superName.replace('/', '.'));
                    }

                    for (String anInterface : interfaces) {
                        array.add(anInterface.replace('/', '.'));
                    }

                    throw EARLY_EXIT;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (ClassNameReader.EarlyExitException $) {
        }

        return (String[]) array.toArray(new String[]{});
    }
}
