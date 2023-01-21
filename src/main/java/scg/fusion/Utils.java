package scg.fusion;

import org.objectweb.asm.MethodVisitor;
import scg.fusion.annotation.*;
import scg.fusion.aop.AutowireJoinPoint;
import scg.fusion.aop.ExecutionJoinPoint;
import scg.fusion.aop.JoinPoint;
import scg.fusion.cglib.proxy.Enhancer;
import scg.fusion.cglib.proxy.MethodProxy;
import scg.fusion.exceptions.IllegalContractException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.lang.reflect.Field;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


import static java.lang.Boolean.getBoolean;
import static java.lang.Boolean.logicalOr;
import static java.lang.String.format;
import static java.lang.reflect.Modifier.*;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.*;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.*;
import static java.util.stream.Stream.*;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.*;
import static scg.fusion.OnTheFlyFactory.newAspectSpecImpl;
import static scg.fusion.Tuple.tupled;
import static scg.fusion.Verification.*;
import static scg.fusion.annotation.MessageTopics.DEFAULT_TOPIC_NAME;
import static scg.fusion.annotation.Privileged.LOWEST_PRIORITY;

public final class Utils {

    static final  Class[] emptyClassArray  = new Class[0];
    static final Object[] emptyObjectArray = new Object[0];

    static final String BOOLEAN       = "boolean";
    static final String BOOLEAN_VALUE = "booleanValue";
    static final String DOUBLE        = "double";
    static final String DOUBLE_VALUE  = "doubleValue";
    static final String SHORT         = "short";
    static final String SHORT_VALUE   = "shortValue";
    static final String FLOAT         = "float";
    static final String FLOAT_VALUE   = "floatValue";
    static final String BYTE          = "byte";
    static final String BYTE_VALUE    = "byteValue";
    static final String CHAR          = "char";
    static final String CHAR_VALUE    = "charValue";
    static final String LONG          = "long";
    static final String LONG_VALUE    = "longValue";
    static final String INT           = "int";
    static final String INT_VALUE     = "intValue";

    static final char PLUS_CHAR  = '+';
    static final char EMPTY_CHAR = '\u0000';

    static final String _GENERATED_                    = "<generated>";
    static final String ON                             = "on";
    static final String OFF                            = "off";
    static final String ENABLED                        = "enabled";
    static final String ENABLE                         = "enable";
    static final String DISABLED                       = "disabled";
    static final String DISABLE                        = "disable";
    static final String EMPTY_STRING                   = "";
    static final String DOT_CLASS                      = ".class";
    static final String ASM                            = "asm";
    static final String DOT                            = ".";
    static final String DOLLAR                         = "$";
    static final String S_PROPERTIES                   = "%s.properties";
    static final String APP_DOT_PROPERTIES             = "fusion.properties";
    static final String TRACE_CLASS_VISITOR_CLASS_NAME = "org.objectweb.asm.util.TraceClassVisitor";
    static final String ON_THE_FLY_DUMP_CODE_LOCATION_PROPERTY_NAME = "fusion.onthefly.dump.location";

