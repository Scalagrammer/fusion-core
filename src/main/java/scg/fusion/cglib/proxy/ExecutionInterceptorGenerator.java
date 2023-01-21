package scg.fusion.cglib.proxy;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import scg.fusion.cglib.core.*;

import java.util.HashMap;

import java.util.List;
import java.util.Map;

class ExecutionInterceptorGenerator implements CallbackGenerator {

    public static final ExecutionInterceptorGenerator INSTANCE = new ExecutionInterceptorGenerator();

    static final String EMPTY_ARGS_NAME = "CGLIB$emptyArgs";
    static final String FIND_PROXY_NAME = "CGLIB$findMethodProxy";
    static final Class[] FIND_PROXY_TYPES = { Signature.class };

    private static final Type ABSTRACT_METHOD_ERROR =
            TypeUtils.parseType("AbstractMethodError");
    private static final Type METHOD =
            TypeUtils.parseType("java.lang.reflect.Method");
    private static final Type REFLECT_UTILS =
            TypeUtils.parseType("scg.fusion.cglib.core.ReflectUtils");
    private static final Type METHOD_PROXY =
            TypeUtils.parseType("scg.fusion.cglib.proxy.MethodProxy");
    private static final Type EXECUTION_INTERCEPTOR =
            TypeUtils.parseType("scg.fusion.cglib.proxy.ExecutionInterceptor");
    private static final Signature GET_DECLARED_METHODS =
            TypeUtils.parseSignature("java.lang.reflect.Method[] getDeclaredMethods()");
    private static final Signature GET_DECLARING_CLASS =
            TypeUtils.parseSignature("Class getDeclaringClass()");
    private static final Signature FIND_METHODS =
            TypeUtils.parseSignature("java.lang.reflect.Method[] findMethods(String[], java.lang.reflect.Method[])");
    private static final Signature MAKE_PROXY =
            new Signature("create", METHOD_PROXY, new Type[]{
                    Constants.TYPE_CLASS,
                    Constants.TYPE_CLASS,
                    Constants.TYPE_STRING,
                    Constants.TYPE_STRING,
                    Constants.TYPE_STRING
            });
    private static final Signature INTERCEPT =
            new Signature("intercept", Constants.TYPE_OBJECT, new Type[]{
                    Constants.TYPE_CLASS,
                    Constants.TYPE_OBJECT,
                    METHOD,
                    Constants.TYPE_OBJECT_ARRAY,
                    METHOD_PROXY
            });
    private static final Signature FIND_PROXY =
            new Signature(FIND_PROXY_NAME, METHOD_PROXY, new Type[]{ Constants.TYPE_SIGNATURE });
    private static final Signature TO_STRING =
            TypeUtils.parseSignature("String toString()");
    private static final Transformer METHOD_TO_CLASS = new Transformer(){
        public Object transform(Object value) {
            return ((MethodInfo)value).getClassInfo();
        }
    };
    private static final Signature CSTRUCT_SIGNATURE =
            TypeUtils.parseConstructor("String, String");
    private static final Signature GET_CALLER_CLASS =
            new Signature("getCallerClass", Constants.TYPE_CLASS, Type.INT_TYPE);

    private String getMethodField(Signature impl) {
        return impl.getName() + "$Method";
    }
    private String getMethodProxyField(Signature impl) {
        return impl.getName() + "$Proxy";
    }

    public void generate(ClassEmitter ce, Context context, List methods) {
        Map sigMap = new HashMap();
        for (Object o : methods) {
            MethodInfo method = (MethodInfo) o;
            Signature sig = method.getSignature();
            Signature impl = context.getImplSignature(method);

            String methodField = getMethodField(impl);
            String methodProxyField = getMethodProxyField(impl);

            sigMap.put(sig.toString(), methodProxyField);
            ce.declare_field(Constants.PRIVATE_FINAL_STATIC, methodField, METHOD, null);
            ce.declare_field(Constants.PRIVATE_FINAL_STATIC, methodProxyField, METHOD_PROXY, null);
            ce.declare_field(Constants.PRIVATE_FINAL_STATIC, EMPTY_ARGS_NAME, Constants.TYPE_OBJECT_ARRAY, null);
            CodeEmitter e;

            // access method
            e = ce.begin_method(Constants.ACC_FINAL,
                    impl,
                    method.getExceptionTypes());
            superHelper(e, method, context);
            e.return_value();
            e.end_method();

            // around method
            e = context.beginMethod(ce, method);
            Label nullInterceptor = e.make_label();
            context.emitCallback(e, context.getIndex(method));
            e.dup();
            e.ifnull(nullInterceptor);

            callSide(e); // TODO: optimize for 'within' guard usage (apply if necessary)

            e.load_this(); // enhancer

            e.getfield(methodField);

            if (sig.getArgumentTypes().length == 0) {
                e.getfield(EMPTY_ARGS_NAME);
            } else {
                e.create_arg_array();
            }

            e.getfield(methodProxyField);
            e.invoke_interface(EXECUTION_INTERCEPTOR, INTERCEPT);
            e.unbox_or_zero(sig.getReturnType());
            e.return_value();

            e.mark(nullInterceptor);
            superHelper(e, method, context);
            e.return_value();
            e.end_method();
        }
        generateFindProxy(ce, sigMap);
    }

