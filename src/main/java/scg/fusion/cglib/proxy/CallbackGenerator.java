package scg.fusion.cglib.proxy;

import scg.fusion.cglib.core.ClassEmitter;
import scg.fusion.cglib.core.CodeEmitter;
import scg.fusion.cglib.core.MethodInfo;
import scg.fusion.cglib.core.Signature;

import java.util.List;

interface CallbackGenerator
{
    void generate(ClassEmitter ce, Context context, List methods) throws Exception;
    void generateStatic(CodeEmitter e, Context context, List methods) throws Exception;

    interface Context
    {
        ClassLoader getClassLoader();
        CodeEmitter beginMethod(ClassEmitter ce, MethodInfo method);
        int getOriginalModifiers(MethodInfo method);
        int getIndex(MethodInfo method);
        void emitCallback(CodeEmitter ce, int index);
        Signature getImplSignature(MethodInfo method);
        void emitLoadArgsAndInvoke(CodeEmitter e, MethodInfo method);
    }
}