    static final String PROPERTIES                   = "properties";
    static final String INITIALIZE                   = "initialize";
    static final String WIRE                         = "wire";
    static final String RESOLVE                      = "resolve";
    static final String NEW                          = "new";
    static final String PROVIDER                     = "provider";
    static final String COMPONENT                    = "component";
    static final String UTILIZE                      = "utilize";
    static final String GET                          = "get";
    static final String LIST_ALL                     = "listAll";
    static final String COMPONENTS                   = "components";
    static final String BIND                         = "bind";
    static final String LOOKUP                       = "lookup";
    static final String DEPENDENCY                   = "dependency";
    static final String ENV                          = "environment";
    static final String MAKE_COMPONENT               = "makeComponent";
    static final String GET_COMPONENT                = "getComponent";
    static final String GET_CLASS                    = "getClass";
    static final String UNREFLECT                    = "unreflect";
    static final String FOR_NAME                     = "forName";
    static final String GET_PROPERTY                 = "getProperty";
    static final String GET_PROVIDER                 = "getProvider";
    static final String NOTIFY                       = "notify";
    static final String EXACTLY                      = "exactly";
    static final String FIELD                        = "field";
    static final String AUTOWIRE_WITH                = "autowireWith";
    static final String AUTOWIRE                     = "autowire";
    static final String INVOKE                       = "invoke";
    static final String ADVISE                       = "advise";
    static final String IS_WITHIN_GUARD              = "isWithinGuard";
    static final String TEST                         = "test";
    static final String POINTCUT                     = "pointcut";
    static final String MATCH                        = "match";
    static final String CONSTRUCTOR_PARAM_TYPES      = "constructorParamTypes";
    static final String TO_INIT_METHOD               = "toInitMethod";
    static final String TO_NEW_METHOD                = "toNewMethod";
    static final String ADVICE                       = "advice";
    static final String JOIN_POINTS                  = "joinPoints";
    static final String PROXY                        = "proxy";
    static final String METHOD                       = "method";
    static final String CONSTRUCTOR                  = "constructor";
    static final String INTERCEPT                    = "intercept";
    static final String EMPTY_OBJECT_ARRAY           = "emptyObjectArray";
    static final String CLAZZ                        = "clazz";
    static final String _INIT_                       = "<init>";
    static final String INIT                         = "init";
    static final String VOID                         = "void";
    static final String VALUE                        = "value";
    static final String PARAMETER_TYPES              = "parameterTypes";
    static final String EXCEPTION_TYPES              = "exceptionTypes";
    static final String MODIFIERS                    = "modifiers";
    static final String SLOT                         = "slot";
    static final String SIGNATURE                    = "signature";
    static final String ANNOTATIONS                  = "annotations";
    static final String PARAMETER_ANNOTATIONS        = "parameterAnnotations";
    static final String EMPTY_CLASS_ARRAY            = "emptyClassArray";
    static final String GET_PRIVILEGE_LEVEL          = "getPrivilegeLevel";
    static final String GET_PRIMITIVE_CLASS          = "getPrimitiveClass";
    static final String GET_METHOD_DESCRIPTOR_STRING = "getMethodDescriptorString";

    static final int LOOKUP_ACCESS = -1;

    public static int availableThreads = Runtime.getRuntime().availableProcessors();

    private Utils() {
        throw new UnsupportedOperationException();
    }

    static Class<ClassLoader> classloaderClass = ClassLoader.class;
    static Class<Application> appClass         = Application.class;

    static void deleteIfExists(File root) throws IOException {
        if (nonNull(root)) {

            File[] children = root.listFiles();

            if (nonNull(children)) {
                for (File child : children) {
                    deleteIfExists(child);
                }
            }

            root.delete();
        }
    }

    static String getMethodDescriptorString(Class<?> returnType) {
        return MethodType.methodType(returnType).toMethodDescriptorString();
    }

    public static Object payloadWithHeaders(Map<String, String> headers, Object payload) {
        return new PayloadWithHeaders(headers, payload);
    }

    static boolean isComponentFactoryLike(Class<?> aspectSpec) {
        return ComponentFactoryLike.class.isAssignableFrom(aspectSpec);
    }

    static boolean hasAutowiredFields(Class<?> componentType) {

        for (Field field : componentType.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                return true;
            }
        }

