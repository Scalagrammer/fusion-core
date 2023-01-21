package scg.fusion;

import org.objectweb.asm.*;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Type.*;
import static org.objectweb.asm.Type.LONG_TYPE;
import static scg.fusion.OnTheFlyClass.ctor;
import static scg.fusion.Utils.*;
import static scg.fusion.Utils.BOOLEAN;
import static scg.fusion.Utils.BYTE;
import static scg.fusion.Utils.CHAR;
import static scg.fusion.Utils.DOUBLE;
import static scg.fusion.Utils.FLOAT;
import static scg.fusion.Utils.INT;
import static scg.fusion.Utils.LONG;
import static scg.fusion.Utils.SHORT;

final class MethodBody implements Consumer<ClassVisitor> {

    private final List<Consumer<MethodVisitor>> instructions = new ArrayList<>();

    private final Map<LabelKey, Label> labels = new HashMap<>();

    private final Map<String, String> fieldDescriptorTable;

    private final int access;
    private final String className;
    private final String methodName;
    private final String descriptor;

    MethodBody(int access, String methodName, String descriptor, Map<String, String> fieldDescriptorTable, String className) {
        this.access = access;
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.fieldDescriptorTable = fieldDescriptorTable;
    }

    @Override
    public void accept(ClassVisitor classVisitor) {

        MethodVisitor methodVisitor = classVisitor.visitMethod(access, methodName, descriptor, null, null);

        methodVisitor.visitCode();

        for (Consumer<MethodVisitor> instruction : instructions) {
            instruction.accept(methodVisitor);
        }
    }