    private static void callSide(CodeEmitter e) {
        e.push(2);
        e.invoke_static(Constants.TYPE_REFLECTION, GET_CALLER_CLASS, false);
    }

    private static void superHelper(CodeEmitter e, MethodInfo method, Context context)
    {
        if (TypeUtils.isAbstract(method.getModifiers())) {
            e.throw_exception(ABSTRACT_METHOD_ERROR, method.toString() + " is abstract" );
        } else {
            e.load_this();
            context.emitLoadArgsAndInvoke(e, method);
        }
    }

    public void generateStatic(CodeEmitter e, Context context, List methods) throws Exception {
        /* generates:
           static {
             Class thisClass = Class.forName("NameOfThisClass");
             Class cls = Class.forName("java.lang.Object");
             String[] sigs = new String[]{ "toString", "()Ljava/lang/String;", ... };
             Method[] methods = cls.getDeclaredMethods();
             methods = ReflectUtils.findMethods(sigs, methods);
             METHOD_0 = methods[0];
             CGLIB$ACCESS_0 = MethodProxy.create(cls, thisClass, "()Ljava/lang/String;", "toString", "CGLIB$ACCESS_0");
             ...
           }
        */

        e.push(0);
        e.newarray();
        e.putfield(EMPTY_ARGS_NAME);

        Local thisclass = e.make_local();
        Local declaringclass = e.make_local();
        EmitUtils.load_class_this(e);
        e.store_local(thisclass);

        Map methodsByClass = CollectionUtils.bucket(methods, METHOD_TO_CLASS);
        for (Object o : methodsByClass.keySet()) {
            ClassInfo classInfo = (ClassInfo) o;

            List classMethods = (List) methodsByClass.get(classInfo);
            e.push(2 * classMethods.size());
            e.newarray(Constants.TYPE_STRING);
            for (int index = 0; index < classMethods.size(); index++) {
                MethodInfo method = (MethodInfo) classMethods.get(index);
                Signature sig = method.getSignature();
                e.dup();
                e.push(2 * index);
                e.push(sig.getName());
                e.aastore();
                e.dup();
                e.push(2 * index + 1);
                e.push(sig.getDescriptor());
                e.aastore();
            }

            EmitUtils.load_class(e, classInfo.getType());
            e.dup();
            e.store_local(declaringclass);
            e.invoke_virtual(Constants.TYPE_CLASS, GET_DECLARED_METHODS);
            e.invoke_static(REFLECT_UTILS, FIND_METHODS);

            for (int index = 0; index < classMethods.size(); index++) {
                MethodInfo method = (MethodInfo) classMethods.get(index);
                Signature sig = method.getSignature();
                Signature impl = context.getImplSignature(method);
                e.dup();
                e.push(index);
                e.array_load(METHOD);
                e.putfield(getMethodField(impl));

                e.load_local(declaringclass);
                e.load_local(thisclass);
                e.push(sig.getDescriptor());
                e.push(sig.getName());
                e.push(impl.getName());
                e.invoke_static(METHOD_PROXY, MAKE_PROXY);
                e.putfield(getMethodProxyField(impl));
            }
            e.pop();
        }
    }

    public void generateFindProxy(ClassEmitter ce, final Map sigMap) {
        final CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC | Constants.ACC_STATIC,
                FIND_PROXY,
                null);
        e.load_arg(0);
        e.invoke_virtual(Constants.TYPE_OBJECT, TO_STRING);
        ObjectSwitchCallback callback = new ObjectSwitchCallback() {
            public void processCase(Object key, Label end) {
                e.getfield((String)sigMap.get(key));
                e.return_value();
            }
            public void processDefault() {
                e.aconst_null();
                e.return_value();
            }
        };
        EmitUtils.string_switch(e,
                (String[])sigMap.keySet().toArray(new String[0]),
                Constants.SWITCH_STYLE_HASH,
                callback);
        e.end_method();
    }
}