        return false;

    }

    public static boolean isInit(Method method) {
        return (_INIT_).equals(method.getName());
    }

    public static boolean isNew(Method method) {
        return (NEW).equals(method.getName());
    }

    static boolean isAdvice(Method method) {

        if (method.isAnnotationPresent(Around.class) || method.isAnnotationPresent(Crosscut.class)) {
            verifyAdvice(method);
            return true;
        }

        return false;

    }

    static boolean isDivergentCrosscutAdvice(Method advice) {
        return advice.getParameterTypes()[0] == JoinPoint.class;
    }

    static boolean isAutowireCrosscutAdvice(Method advice) {
        return advice.getParameterTypes()[0] == AutowireJoinPoint.class;
    }

    static String getOnTheFlyDumpCodePath(Application instance) {
        return instance.getClass().getAnnotation(DumpOnTheFlyCode.class).value();
    }

    static boolean onTheFlyDumpCodeEnabled(Application instance) {
        return instance.getClass().isAnnotationPresent(DumpOnTheFlyCode.class);
    }

    static boolean isEnhanced(Class<?> componentType) {
        return Enhancer.isEnhanced(componentType);
    }

    static boolean isExecutionCrosscutAdvice(Method advice) {
        return advice.getParameterTypes()[0] == ExecutionJoinPoint.class;
    }

    static boolean isAspectSpecImpl(Class<?> componentType) {
        return componentType.isAnnotationPresent(AspectSpecImpl.class);
    }

    static boolean isAspectSpec(Class<?> componentType) {
        // TODO : verify
        return componentType.isInterface() && hasNoAbstractMethods(componentType) && hasNoInterfaces(componentType) && streamAllAdvices(componentType).findAny().isPresent();
    }

    static Predicate<Class<?>> isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return componentType -> componentType.isAnnotationPresent(annotationType);
    }

    static boolean isNotAspect(Class<?> componentType) {
        return !streamAllAdvices(componentType).findAny().isPresent();
    }

    static boolean hasNoAbstractMethods(Class<?> componentType) {
        return Stream.of(componentType.getDeclaredMethods()).map(Method::getModifiers).noneMatch(Modifier::isAbstract);
    }

    static boolean hasNoInterfaces(Class<?> componentType) {
        return componentType.getInterfaces().length == 0;
    }

    static boolean isComponentTypeCandidate(Class<?> componentType) {
        return !appClass.isAssignableFrom(componentType) && !componentType.isInterface() && !componentType.isAnnotation() && !componentType.isEnum() && !componentType.isSynthetic() && !componentType.isAnonymousClass() && !componentType.isMemberClass() && !componentType.isLocalClass() && !componentType.isArray();
    }

    static MethodProxy newConstructorProxy(Method constructorMethod, ComponentConstructor constructor) {
        return new InitMethodProxy(constructor, constructorMethod.getParameterCount());
    }

    static ComponentScopeServiceDecorator newScope(AnnotatedElement componentTypeOrFactoryMethod) {

        if (componentTypeOrFactoryMethod instanceof Class<?>) {
            return getVerifiedScopeDecorator(componentTypeOrFactoryMethod);
        }

        if (componentTypeOrFactoryMethod instanceof Method) {
            return getVerifiedScopeDecorator(componentTypeOrFactoryMethod);
        }

        throw new IllegalContractException("unknown component kind [%s]", componentTypeOrFactoryMethod);

    }

    static <A> Stream<A> suspendStream(Supplier<Stream<A>> stream) {
        return Stream.of((A) (null)).flatMap($ -> stream.get());
    }

    static Stream<Class<?>> streamApi(Class<?> componentType) {

        if (isNull(componentType) || componentType == Object.class) {
            return Stream.empty();
        }

        Stream<Class<?>> superTypes = of(componentType);

        Class<?> superclass = componentType.getSuperclass();

        if (nonNull(superclass)) {
            superTypes = concat(superTypes, streamApi(superclass));
        }

        for (Class<?> iface : componentType.getInterfaces()) {
            superTypes = concat(superTypes, streamApi(iface));
        }

        return superTypes;

    }

    static Collection<Class<?>> listTypeHierarchy(Class<?> componentType) {
        return streamApi(componentType).collect(toList());
    }

    static Class<?> getActualComponentType(Class<?> componentType) {
        return isEnhanced(componentType) ? (componentType.getSuperclass()) : componentType;
    }

    static Iterable<Properties> listProperties(Application application) throws IOException {

        Class<? extends Application> applicationClass = application.getClass();

        ClassLoader classLoader = applicationClass.getClassLoader();

        List<Properties> properties = new ArrayList<>();

        properties.add(loadProperties(classLoader.getResourceAsStream(APP_DOT_PROPERTIES)));

        if (applicationClass.isAnnotationPresent(WithProperties.class)) {
            for (String fileName : applicationClass.getAnnotation(WithProperties.class).value()) {
                properties.add(loadProperties(classLoader.getResourceAsStream(format(S_PROPERTIES, fileName))));
            }
        }

        return properties;

    }

    static Properties loadProperties(InputStream inputStream) throws IOException {

        Properties properties = new Properties();

        if (nonNull(inputStream)) {
            try {
                properties.load(inputStream);
            } finally {
                inputStream.close();
            }
        }

        return properties;

    }

    static boolean awaitShutdownEnabled(Application application) {
        return logicalOr(getBoolean("fusion.awaitShutdown"), (application.getClass()).isAnnotationPresent(AwaitShutdown.class));
    }

    static boolean isSingletonOrPrototype(Class<?> componentType) {
        return componentType.isAnnotationPresent(Prototype.class) || componentType.isAnnotationPresent(Singleton.class);
    }

    static Application instantiateApplication() {

        ClassLoader contextClassLoader = (Thread.currentThread()).getContextClassLoader();

        try {

            Field loadedClasses = classloaderClass.getDeclaredField("classes");

            loadedClasses.setAccessible(true);

            for (Class<?> clazz : ((Iterable<Class<?>>) loadedClasses.get(contextClassLoader))) {
                if (!isAbstract(clazz.getModifiers()) && appClass != clazz && appClass.isAssignableFrom(clazz)) {
                    return clazz.asSubclass(appClass).getConstructor().newInstance();
                }
            }

            throw new RuntimeException("Application not found");

        } catch (IllegalAccessException | NoSuchFieldException cause) {
            throw new RuntimeException("Cannot find application", cause); // never
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException cause) {
            throw new RuntimeException("Application instantiation failed", cause);
        }

    }

    static Method getPrimaryConstructorMethod(Class<?> componentType) {

        List<Method> methods = new ArrayList<>();

        for (Method method : componentType.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Primary.class) && isStatic(method)) {
                methods.add(method);
            }
        }

        if (methods.isEmpty()) {
            throw new IllegalContractException("cannot find @Primary constructor method for component [%s]", componentType);
        } else if (1 == methods.size()) {
            return methods.get(0);
        } else {
            throw new IllegalContractException("cannot recognize @Primary constructor method for component [%s]", componentType);
        }

    }

    static Constructor<?> getPrimaryConstructor(Class<?> componentType) {

        Constructor<?>[] declaredConstructors = componentType.getDeclaredConstructors();

        if (1 == declaredConstructors.length) {
            return declaredConstructors[0];
        }

        List<Constructor<?>> primaryConstructors = new ArrayList<>();

        for (Constructor<?> constructor : declaredConstructors) {
            if (constructor.isAnnotationPresent(Primary.class)) {
                primaryConstructors.add(constructor);
            }
        }

        if (primaryConstructors.isEmpty()) {
            throw new IllegalContractException("cannot find @Primary constructor for component [%s]", componentType);
//            return null;
        } else if (1 == primaryConstructors.size()) {
            return primaryConstructors.get(0);
        } else {
            throw new IllegalContractException("cannot recognize primary constructor for component [%s]", componentType);
        }

    }

    // FIXME

    static Type getMessageType(Method method) {

        Type param = (method.getGenericParameterTypes())[0];

        if (param instanceof ParameterizedType) {

            Type actualTypeParam = (((ParameterizedType) param).getActualTypeArguments())[0];

            if (actualTypeParam instanceof WildcardType) {
                throw new IllegalContractException("wildcard message parametrization unsupported, content type must be invariant");
            } else {
                param = actualTypeParam;
            }
        }

        return param;

    }

    static Iterable<String> listMessageTopics(Method method) {
        if (method.isAnnotationPresent(MessageTopics.class)) {
            return of(method.getAnnotation(MessageTopics.class).value()).collect(toSet());
        } else {
            return singleton(DEFAULT_TOPIC_NAME);
        }
    }

    static Iterable<Class<?>> listInterfaces(Class<?> componentType) {
        return Stream.of(componentType.getInterfaces()).collect(toSet());
    }

    static Stream<Method> streamAllAdvices(Class<?> componentType) {

        if (isNull(componentType)) {
            return Stream.empty();
        }

        Stream<Method> superAdvices = streamAllAdvices(componentType.getSuperclass());

        for (Class<?> api : listInterfaces(componentType)) {
            superAdvices = Stream.concat(superAdvices, streamAllAdvices(api));
        }

        Stream<Method> advices = Stream.of(componentType.getDeclaredMethods());

        return Stream.concat(advices, superAdvices).filter(Utils::isAdvice);

    }

    static boolean isAutowired(Field field) {
        return field.isAnnotationPresent(Autowired.class);
    }

    static Stream<Field> streamAllAutowireJoinPoints(Class<?> componentType) {
        return Stream.of(componentType.getDeclaredFields()).filter(Utils::isAutowired);
    }

    static Map<Class<?>, Set<Field>> classifyAllAutowireJoinPoints(Collection<Class<?>> componentTypes) {
        return (componentTypes.stream()).filter(Utils::isNotAspect).flatMap(Utils::streamAllAutowireJoinPoints).collect(groupingBy(Field::getDeclaringClass, toSet()));
    }

    static <T> Function<T, Stream<T>> zipWith(T value) {
        return item -> Stream.concat(Stream.of(item), Stream.of(value));
    }

    static Map<Class<?>, Set<Method>> classifyAllExecutionJoinPoints(Collection<Class<?>> componentTypes, ConstructorFactory converter) {

        Function<Class<?>, Stream<Tuple<Method, Class<?>>>> zip = componentType -> {

            Constructor<?> constructor = getPrimaryConstructor(componentType);

            Method newMethod = converter.toNewMethod(componentType, constructor.getModifiers());

            return concat(concat(streamAllMethodJoinPoints(componentType), streamConstructorJoinPoints(componentType).map(converter)), of(newMethod)).map(tupled(componentType));

        };

        return (componentTypes.stream()).filter(Utils::isNotAspect).flatMap(zip).collect(groupingBy_2(toSet()));

    }

    static Map<Class<?>, Set<Method>> classifyAdvices(Collection<Class<?>> componentTypes) {

        Function<Class<?>, Stream<Tuple<Method, Class<?>>>> zip = componentType -> {
            return streamAllAdvices(componentType).map(tupled(isAspectSpec(componentType) ? newAspectSpecImpl(componentType) : (componentType)));
        };

        return (componentTypes.stream()).flatMap(zip).collect(groupingBy_2(toSet()));

    }

    static <A, B, R> Collector<Tuple<A, B>, ?, Map<B, R>> groupingBy_2(Collector<A, ?, R> downstream) {
        return groupingBy(Tuple::_2, mapping(Tuple::_1, downstream));
    }

    static Stream<Method> streamAllMethodJoinPoints(Class<?> componentType) {

        if (isNull(componentType)) {
            return Stream.empty();
        }

        Stream<Method> superJoinPoints = streamAllMethodJoinPoints(componentType.getSuperclass());

        for (Class<?> api : listInterfaces(componentType)) {
            superJoinPoints = Stream.concat(superJoinPoints, streamAllMethodJoinPoints(api));
        }

        Stream<Method> joinPoints = Stream.of(componentType.getDeclaredMethods());

        return Stream.concat(joinPoints, superJoinPoints).filter(Utils::isJoinPoint);

    }

    static Stream<Constructor<?>> streamConstructorJoinPoints(Class<?> componentType) {
        // TODO: @Primary logic?
//        return Stream.of(componentType.getDeclaredConstructors()).filter(constructor -> !isPrivate(constructor.getModifiers()));
        return Stream.of(componentType.getDeclaredConstructors());
    }

    static boolean isJoinPoint(Method method) {

        int modifiers = method.getModifiers();

        return !isNative(modifiers) &&
                   !isFinal(method) &&
                  !isStatic(method) &&
              !isPrivate(modifiers) &&
             !isAbstract(modifiers) &&
                 !method.isBridge() && !method.isSynthetic();

    }

    static boolean isJoinPoint(Field field) {
        return !isFinal(field) && !isStatic(field);
    }
    
    static Iterable<Around> listCrosscut(Method advice) {
        if (advice.isAnnotationPresent(Crosscut.class)) {
            return asList(advice.getAnnotation(Crosscut.class).value());
        } else {
            return asList(advice.getAnnotationsByType(Around.class));
        }
    }

    static boolean hasFactories(Class<?> componentType) {

        for (Method method : componentType.getDeclaredMethods()) {
            if (isFactoryMethod(method, false)) {
                return true;
            }
        }

        return false;

    }

    static boolean hasMessageListeners(Class<?> componentType) {

        for (Method method : componentType.getDeclaredMethods()) {
            if (isMessageListener(method, false) || isDlqListener(method, false)) {
                return true;
            }
        }

        return false;

    }

    static int getPrivilegeLevel(Method advice) {
        return advice.isAnnotationPresent(Privileged.class) ? (advice.getAnnotation(Privileged.class).value()) : LOWEST_PRIORITY;
    }

    static String getSimpleName(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    static String getPackageName(Class<?> clazz) {

        Package pack = clazz.getPackage();

        return isNull(pack) ? (null) : pack.getName();

    }

    static String getPointcutExpression(Method advice) {
        return advice.getAnnotation(Around.class).value();
    }

    static Consumer<MethodVisitor> unboxing(Class<?> primitiveType) {
        return visitor -> {
            if (primitiveType.isPrimitive()) {
                switch (primitiveType.getName()) {
                    case BOOLEAN:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), BOOLEAN_VALUE, getMethodDescriptor(BOOLEAN_TYPE), false);
                        break;
                    case SHORT:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), SHORT_VALUE, getMethodDescriptor(SHORT_TYPE), false);
                        break;
                    case BYTE:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), BYTE_VALUE, getMethodDescriptor(BYTE_TYPE), false);
                        break;
                    case CHAR:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Character.class), CHAR_VALUE, getMethodDescriptor(CHAR_TYPE), false);
                        break;
                    case INT:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), INT_VALUE, getMethodDescriptor(INT_TYPE), false);
                        break;
                    case DOUBLE:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), DOUBLE_VALUE, getMethodDescriptor(DOUBLE_TYPE), false);
                        break;
                    case FLOAT:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), FLOAT_VALUE, getMethodDescriptor(FLOAT_TYPE), false);
                        break;
                    case LONG:
                        visitor.visitMethodInsn(INVOKEVIRTUAL, getInternalName(Number.class), LONG_VALUE, getMethodDescriptor(LONG_TYPE), false);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    static Consumer<MethodVisitor> boxing(Class<?> primitiveType) {
        return visitor -> {
            // TODO
        };
    }

    static Class<?> getMagicAccessorImplClass() {
        try {
            return Class.forName("sun.reflect.MagicAccessorImpl");
        } catch (ClassNotFoundException cause) {
            throw new RuntimeException("Cannot get sun.reflect.MagicAccessorImpl for name", cause);
        }
    }

    static BiFunction<Class<?>, Map<String, Field>, Map<String, Field>> append(Field field) {
        return ($, fields) -> {

            if (isNull(fields)) {
                fields = new HashMap<>();
            }

            fields.put(field.getName(), field);

            return fields;

        };
    }

    static BiFunction<Method, Set<ExecutionAdvisor>, Set<ExecutionAdvisor>> append(ExecutionAdvisor advisor) {
        return ($, advisement) -> {

            if (isNull(advisement)) {
                advisement = new TreeSet<>(comparingInt(ExecutionAdvisor::getPrivilegeLevel));
            }

            advisement.add(advisor);

            return advisement;

        };
    }

    static BiFunction<Field, Set<AutowireAdvisor>, Set<AutowireAdvisor>> append(AutowireAdvisor advisor) {
        return ($, advisement) -> {

            if (isNull(advisement)) {
                advisement = new TreeSet<>(comparingInt(AutowireAdvisor::getPrivilegeLevel));
            }

            advisement.add(advisor);

            return advisement;

        };
    }

    static BiFunction<Class<?>, Set<AutowiringHook>, Set<AutowiringHook>> append(AutowiringHook hook) {
        return ($, autowiring) -> {

            if (isNull(autowiring)) {
                autowiring = new HashSet<>();
            }

            autowiring.add(hook);

            return autowiring;

        };
    }

    // TODO : CACHE

    static Iterable<Method> listAnnotatedMethods(Class<?> componentType, Class<? extends Annotation> annotationType) {

        List<Method> methods = new ArrayList<>();

        visitAnnotatedMethods(componentType, annotationType, methods);

        visitAnnotatedMethods(componentType.getSuperclass(), annotationType, methods);

        for (Class<?> iface : componentType.getInterfaces()) {
            visitAnnotatedMethods(iface, annotationType, methods);
        }

        return methods;

    }

    // TODO : CACHE

    static boolean isStatic(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    static boolean isFinal(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    static boolean isFinal(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    static boolean isStatic(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    static boolean hasNoArgs(Method method) {
        return 0 == method.getParameterCount();
    }

    static boolean isVoid(Class<?> returnType) {
        return void.class == returnType;
    }

    static void visitAnnotatedMethods(Class<?> componentType, Class<? extends Annotation> annotationType, List<Method> accumulator) {
        if (nonNull(componentType)) {
            for (Method method : componentType.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotationType)) {
                    accumulator.add(method);
                }
            }
        }
    }

    static void visitMethods(Class<?> componentType, List<Method> accumulator, ExecutionPointcut pointcut) {
        if (nonNull(componentType)) {
            for (Method method : componentType.getDeclaredMethods()) {
                if (pointcut.match(method)) {
                    accumulator.add(method);
                }
            }
        }
    }

    static Map<Class<?>, Object> classifyByType(Object[] components) {

        HashMap<Class<?>, Object> classification = new HashMap<>();

        for (Object component : components) {

            Class<?> selfComponentType = component.getClass();

            classification.put(selfComponentType, component);

            for (Class<?> componentType : listTypeHierarchy(selfComponentType)) {
                classification.put(componentType, component);
            }
        }

        return classification;

    }

    public static Iterable<Method> filterMethods(Iterable<Class<?>> componentTypes, Pointcut crosscut) {
        return StreamSupport.stream(componentTypes.spliterator(), false)
                .flatMap(componentType -> stream(componentType.getDeclaredMethods()))
                .filter(method -> isJoinPoint(method))
                .filter(crosscut::match)
                .collect(toList());
    }

    public static Iterable<Field> filterFields(Iterable<Class<?>> componentTypes, Pointcut crosscut) {
        return StreamSupport.stream(componentTypes.spliterator(), false)
                .flatMap(componentType -> stream(componentType.getDeclaredFields()))
                .filter(method -> isJoinPoint(method))
                .filter(crosscut::match)
                .collect(toList());
    }
}
