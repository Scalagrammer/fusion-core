package scg.fusion;

import org.objectweb.asm.*;
import scg.fusion.annotation.Autowired;
import scg.fusion.annotation.OnTheFly;
import scg.fusion.annotation.Property;
import scg.fusion.exceptions.DumpWritingException;
import scg.fusion.exceptions.IllegalContractException;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.io.File.separatorChar;
import static java.lang.String.format;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;
import static scg.fusion.Utils.*;
import static scg.fusion.cglib.core.ReflectUtils.defineClass;

abstract class OnTheFlyClass {

    private static final ClassLoader onTheFlyLoader = new ClassLoader() {};

    protected static final Class<?> MagicAccessorImpl = getMagicAccessorImplClass();

    protected static final int ACC_PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC;
    protected static final int ACC_PRIVATE_STATIC = ACC_PRIVATE | ACC_STATIC;
    protected static final int ACC_PUBLIC_FINAL = ACC_PUBLIC | ACC_FINAL;
    protected static final int ACC_PRIVATE_FINAL = ACC_PRIVATE | ACC_FINAL;

    protected static final String ctor = "<init>";
    protected static final String clinit = "<clinit>";

    private final Map<String, String> fieldDescriptorTable = new HashMap<>();

    private final List<Consumer<ClassVisitor>> fields       = new ArrayList<>();
    private final List<Consumer<ClassVisitor>> methods      = new ArrayList<>();
    private final List<Consumer<ClassVisitor>> annotations  = new ArrayList<>();

    protected final String className;
    protected final String descriptor;
    protected final String slashedClassName;

    protected final Class<?>   superClass;
    protected final Class<?>[] interfaces;

    private static String dumpCodeLocation;
    private static boolean dumpCodeEnabled;
    private static Constructor<? extends ClassVisitor> traceCtor;

    static {
        dumpCodeLocation = System.getProperty(ON_THE_FLY_DUMP_CODE_LOCATION_PROPERTY_NAME);
        if (nonNull(dumpCodeLocation)) {
            System.err.printf("Fusion on the fly damp enabled, writing to '%s'\n", dumpCodeLocation);
            try {
                traceCtor = Class.forName(TRACE_CLASS_VISITOR_CLASS_NAME).asSubclass(ClassVisitor.class).getConstructor(PrintWriter.class);

                File dumpRoot = new File(dumpCodeLocation);

                deleteIfExists(dumpRoot);

                dumpRoot.mkdir();

                dumpCodeEnabled = nonNull(traceCtor);
            } catch (Throwable $) {}
        }
    }

    protected OnTheFlyClass(String classNamePrefix, Class<?> superClass, Class<?>... interfaces) {

        if (isNull(classNamePrefix) || classNamePrefix.isEmpty()) {
            throw new IllegalContractException("on the fly class name prefix cannot be empty or null");
        }

        this.superClass       = superClass;
        this.interfaces       = interfaces;
        this.className        = prepareClassName(classNamePrefix);
        this.slashedClassName = className.replace('.', separatorChar);
        this.descriptor       = format("L%s;", slashedClassName);

        this.emit();

    }

    protected abstract void emit();

    protected final void mark(Class<? extends Annotation> annotationType) {
        mark(annotationType, emptyMap());
    }

    protected final void mark(Class<? extends Annotation> annotationType, Object value) {
        mark(annotationType, singletonMap(VALUE, value));
    }

    protected final void mark(Class<? extends Annotation> annotationType, Map<String, Object> attributes) {

        Consumer<ClassVisitor> annotation = classVisitor -> {

            AnnotationVisitor annotationVisitor = classVisitor.visitAnnotation(Type.getDescriptor(annotationType), true);

            for (String attribute : attributes.keySet()) {
                annotationVisitor.visit(attribute, attributes.get(attribute));
            }

            annotationVisitor.visitEnd();

        };

        annotations.add(annotation);

    }

    protected final void field(int access, Class<?> type, String name) {

        fieldDescriptorTable.put(name, Type.getDescriptor(type));

        fields.add(classVisitor -> classVisitor.visitField(access, name, Type.getDescriptor(type), null, null).visitEnd());

    }

    protected final void autowired(Class<?> type, String fieldName) {
        field(ACC_PRIVATE, type, fieldName, Autowired.class);
    }

    protected final void property(Class<?> type, String fieldName, String property) {
        field(ACC_PRIVATE, type, fieldName, Property.class, singletonMap("value", property));
    }

