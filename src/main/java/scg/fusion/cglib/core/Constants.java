package scg.fusion.cglib.core;

import org.objectweb.asm.Type;

public interface Constants extends org.objectweb.asm.Opcodes {

    /* Indicates the ASM API version that is used throughout cglib */
    int ASM_API = AsmApi.value();

    Class[] EMPTY_CLASS_ARRAY = {};
    Type[] TYPES_EMPTY = {};

    Signature SIG_STATIC =
            TypeUtils.parseSignature("void <clinit>()");

    Type TYPE_OBJECT_ARRAY = TypeUtils.parseType("Object[]");
    Type TYPE_CLASS_ARRAY = TypeUtils.parseType("Class[]");
    Type TYPE_STRING_ARRAY = TypeUtils.parseType("String[]");
    Type TYPE_STACK_TRACE_ELEMENT_ARRAY = TypeUtils.parseType("StackTraceElement[]");

    Type TYPE_OBJECT = TypeUtils.parseType("Object");
    Type TYPE_REFLECTION = TypeUtils.parseType("sun.reflect.Reflection");
    Type TYPE_CLASS = TypeUtils.parseType("Class");
    Type TYPE_CLASS_LOADER = TypeUtils.parseType("ClassLoader");
    Type TYPE_CHARACTER = TypeUtils.parseType("Character");
    Type TYPE_BOOLEAN = TypeUtils.parseType("Boolean");
    Type TYPE_DOUBLE = TypeUtils.parseType("Double");
    Type TYPE_FLOAT = TypeUtils.parseType("Float");
    Type TYPE_LONG = TypeUtils.parseType("Long");
    Type TYPE_INTEGER = TypeUtils.parseType("Integer");
    Type TYPE_SHORT = TypeUtils.parseType("Short");
    Type TYPE_BYTE = TypeUtils.parseType("Byte");
    Type TYPE_NUMBER = TypeUtils.parseType("Number");
    Type TYPE_STRING = TypeUtils.parseType("String");
    Type TYPE_THROWABLE = TypeUtils.parseType("Throwable");
    Type TYPE_BIG_INTEGER = TypeUtils.parseType("java.math.BigInteger");
    Type TYPE_BIG_DECIMAL = TypeUtils.parseType("java.math.BigDecimal");
    Type TYPE_STRING_BUFFER = TypeUtils.parseType("StringBuffer");
    Type TYPE_RUNTIME_EXCEPTION = TypeUtils.parseType("RuntimeException");
    Type TYPE_ERROR = TypeUtils.parseType("Error");
    Type TYPE_SYSTEM = TypeUtils.parseType("System");
    Type TYPE_SIGNATURE = TypeUtils.parseType("scg.fusion.cglib.core.Signature");
    Type TYPE_TYPE = Type.getType(Type.class);

    String CONSTRUCTOR_NAME = "<init>";
    String STATIC_NAME = "<clinit>";
    String SOURCE_FILE = "<generated>";
    String SUID_FIELD_NAME = "serialVersionUID";

    int PRIVATE_FINAL_STATIC = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;

    int SWITCH_STYLE_TRIE = 0;
    int SWITCH_STYLE_HASH = 1;
    int SWITCH_STYLE_HASHONLY = 2;
}