    public MethodBody aaload() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(AALOAD));
        return this;
    }

    public MethodBody line_number(int number, Label label) {
        instructions.add(methodVisitor -> methodVisitor.visitLineNumber(number, label));
        return this;
    }

    // #58
    public MethodBody pop_2() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(POP2));
        return this;
    }

    // #57
    public MethodBody pop() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(POP));
        return this;
    }

    // #59
    public MethodBody dup() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP));
        return this;
    }

    public MethodBody icons_0() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ICONST_0));
        return this;
    }

    public MethodBody icons_1() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ICONST_1));
        return this;
    }

    public MethodBody bipush(int i) {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(BIPUSH, i));
        return this;
    }

    public MethodBody sipush(int i) {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(SIPUSH, i));
        return this;
    }

    public MethodBody istore(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(ISTORE, index));
        return this;
    }

    public MethodBody iconst_0() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ICONST_0));
        return this;
    }

    public MethodBody iconst_1() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ICONST_1));
        return this;
    }

    public MethodBody ior() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(IOR));
        return this;
    }

    // #b6
    public MethodBody invoke_virtual(Class<?> owner, Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(owner), methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    // on self
    public MethodBody invoke_virtual(Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKEVIRTUAL, className, methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    // #b8
    public MethodBody invoke_static(Class<?> owner, Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(owner), methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    public MethodBody invoke_static(Method method) {
        return this.invoke_static(method.getDeclaringClass(), method.getReturnType(), method.getName(), method.getParameterTypes());
    }

    // on self
    public MethodBody invoke_static(Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKESTATIC, className, methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    // #b9
    public MethodBody invoke_interface(Class<?> owner, Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(owner), methodName, getMethodTypeDescriptor(returnType, parameterTypes), true));
        return this;
    }

    public MethodBody invoke_interface(Method method) {
        return this.invoke_interface(method.getDeclaringClass(), method.getReturnType(), method.getName(), method.getParameterTypes());
    }

    public MethodBody invoke_virtual(Method method) {
        return this.invoke_virtual(method.getDeclaringClass(), method.getReturnType(), method.getName(), method.getParameterTypes());
    }

    // on self
    public MethodBody invoke_interface(Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKEINTERFACE, className, methodName, getMethodTypeDescriptor(returnType, parameterTypes), true));
        return this;
    }

    // #b7
    public MethodBody invoke_special(Class<?> owner, Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(owner), methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    public MethodBody invoke_special(Constructor<?> special) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(special.getDeclaringClass()), ctor, getMethodTypeDescriptor(void.class, special.getParameterTypes()), false));
        return this;
    }

    // on self
    public MethodBody invoke_special(Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        instructions.add(methodVisitor -> methodVisitor.visitMethodInsn(INVOKESPECIAL, className, methodName, getMethodTypeDescriptor(returnType, parameterTypes), false));
        return this;
    }

    // #2d
    public MethodBody aload_3() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 3));
        return this;
    }

    public MethodBody aload_4() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 4));
        return this;
    }

    public MethodBody aload_5() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 5));
        return this;
    }

    public MethodBody aload_6() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 6));
        return this;
    }

    // #2c
    public MethodBody aload_2() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 2));
        return this;
    }

    // #2b
    public MethodBody aload_1() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 1));
        return this;
    }

    // #2a
    public MethodBody aload_0() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ALOAD, 0));
        return this;
    }

    // #00
    public MethodBody nop() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(NOP));
        return this;
    }

    // #be
    public MethodBody array_length() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ARRAYLENGTH));
        return this;
    }

    // #5f
    public MethodBody swap() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(SWAP));
        return this;
    }

    // #5e
    public MethodBody dup_2_x2() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP2_X2));
        return this;
    }

    // #5e
    public MethodBody dup_2_x1() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP2_X1));
        return this;
    }

    // #5c
    public MethodBody dup_2() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP2));
        return this;
    }

    // #5b
    public MethodBody dup_x2() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP_X2));
        return this;
    }

    // #5a
    public MethodBody dup_x1() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(DUP_X1));
        return this;
    }

    // #01
    public MethodBody aconst_null() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ACONST_NULL));
        return this;
    }

    // #bf
    public MethodBody athrow() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(ATHROW));
        return this;
    }

    // #19
    public MethodBody aload(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(ALOAD, index));
        return this;
    }

    public MethodBody iload_1() {
        return iload(1);
    }

    public MethodBody iload(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(ILOAD, index));
        return this;
    }

    public MethodBody lload(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(LLOAD, index));
        return this;
    }

    public MethodBody dload(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(DLOAD, index));
        return this;
    }

    public MethodBody fload(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitVarInsn(FLOAD, index));
        return this;
    }


    // #b0
    public void arеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    // return


    public void lrеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(LRETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    public void irеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    public void frеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(FRETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    public void drеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(DRETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    // #b1
    public void rеturn() {
        instructions.add(methodVisitor -> {
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
            methodVisitor.visitEnd();
        });
    }

    public void rеturn(Class<?> returnType) {
        if (returnType == void.class) {
            instructions.add(methodVisitor -> {
                methodVisitor.visitInsn(RETURN);
                methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                methodVisitor.visitEnd();
            });
        } else if (returnType.isPrimitive()) {
            switch (returnType.getName()) {
                case BOOLEAN:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), BOOLEAN_VALUE, getMethodDescriptor(BOOLEAN_TYPE), false);
                        methodVisitor.visitInsn(IRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case SHORT:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), SHORT_VALUE, getMethodDescriptor(SHORT_TYPE), false);
                        methodVisitor.visitInsn(IRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case BYTE:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), BYTE_VALUE, getMethodDescriptor(BYTE_TYPE), false);
                        methodVisitor.visitInsn(IRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case CHAR:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Character.class), CHAR_VALUE, getMethodDescriptor(CHAR_TYPE), false);
                        methodVisitor.visitInsn(IRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case INT:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), INT_VALUE, getMethodDescriptor(INT_TYPE), false);
                        methodVisitor.visitInsn(IRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case DOUBLE:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), DOUBLE_VALUE, getMethodDescriptor(DOUBLE_TYPE), false);
                        methodVisitor.visitInsn(DRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case FLOAT:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), FLOAT_VALUE, getMethodDescriptor(FLOAT_TYPE), false);
                        methodVisitor.visitInsn(FRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                case LONG:
                    instructions.add(methodVisitor -> {
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), LONG_VALUE, getMethodDescriptor(LONG_TYPE), false);
                        methodVisitor.visitInsn(LRETURN);
                        methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                        methodVisitor.visitEnd();
                    });
                    break;
                default:
                    break;
            }
        } else {
            instructions.add(methodVisitor -> {
                methodVisitor.visitInsn(ARETURN);
                methodVisitor.visitMaxs(COMPUTE_MAXS, COMPUTE_MAXS);
                methodVisitor.visitEnd();
            });
        }
    }

    public MethodBody f_same(int nLocal, Object[] local, int nStack, Object[] stack) {
        instructions.add(methodVisitor -> methodVisitor.visitFrame(F_SAME, nLocal, local, nStack, stack));
        return this;
    }

    public MethodBody visit(Consumer<MethodVisitor> acceptor) {
        instructions.add(acceptor);
        return this;
    }

    public MethodBody also(Consumer<MethodBody> acceptor) {
        acceptor.accept(this);
        return this;
    }

    public MethodBody iand() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(IAND));
        return this;
    }

    public MethodBody ifne(Label label) {
        instructions.add(methodVisitor -> methodVisitor.visitJumpInsn(IFNE, label));
        return this;
    }

    public MethodBody ifeq(Label label) {
        instructions.add(methodVisitor -> methodVisitor.visitJumpInsn(IFEQ, label));
        return this;
    }

    // #3a
    public MethodBody astore(int index) {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ASTORE, index));
        return this;
    }

    // 4b
    public MethodBody astore_0() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ASTORE, 0));
        return this;
    }

    // 4c
    public MethodBody astore_1() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ASTORE, 1));
        return this;
    }

    // 4d
    public MethodBody astore_2() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ASTORE, 2));
        return this;
    }

    // 4e
    public MethodBody astore_3() {
        instructions.add(methodVisitor -> methodVisitor.visitIntInsn(ASTORE, 3));
        return this;
    }

    // #c0
    public MethodBody check_cast(Class<?> castType) {
        instructions.add(methodVisitor -> methodVisitor.visitTypeInsn(CHECKCAST, Type.getInternalName(castType)));
        return this;
    }

    // #bb
    public MethodBody nеw(Class<?> type) {
        instructions.add(methodVisitor -> methodVisitor.visitTypeInsn(NEW, Type.getInternalName(type)));
        return this;
    }

    // #b2
    public MethodBody get_static(Class<?> owner, String staticName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(GETSTATIC, Type.getInternalName(owner), staticName, getFieldDescriptor(owner, staticName)));
        return this;
    }

    // #b2
    public MethodBody get_static(String staticName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(GETSTATIC, className, staticName, fieldDescriptorTable.get(staticName)));
        return this;
    }


    public MethodBody get_field(Class<?> owner, String fieldName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(GETFIELD, Type.getInternalName(owner), fieldName, getFieldDescriptor(owner, fieldName)));
        return this;
    }

    // #b4
    public MethodBody get_field(String fieldName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(GETFIELD, className, fieldName, fieldDescriptorTable.get(fieldName)));
        return this;
    }

    // #b3
    public MethodBody put_static(Class<?> owner, String staticName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(PUTSTATIC, Type.getInternalName(owner), staticName, getFieldDescriptor(owner, staticName)));
        return this;
    }

    // (#b5)
    public MethodBody put_field(String fieldName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(PUTFIELD, className, fieldName, fieldDescriptorTable.get(fieldName)));
        return this;
    }

    // #b5
    public MethodBody put_field(Class<?> owner, String fieldName) {
        instructions.add(methodVisitor -> methodVisitor.visitFieldInsn(PUTFIELD, Type.getInternalName(owner), fieldName, getFieldDescriptor(owner, fieldName)));
        return this;
    }

    // #12
    public MethodBody ldc(Object constant) {
        instructions.add(methodVisitor -> methodVisitor.visitLdcInsn(constant));
        return this;
    }

    public MethodBody ldc(Class<?> constant) {
        return this.ldc(Type.getType(constant));
    }

    // #84
    public MethodBody iinc(int index, int increment) {
        instructions.add(methodVisitor -> methodVisitor.visitIincInsn(index, increment));
        return this;
    }

    // long

    // #88
    public MethodBody l2i() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(L2I));
        return this;
    }

    // #89
    public MethodBody l2f() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(L2F));
        return this;
    }

    // #8a
    public MethodBody l2d() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(L2D));
        return this;
    }

    // integer

    // #93
    public MethodBody i2s() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(I2S));
        return this;
    }

    // #86
    public MethodBody i2f() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(I2L));
        return this;
    }

    // #87
    public MethodBody i2d() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(I2D));
        return this;
    }

    // #92
    public MethodBody i2c() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(I2C));
        return this;
    }

    // #91
    public MethodBody i2b() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(I2B));
        return this;
    }

    // float

    // #8c
    public MethodBody f2l() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(F2L));
        return this;
    }

    // #8b
    public MethodBody f2i() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(F2I));
        return this;
    }

    // #8d
    public MethodBody f2d() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(F2D));
        return this;
    }

    // double

    // 8f
    public MethodBody d2l() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(D2L));
        return this;
    }

    public MethodBody frame(int type) {
        instructions.add(methodVisitor -> methodVisitor.visitFrame(type, 0, null, 0, null));
        return this;
    }

    // #8e

    public MethodBody d2i() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(D2I));
        return this;
    }
    // #90

    public MethodBody d2f() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(D2F));
        return this;
    }
    // #a7

    public MethodBody ifnonnull_jump(LabelKey label) {

        Label jumpLabel = new Label();

        labels.put(label, jumpLabel);

        instructions.add(methodVisitor -> methodVisitor.visitJumpInsn(IFNONNULL, jumpLabel));

        return this;
    }

    public MethodBody label(LabelKey label) {

        Label jumpLabel = labels.get(label);

        instructions.add(methodVisitor -> methodVisitor.visitLabel(jumpLabel));

        return this;

    }

    public MethodBody anewarray(Class<?> arrayType) {
        instructions.add(methodVisitor -> methodVisitor.visitTypeInsn(ANEWARRAY, Type.getDescriptor(arrayType)));
        return this;
    }

    public MethodBody aastore() {
        instructions.add(methodVisitor -> methodVisitor.visitInsn(AASTORE));
        return this;
    }

    private static String getFieldDescriptor(Class<?> owner, String fieldName) {
        try {
            return Type.getDescriptor(owner.getDeclaredField(fieldName).getType());
        } catch (NoSuchFieldException cause) {
            throw new RuntimeException("Cannot find field", cause);
        }
    }

    private static String getMethodTypeDescriptor(Class<?> returnType, Class<?>... parameterTypes) {
        return MethodType.methodType(returnType, parameterTypes).toMethodDescriptorString();
    }

    public MethodBody invoke_instance(Method by) {

        Class<?> declaringClass = by.getDeclaringClass();

        if (declaringClass.isInterface()) {
            invoke_interface(declaringClass, by.getReturnType(), by.getName(), by.getParameterTypes());
        } else {
            invoke_virtual(declaringClass, by.getReturnType(), by.getName(), by.getParameterTypes());
        }

        return this;

    }

}