    protected final void field(int access, Class<?> type, String name, Class<? extends Annotation> annotationType) {
        field(access, type, name, annotationType, emptyMap());
    }

    protected final void field(int access, Class<?> type, String name, Class<? extends Annotation> annotationType, Object value) {
        field(access, type, name, annotationType, singletonMap(VALUE, value));
    }

    protected final void field(int access, Class<?> type, String name, Class<? extends Annotation> annotationType, Map<String, Object> attributes) {

        fieldDescriptorTable.put(name, Type.getDescriptor(type));

        Consumer<ClassVisitor> field = classVisitor -> {

            FieldVisitor fieldVisitor = classVisitor.visitField(access, name, Type.getDescriptor(type), null, null);

            AnnotationVisitor annotationVisitor = fieldVisitor.visitAnnotation(Type.getDescriptor(annotationType), true);

            for (String attribute : attributes.keySet()) {
                annotationVisitor.visit(attribute, attributes.get(attribute));
            }

            annotationVisitor.visitEnd();


            fieldVisitor.visitEnd();

        };

        fields.add(field);

    }

    protected final void utl_ctor() {
        ctor(ACC_PRIVATE)
                .aload_0()
                .invoke_special(superClass, void.class, ctor)
                .nеw(UnsupportedOperationException.class)
                .dup()
                .invoke_special(UnsupportedOperationException.class, void.class, ctor)
                .athrow()
                .rеturn();
    }

    protected final void null_ctor(int access) {
        ctor(access)
                .aload_0()
                .invoke_special(superClass, void.class, ctor)
                .rеturn();
    }

    protected final void null_ctor() {
        null_ctor(ACC_PUBLIC);
    }

    protected final MethodBody ctor(int access, Class<?>... parameterTypes) {
        return method(access, void.class, ctor, parameterTypes);
    }

    protected final MethodBody ctor(Class<?>... parameterTypes) {
        return method(ACC_PUBLIC, void.class, ctor, parameterTypes);
    }

    protected final MethodBody method(int access, Class<?> returnType, String methodName, Class<?>... parameterTypes) {

        String methodDescriptor = MethodType.methodType(returnType, parameterTypes).toMethodDescriptorString();

        MethodBody methodBody = new MethodBody(access, methodName, methodDescriptor, fieldDescriptorTable, className);

        methods.add(methodBody);

        return methodBody;

    }

    public final Class<?> load() throws Exception {

        byte[] bytes = toBytecode();

        if (dumpCodeEnabled) {
            writeDump(bytes, slashedClassName);
        }

        return defineClass(className, bytes, onTheFlyLoader);

    }

    public final <T> Class<? extends T> loadAs(Class<T> superClass) throws Exception {
        return load().asSubclass(superClass);
    }

    public final byte[] toBytecode() {

        ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);

        classWriter.visit(V1_8, ACC_PUBLIC, slashedClassName, descriptor, Type.getInternalName(superClass), Stream.of(interfaces).map(Type::getInternalName).toArray(String[]::new));

        classWriter.visitSource(_GENERATED_, _GENERATED_);

        for (Consumer<ClassVisitor> annotation : annotations) {
            annotation.accept(classWriter);
        }

        for (Consumer<ClassVisitor> field : fields) {
            field.accept(classWriter);
        }

        for (Consumer<ClassVisitor> method : methods) {
            method.accept(classWriter);
        }

        classWriter.visitAnnotation(Type.getDescriptor(OnTheFly.class), true).visitEnd();

        classWriter.visitEnd();

        return classWriter.toByteArray();

    }

    private String prepareClassName(String prefix) {
        return format("%s$$%s", prefix, Integer.toHexString(hashCode()));
    }

    private static void writeDump(byte[] bytecode, String slashedClassName) {
        try {
            File dumpRoot = new File(dumpCodeLocation);

            File dumpFile = new File(dumpRoot, format("%s.%s", slashedClassName, ASM));

            try (OutputStream asmFileOutput = new BufferedOutputStream(new FileOutputStream(dumpFile))) {

                ClassReader reader = new ClassReader(bytecode);

                PrintWriter printer = new PrintWriter(new OutputStreamWriter(asmFileOutput));

                reader.accept(traceCtor.newInstance(printer), 0);

                printer.flush();
            }

        } catch (Exception cause) {
            throw new DumpWritingException(cause);
        }
    }
}