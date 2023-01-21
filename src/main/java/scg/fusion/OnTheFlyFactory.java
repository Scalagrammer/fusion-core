package scg.fusion;

import org.objectweb.asm.Type;
import scg.fusion.annotation.*;
import scg.fusion.aop.AutowireJoinPoint;
import scg.fusion.aop.ExecutionJoinPoint;
import scg.fusion.cglib.proxy.ExecutionInterceptor;
import scg.fusion.cglib.proxy.MethodProxy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.String.format;

import static java.util.Objects.nonNull;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static scg.fusion.LabelKey._1;
import static scg.fusion.OnTheFlyClass.MagicAccessorImpl;
import static scg.fusion.Utils.*;
import static scg.fusion.Utils.METHOD;
import static scg.fusion.Utils.NEW;
import static scg.fusion.Utils.VOID;
import static scg.fusion.Verification.*;

public final class OnTheFlyFactory {

    static Map<Method, ExecutionAdvisor> invokerCache = new HashMap<>();
    static Map<Method, AutowireAdvisor> injectorCache = new HashMap<>();
    static Map<Class<?>, ComponentService> serviceCache = new HashMap<>();
    static Map<Class<?>, Class<?>> aspectSpecImplCache = new HashMap<>();

    private OnTheFlyFactory() {
        throw new UnsupportedOperationException();
    }

    static AutowiringHook newAutowiringHook(Method by, ComponentFactory components) {
        try {
            return new OnTheFlyClass("AutowiringHook", MagicAccessorImpl, AutowiringHook.class) {

                @Override
                protected void emit() {

                    field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);

                    ctor(ComponentFactory.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(COMPONENTS)
                            .rеturn();

                    emitAutowireWith(method(ACC_PUBLIC_FINAL, Object.class, AUTOWIRE_WITH, ComponentFactory.class));
                }

                void emitAutowireWith(MethodBody body) {

                    body.aload_1()
                        .ifnonnull_jump(_1)
                        .aload_0()
                        .get_field(COMPONENTS)
                        .astore_1()
                        .label(_1)
                        .aload_1()
                        .ldc(by.getDeclaringClass())
                        .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class);

                    for (Class<?> parameterType : by.getParameterTypes()) {
                        body.aload_1()
                                .ldc(parameterType)
                                .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class);
                    }

                    body.invoke_instance(by);

                    if (isVoid(by.getReturnType())) {
                        body.aconst_null()
                                .rеturn();
                    } else {
                        body.visit(boxing(by.getReturnType()))
                                .arеturn();
                    }
                }

            }.loadAs(AutowiringHook.class)
                    .getDeclaredConstructor(ComponentFactory.class)
                    .newInstance(components);
        } catch (Exception cause) {
            throw new RuntimeException("AutowiringHook instantiation failed", cause);
        }
    }

    static AutowiringHook newAutowiringHook(AutowireInterceptor advice, Field at, ComponentFactory components) {
        try {
            return new OnTheFlyClass("AutowiringHook", MagicAccessorImpl, AutowiringHook.class) {
                @Override
                protected void emit() {

                    field(ACC_PRIVATE_FINAL, Field.class,                 FIELD);
                    field(ACC_PRIVATE_FINAL, AutowireInterceptor.class,  ADVICE);
                    field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);

                    ctor(Field.class, AutowireInterceptor.class, ComponentFactory.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(FIELD)
                            .aload_0()
                            .aload_2()
                            .put_field(ADVICE)
                            .aload_0()
                            .aload_3()
                            .put_field(COMPONENTS)
                            .rеturn();

                    emitAdvise(method(ACC_PRIVATE_FINAL, Object.class, ADVISE, Object.class, ComponentProvider.class));

                    emitAutowireWith(method(ACC_PUBLIC_FINAL, Object.class, AUTOWIRE_WITH, ComponentFactory.class));

                }

                void emitAdvise(MethodBody body) {
                    body.aload_0()
                        .get_field(ADVICE)
                        .aload_1()
                        .aload_0()
                        .get_field(FIELD)
                        .aload_2()
                        .invoke_interface(AutowireInterceptor.class, Object.class, INTERCEPT, Object.class, Field.class, ComponentProvider.class)
                        .arеturn();
                }

                void emitAutowireWith(MethodBody body) {
                    body.aload_1()
                        .ifnonnull_jump(_1)
                        .aload_0()
                        .get_field(COMPONENTS)
                        .astore_1()
                        .label(_1)
                        .aload_1()
                        .ldc(at.getDeclaringClass())
                        .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class) // FIXME: ASTORE_2/ALOAD_2
                        .aload_0()
                        .aconst_null() // FIXME
                        .aload_1()
                        .ldc(at.getType())
                        .invoke_interface(ComponentFactory.class, ComponentProvider.class, GET_PROVIDER, Class.class)
                        .invoke_special(Object.class, ADVISE, Object.class, ComponentProvider.class)
                        .dup_x1()
                        .put_field(at.getDeclaringClass(), at.getName())
                        .arеturn();
                }

            }.loadAs(AutowiringHook.class)
                    .getDeclaredConstructor(Field.class, AutowireInterceptor.class, ComponentFactory.class)
                    .newInstance(at, advice, components);
        } catch (Exception cause) {
            throw new RuntimeException("AutowiringHook instantiation failed", cause);
        }
    }

    static ExecutionAdvisor newExecutionAdvisor(ExecutionPointcut pointcut, Class<?> aspectComponentType, Method advice, ComponentProvider<?> aspectProvider, int privilegeLevel) {
        return invokerCache.computeIfAbsent(advice, $ -> {
            try {
                return new OnTheFlyClass("ExecutionAdvisor", MagicAccessorImpl, ExecutionAdvisor.class) {
                    @Override
                    protected void emit() {

                        field(ACC_PRIVATE_FINAL, ComponentProvider.class, PROVIDER);
                        field(ACC_PRIVATE_FINAL, ExecutionPointcut.class, POINTCUT);

                        ctor(ComponentProvider.class, ExecutionPointcut.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(PROVIDER)
                                .aload_0()
                                .aload_2()
                                .put_field(POINTCUT)
                                .rеturn();

                        method(ACC_PUBLIC_FINAL, int.class, GET_PRIVILEGE_LEVEL)
                                .ldc(privilegeLevel)
                                .irеturn();

                        defineIsWithinGuard(method(ACC_PUBLIC_FINAL, boolean.class, IS_WITHIN_GUARD));

                        method(ACC_PUBLIC_FINAL, Object.class, ADVISE, ExecutionJoinPoint.class)
                                .aload_0()
                                .get_field(PROVIDER)
                                .invoke_interface(ComponentProvider.class, Object.class, GET_COMPONENT)
                                .aload_1()
                                .invoke_virtual(aspectComponentType, advice.getReturnType(), advice.getName(), advice.getParameterTypes())
                                .arеturn();

                        method(ACC_PUBLIC_FINAL, boolean.class, MATCH, Class.class, Method.class)
                                .aload_0()
                                .get_field(POINTCUT)
                                .aload_1()
                                .aload_2()
                                .invoke_interface(ExecutionPointcut.class, boolean.class, MATCH, Class.class, Method.class)
                                .irеturn();
                    }

                    void defineIsWithinGuard(MethodBody methodBody) {

                        if (pointcut.isWithinGuard()) {
                            methodBody.icons_1();
                        } else {
                            methodBody.icons_0();
                        }

                        methodBody.irеturn();

                    }

                }.loadAs(ExecutionAdvisor.class)
                        .getDeclaredConstructor(ComponentProvider.class, ExecutionPointcut.class)
                        .newInstance(aspectProvider, pointcut);
            } catch (Exception cause) {
                throw new RuntimeException("ExecutionAdvisor instantiation failed", cause);
            }
        });
    }

    static AutowireAdvisor newAutowireAdvisor(AutowirePointcut pointcut, Class<?> aspectComponentType, Method advice, ComponentProvider<?> aspectProvider, int privilegeLevel) {
        return injectorCache.computeIfAbsent(advice, $ -> {
            try {
                return new OnTheFlyClass("AutowireAdvisor", MagicAccessorImpl, AutowireAdvisor.class, Pointcut.class) {
                    @Override
                    protected void emit() {

                        field(ACC_PRIVATE_FINAL, ComponentProvider.class, PROVIDER);
                        field(ACC_PRIVATE_FINAL, AutowirePointcut.class, POINTCUT);

                        ctor(ComponentProvider.class, AutowirePointcut.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(PROVIDER)
                                .aload_0()
                                .aload_2()
                                .put_field(POINTCUT)
                                .rеturn();

                        method(ACC_PUBLIC_FINAL, int.class, GET_PRIVILEGE_LEVEL)
                                .ldc(privilegeLevel)
                                .irеturn();

                        method(ACC_PUBLIC_FINAL, Object.class, ADVISE, AutowireJoinPoint.class)
                                .aload_0()
                                .get_field(PROVIDER)
                                .invoke_interface(ComponentProvider.class, Object.class, GET_COMPONENT)
                                .aload_1()
                                .invoke_virtual(aspectComponentType, advice.getReturnType(), advice.getName(), advice.getParameterTypes())
                                .arеturn();

                        method(ACC_PUBLIC_FINAL, boolean.class, MATCH, Class.class, Field.class)
                                .aload_0()
                                .get_field(POINTCUT)
                                .aload_1()
                                .aload_2()
                                .invoke_interface(AutowirePointcut.class, boolean.class, MATCH, Class.class, Field.class)
                                .irеturn();

                    }
                }.loadAs(AutowireAdvisor.class)
                        .getDeclaredConstructor(ComponentProvider.class, AutowirePointcut.class)
                        .newInstance(aspectProvider, pointcut);
            } catch (Exception cause) {
                throw new RuntimeException("AutowireAdvisor instantiation failed", cause);
            }
        });
    }

    static Class<?> newAspectSpecImpl(Class<?> aspectSpec) {
        return aspectSpecImplCache.computeIfAbsent(aspectSpec, $ -> {
            try {
                return new OnTheFlyClass(format("%s$Impl", aspectSpec.getSimpleName()), MagicAccessorImpl, aspectSpec) {
                    @Override
                    protected void emit() {

                        mark(AspectSpecImpl.class, aspectSpec.getCanonicalName());

                        ctor(ACC_PRIVATE)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .invoke_virtual(Class.class, GET_CLASS)
                                .aconst_null()
                                .invoke_special(Class.class, void.class, ctor, ClassLoader.class)
                                .rеturn();

                        if (isComponentFactoryLike(aspectSpec)) {

                            autowired(ComponentFactory.class, COMPONENTS);

                            method(ACC_PUBLIC_FINAL, Object.class, GET, Class.class)
                                    .aload_0()
                                    .get_field(COMPONENTS)
                                    .aload_1()
                                    .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class)
                                    .arеturn();

                            method(ACC_PUBLIC_FINAL, Object.class, GET, String.class)
                                    .aload_0()
                                    .get_field(COMPONENTS)
                                    .aload_1()
                                    .invoke_interface(ComponentFactory.class, Object.class, GET, String.class)
                                    .arеturn();
                        }
                    }
                }.load();
            } catch (Exception cause) {
                throw new RuntimeException("Cannot load aspect spec impl class on the fly", cause);
            }
        });
    }

    static <T> ComponentProvider<T> newAllocator(ExecutionInterceptor advice, Method newMethod, Class<T> componentType) {
        try {
            MethodProxy methodProxy = new NewMethodProxy(newAllocator(componentType));

            return new OnTheFlyClass("EnhancedAllocator", MagicAccessorImpl, ComponentProvider.class) {
                @Override
                protected void emit() {

                    Class<?> actualComponentType = getActualComponentType(componentType);

                    field(ACC_PRIVATE_FINAL, ExecutionInterceptor.class, ADVICE);
                    field(ACC_PRIVATE_FINAL, Method.class, METHOD);
                    field(ACC_PRIVATE_FINAL, MethodProxy.class, PROXY);

                    ctor(ExecutionInterceptor.class, Method.class, MethodProxy.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(ADVICE)
                            .aload_0()
                            .aload_2()
                            .put_field(METHOD)
                            .aload_0()
                            .aload_3()
                            .put_field(PROXY)
                            .rеturn();

                    method(ACC_PUBLIC_FINAL, Object.class, GET_COMPONENT)
                            .aload_0()
                            .get_field(ADVICE)
                            .ldc(ComponentProvider.class)
                            .aconst_null()
                            .aload_0()
                            .get_field(METHOD)
                            .get_static(Utils.class, EMPTY_OBJECT_ARRAY)
                            .aload_0()
                            .get_field(PROXY)
                            .invoke_interface(ExecutionInterceptor.class, Object.class, INTERCEPT, Class.class, Object.class, Method.class, Object[].class, MethodProxy.class)
                            .arеturn();

                }
            }.loadAs(ComponentProvider.class)
                    .getDeclaredConstructor(ExecutionInterceptor.class, Method.class, MethodProxy.class)
                    .newInstance(advice, newMethod, methodProxy);
        } catch (Exception cause) {
            throw new RuntimeException("ComponentProvider as enhanced allocator instantiation failed", cause);
        }
    }

    static <T> ComponentProvider<T> newAllocator(Class<T> componentType) {
        try {
            return new OnTheFlyClass("Allocator", MagicAccessorImpl, ComponentProvider.class) {
                @Override
                protected void emit() {

                    null_ctor();

                    method(ACC_PUBLIC_FINAL, Object.class, GET_COMPONENT)
                            .nеw(componentType)
                            .arеturn();
                }
            }.loadAs(ComponentProvider.class).newInstance();
        } catch (Exception cause) {
            throw new RuntimeException("ComponentProvider as allocator instantiation failed", cause);
        }
    }

    static ConstructorFactory newConstructorConverter() {
        try {
            return new OnTheFlyClass("ConstructorFactory", MagicAccessorImpl, ConstructorFactory.class) {
                @Override
                protected void emit() {

                    null_ctor();

                    method(ACC_PUBLIC_FINAL, Method.class, TO_INIT_METHOD, Constructor.class)
                            .nеw(Method.class)
                            .dup()
                            .aload_1()
                            .get_field(Constructor.class, CLAZZ)
                            .ldc(_INIT_)
                            .aload_1()
                            .get_field(Constructor.class, PARAMETER_TYPES)
                            .ldc(VOID)
                            .invoke_static(Class.class, Class.class, GET_PRIMITIVE_CLASS, String.class)
                            .aload_1()
                            .get_field(Constructor.class, EXCEPTION_TYPES)
                            .aload_1()
                            .get_field(Constructor.class, MODIFIERS)
                            .aload_1()
                            .get_field(Constructor.class, SLOT)
                            .aload_1()
                            .get_field(Constructor.class, SIGNATURE)
                            .aload_1()
                            .get_field(Constructor.class, ANNOTATIONS)
                            .aload_1()
                            .get_field(Constructor.class, PARAMETER_ANNOTATIONS)
                            .aconst_null()
                            .invoke_special(Method.class, void.class, ctor, Class.class, String.class, Class[].class, Class.class, Class[].class, int.class, int.class, String.class, byte[].class, byte[].class, byte[].class)
                            .arеturn();

                    method(ACC_PUBLIC_FINAL, Method.class, TO_NEW_METHOD, Class.class, int.class)
                            .nеw(Method.class)
                            .dup()
                            .aload_1()
                            .ldc(NEW)
                            .get_static(Utils.class, EMPTY_CLASS_ARRAY)
                            .aload_1()
                            .get_static(Utils.class, EMPTY_CLASS_ARRAY)
                            .aload_2()
                            .sipush(ACC_STATIC | ACC_SYNTHETIC)
                            .ior()
                            .bipush(-1)
                            .aload_1()
                            .invoke_static(Utils.class, String.class, GET_METHOD_DESCRIPTOR_STRING, Class.class)
                            .aconst_null()
                            .aconst_null()
                            .aconst_null()
                            .invoke_special(Method.class, void.class, ctor, Class.class, String.class, Class[].class, Class.class, Class[].class, int.class, int.class, String.class, byte[].class, byte[].class, byte[].class)
                            .arеturn();

                }
            }.loadAs(ConstructorFactory.class).newInstance();
        } catch (Exception cause) {
            throw new RuntimeException("ConstructorFactory instantiation failed", cause);
        }
    }

    static MethodProxy newInitMethodProxy(Method initMethod, ComponentConstructor constructor) {
        return new InitMethodProxy(constructor, initMethod.getParameterCount());
    }

    static MessageListenerHandle newStaticMessageListenerHandle(Method method) {
        try {
            return new OnTheFlyClass("StaticMessageListenerHandle", MagicAccessorImpl, MessageListenerHandle.class) {
                @Override
                protected void emit() {

                    null_ctor();

                    method(ACC_PUBLIC_FINAL, void.class, NOTIFY, Object.class)
                            .aload_1()
                            .invoke_static(method)
                            .rеturn();
                }
            }.loadAs(MessageListenerHandle.class).newInstance();
        } catch (Exception cause) {
            throw new RuntimeException("MessageListenerHandle instantiation failed", cause);
        }
    }

    static MessageListenerHandle newInstanceMessageListenerHandle(Method method, ComponentProvider<?> instanceProvider) {
        try {
            return new OnTheFlyClass("InstanceMessageListenerHandle", MagicAccessorImpl, MessageListenerHandle.class) {
                @Override
                protected void emit() {

                    field(ACC_PRIVATE_FINAL, ComponentProvider.class, PROVIDER);

                    ctor(ComponentProvider.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(PROVIDER)
                            .rеturn();

                    method(ACC_PUBLIC_FINAL, void.class, NOTIFY, Object.class)
                            .aload_0()
                            .get_field(PROVIDER)
                            .invoke_interface(ComponentProvider.class, Object.class, GET_COMPONENT)
                            .aload_1()
                            .invoke_virtual(method)
                            .rеturn();
                }
            }.loadAs(MessageListenerHandle.class)
                    .getDeclaredConstructor(ComponentProvider.class)
                    .newInstance(instanceProvider);
        } catch (Exception cause) {
            throw new RuntimeException("MessageListenerHandle instantiation failed", cause);
        }
    }

    static ComponentConstructor newComponentConstructor(Class<?> componentType, Class<?>[] parameterTypes) {
        try {
            return new OnTheFlyClass("ComponentConstructor", MagicAccessorImpl, ComponentConstructor.class) {
                @Override
                protected void emit() {
                    null_ctor();
                    defineInvoke(method(ACC_PUBLIC_FINAL, void.class, INVOKE, Object.class, Object[].class));
                }

                void defineInvoke(MethodBody body) {
                    body.aload_1();
                    for (int i = 0; i < parameterTypes.length; i++) {
                        body.aload_2()
                                .bipush(i)
                                .aaload()
                                .visit(unboxing(parameterTypes[i]));
                    }
                    body.invoke_special(componentType, void.class, ctor, parameterTypes)
                            .rеturn();
                }

            }.loadAs(ComponentConstructor.class).newInstance();
        } catch (Exception cause) {
            throw new RuntimeException("ComponentConstructor instantiation failed", cause);
        }
    }

    static ComponentScope newStaticFactoryScope(Method method, Environment environment, ComponentFactory components) {
        try {

            String utilizeBy = getVerifiedUtilizeMethodName(method);
            String initializeBy = getVerifiedInitializeByMethodName(method);

            int parameterCount = method.getParameterCount();

            Class<? extends ComponentProvider> factoryMethodClass = new OnTheFlyClass("StaticFactoryMethod", MagicAccessorImpl, ComponentProvider.class) {
                @Override
                protected void emit() {

                    if (0 == parameterCount) {
                        null_ctor();
                    } else {
                        field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);
                        ctor(ComponentFactory.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(COMPONENTS)
                                .rеturn();
                    }

                    defineGetComponent(method(ACC_PUBLIC_FINAL, Object.class, GET_COMPONENT));

                }

                void defineGetComponent(MethodBody body) {
                    if (0 == parameterCount) {
                        body.invoke_static(method).arеturn();
                    } else {
                        for (Parameter parameter : method.getParameters()) {
                            if (parameter.isAnnotationPresent(Qualified.class)) {
                                body.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getAnnotation(Qualified.class).value())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, String.class);
                            } else {
                                body.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getType())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class);
                            }
                            body.visit(unboxing(parameter.getType()));
                        }
                        body.invoke_static(method)
                                .rеturn();
                    }
                }

            }.loadAs(ComponentProvider.class);

            ComponentScopeServiceDecorator scope = newScope(method);

            if (0 == parameterCount) {
                scope.componentAllocator = factoryMethodClass.newInstance();
            } else {
                scope.componentAllocator = factoryMethodClass.getDeclaredConstructor(ComponentFactory.class).newInstance(components);
            }

            scope.componentAutowiring = newComponentWiring(method.getReturnType(), components);
            scope.componentService = newComponentService(environment, utilizeBy, initializeBy, method.getReturnType());

            return scope;

        } catch (Exception cause) {
            throw new RuntimeException("StaticFactoryMethod instantiation failed", cause);
        }
    }

    static ComponentScope newInstanceFactoryScope(ComponentProvider<?> instanceProvider, Method method, Environment env, ComponentFactory components) {
        try {

            String initializeBy = getVerifiedInitializeByMethodName(method);
            String utilizeBy = getVerifiedUtilizeMethodName(method);

            Class<?> componentType = method.getReturnType();

            int parameterCount = method.getParameterCount();

            Class<? extends ComponentProvider> factoryMethodClass = new OnTheFlyClass("InstanceFactoryMethod", MagicAccessorImpl, ComponentProvider.class) {
                @Override
                protected void emit() {
                    field(ACC_PRIVATE_FINAL, ComponentProvider.class, PROVIDER);
                    if (0 == parameterCount) {
                        ctor(ComponentProvider.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(PROVIDER)
                                .rеturn();
                    } else {
                        field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);
                        ctor(ComponentProvider.class, ComponentFactory.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(PROVIDER)
                                .aload_0()
                                .aload_2()
                                .put_field(COMPONENTS)
                                .rеturn();
                    }
                    defineMakeComponent(method(ACC_PUBLIC_FINAL, Object.class, MAKE_COMPONENT));
                }

                void defineMakeComponent(MethodBody body) {
                    if (0 == parameterCount) {
                        body.aload_0()
                                .get_field(PROVIDER)
                                .invoke_interface(ComponentProvider.class, Object.class, GET_COMPONENT)
                                .invoke_virtual(method)
                                .arеturn();
                    } else {
                        body.aload_0()
                                .get_field(PROVIDER)
                                .invoke_interface(ComponentProvider.class, Object.class, GET_COMPONENT);

                        for (Parameter parameter : method.getParameters()) {
                            if (parameter.isAnnotationPresent(Qualified.class)) {
                                body.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getAnnotation(Qualified.class).value())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, String.class);
                            } else {
                                body.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getType())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class);
                            }
                            body.visit(unboxing(parameter.getType()));
                        }
                        body.invoke_virtual(method).arеturn();
                    }
                }

            }.loadAs(ComponentProvider.class);

            ComponentScopeServiceDecorator scope = newScope(method);

            scope.componentAutowiring = newComponentWiring(method.getReturnType(), components);
            scope.componentService = newComponentService(env, utilizeBy, initializeBy, method.getReturnType());

            if (0 == parameterCount) {
                scope.componentAllocator = factoryMethodClass.getDeclaredConstructor(ComponentProvider.class).newInstance(instanceProvider);
            } else {
                scope.componentAllocator = factoryMethodClass.getDeclaredConstructor(ComponentProvider.class, ComponentFactory.class).newInstance(instanceProvider, components);
            }

            return scope;

        } catch (Exception cause) {
            throw new RuntimeException("InstanceFactoryMethod instantiation failed", cause);
        }
    }

    static ComponentWiring newComponentWiring(Class<?> componentType, ComponentFactory components) {

        if (!hasAutowiredFields(componentType)) {
            return ComponentWiring.NO_OP_WIRING;
        }

        try {
            return new OnTheFlyClass("ComponentWiring", MagicAccessorImpl, ComponentWiring.class) {
                @Override
                protected void emit() {

                    Class<?> actualComponentType = getActualComponentType(componentType);

                    field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);

                    ctor(ComponentFactory.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(COMPONENTS)
                            .rеturn();

                    method(ACC_PUBLIC_FINAL, void.class, WIRE, Object.class).visit(mv -> {

                        for (Field field : listVerifiedInjectPoints(actualComponentType)) {

                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, slashedClassName, COMPONENTS, getDescriptor(ComponentFactory.class));

                            if (field.isAnnotationPresent(Qualified.class)) {
                                mv.visitLdcInsn(field.getAnnotation(Qualified.class).value());
                                mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET, getMethodDescriptor(getType(Object.class), getType(String.class)), true);
                            } else {
                                mv.visitLdcInsn(getType(field.getType()));
                                mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET, getMethodDescriptor(getType(Object.class), getType(Class.class)), true);
                            }

                            unboxing(field.getType()).accept(mv);

                            mv.visitFieldInsn(PUTFIELD, getInternalName(actualComponentType), field.getName(), getDescriptor(field.getType()));

                        }

                        mv.visitInsn(RETURN);

                    });
                }
            }.loadAs(ComponentWiring.class)
                    .getDeclaredConstructor(ComponentFactory.class)
                    .newInstance(components);
        } catch (Exception cause) {
            throw new RuntimeException("ComponentWiring instantiation failed", cause);
        }
    }

    static ComponentWiring newComponentWiring(Class<?> componentType, AutowireInterceptor advice, Map<String, Field> autowiring, ComponentFactory components) {

        if (!hasAutowiredFields(componentType) && autowiring.isEmpty()) {
            return ComponentWiring.NO_OP_WIRING;
        }

        try {
            return new OnTheFlyClass("EnhancedComponentWiring", MagicAccessorImpl, ComponentWiring.class) {

                @Override
                protected void emit() {

                    Class<?> actualComponentType = getActualComponentType(componentType);

                    field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);
                    field(ACC_PRIVATE_FINAL, AutowireInterceptor.class, ADVICE);
                    field(ACC_PRIVATE_FINAL, Map.class, JOIN_POINTS);

                    ctor(AutowireInterceptor.class, ComponentFactory.class, Map.class)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .aload_1()
                            .put_field(ADVICE)
                            .aload_0()
                            .aload_2()
                            .put_field(COMPONENTS)
                            .aload_0()
                            .aload_3()
                            .put_field(JOIN_POINTS)
                            .rеturn();

                    method(ACC_PUBLIC_FINAL, void.class, WIRE, Object.class).visit(mv -> {

                        for (Field field : autowiring.values()) {

                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, slashedClassName, ADVICE, getDescriptor(AutowireInterceptor.class));
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, slashedClassName, JOIN_POINTS, getDescriptor(Map.class));
                            mv.visitLdcInsn(field.getName());
                            mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(Map.class), GET, getMethodDescriptor(getType(Object.class), getType(Object.class)), true);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, slashedClassName, COMPONENTS, getDescriptor(ComponentFactory.class));

                            if (field.isAnnotationPresent(Qualified.class)) {
                                mv.visitLdcInsn(field.getAnnotation(Qualified.class).value());
                                mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET_PROVIDER, getMethodDescriptor(getType(ComponentProvider.class), getType(String.class)), true);
                            } else {
                                mv.visitLdcInsn(getType(field.getType()));
                                mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET_PROVIDER, getMethodDescriptor(getType(ComponentProvider.class), getType(Class.class)), true);
                            }

                            mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(AutowireInterceptor.class), INTERCEPT, getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class), Type.getType(Field.class), Type.getType(ComponentProvider.class)), true);

                            unboxing(field.getType()).accept(mv);

                            mv.visitFieldInsn(PUTFIELD, getInternalName(actualComponentType), field.getName(), getDescriptor(field.getType()));
                        }

                        for (Field field : listVerifiedInjectPoints(actualComponentType)) {
                            if (!autowiring.containsKey(field.getName())) {

                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitFieldInsn(GETFIELD, slashedClassName, COMPONENTS, getDescriptor(ComponentFactory.class));

                                if (field.isAnnotationPresent(Qualified.class)) {
                                    mv.visitLdcInsn(field.getAnnotation(Qualified.class).value());
                                    mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET, getMethodDescriptor(getType(Object.class), getType(String.class)), true);
                                } else {
                                    mv.visitLdcInsn(getType(field.getType()));
                                    mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ComponentFactory.class), GET, getMethodDescriptor(getType(Object.class), getType(Class.class)), true);
                                }

                                unboxing(field.getType()).accept(mv);

                                mv.visitFieldInsn(PUTFIELD, getInternalName(actualComponentType), field.getName(), getDescriptor(field.getType()));
                            }
                        }
                        mv.visitInsn(RETURN);
                    });
                }
            }.loadAs(ComponentWiring.class)
                    .getDeclaredConstructor(AutowireInterceptor.class, ComponentFactory.class, Map.class)
                    .newInstance(advice, components, autowiring);
        } catch (Exception cause) {
            throw new RuntimeException("EnhancedComponentWiring instantiation failed", cause);
        }
    }

    static ComponentService newComponentService(ExecutionInterceptor advice, Method initMethod, Class<?> enhancedComponentType, Environment env, ComponentFactory components) {

        Class<?> actualComponentType = getActualComponentType(enhancedComponentType);

        ComponentConstructor enhancedConstructor = newComponentConstructor(enhancedComponentType, initMethod.getParameterTypes());

        MethodProxy initProxy = newInitMethodProxy(initMethod, enhancedConstructor);

        return serviceCache.computeIfAbsent(enhancedComponentType, $ -> {
            try {
                return new OnTheFlyClass("EnhancedComponentService", MagicAccessorImpl, ComponentService.class) {
                    @Override
                    protected void emit() {

                        field(ACC_PRIVATE_FINAL, Environment.class, ENV);
                        field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);
                        field(ACC_PRIVATE_FINAL, ExecutionInterceptor.class, ADVICE);
                        field(ACC_PRIVATE_FINAL, MethodProxy.class, PROXY);
                        field(ACC_PRIVATE_FINAL, Method.class, CONSTRUCTOR);
                        field(ACC_PRIVATE_FINAL, Class[].class, CONSTRUCTOR_PARAM_TYPES);

                        ctor(Environment.class, ComponentFactory.class, ExecutionInterceptor.class, MethodProxy.class, Method.class, Class[].class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(ENV)
                                .aload_0()
                                .aload_2()
                                .put_field(COMPONENTS)
                                .aload_0()
                                .aload_3()
                                .put_field(ADVICE)
                                .aload_0()
                                .aload_4()
                                .put_field(PROXY)
                                .aload_0()
                                .aload_5()
                                .put_field(CONSTRUCTOR)
                                .aload_0()
                                .aload_6()
                                .put_field(CONSTRUCTOR_PARAM_TYPES)
                                .rеturn();

                        MethodBody resolveMethodBody = method(ACC_PUBLIC_FINAL, void.class, RESOLVE, Object.class);

                        for (Field field : listVerifiedPropertyInjectPoints(actualComponentType)) {
                            resolveMethodBody.aload_1()
                                    .aload_0()
                                    .get_field(ENV)
                                    .ldc(field.getAnnotation(Property.class).value())
                                    .invoke_interface(Environment.class, String.class, GET_PROPERTY, String.class)
                                    .visit(unboxing(field.getType()))
                                    .put_field(actualComponentType, field.getName());
                        }

                        resolveMethodBody.rеturn();

                        method(ACC_PUBLIC_FINAL, Object.class, INIT, Object.class)
                                .aload_0()
                                .aload_1()
                                .aload_0()
                                .get_field(COMPONENTS)
                                .aload_0()
                                .get_field(CONSTRUCTOR_PARAM_TYPES)
                                .invoke_interface(ComponentFactory.class, Object[].class, LIST_ALL, Class[].class)
                                .invoke_virtual(Object.class, INIT, Object.class, Object[].class)
                                .arеturn();

                        method(ACC_PUBLIC_FINAL, Object.class, INIT, Object.class, Object[].class)
                                .aload_0()
                                .get_field(ADVICE)
                                .aload_0()
                                .invoke_virtual(Class.class, GET_CLASS)
                                .aload_1()
                                .aload_0()
                                .get_field(CONSTRUCTOR)
                                .aload_2()
                                .aload_0()
                                .get_field(PROXY)
                                .invoke_interface(ExecutionInterceptor.class, Object.class, INTERCEPT, Class.class, Object.class, Method.class, Object[].class, MethodProxy.class)
                                .aload_1()
                                .arеturn();

                        method(ACC_PUBLIC_FINAL, void.class, INITIALIZE, Object.class).visit(mv -> {

                            for (Method method : listVerifiedInitializeMethod(actualComponentType)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), method.getName(), getMethodDescriptor(method), false);
                            }

                            mv.visitInsn(RETURN);

                        });

                        method(ACC_PUBLIC_FINAL, void.class, UTILIZE, Object.class).visit(mv -> {

                            for (Method method : listVerifiedUtilizeMethod(actualComponentType)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), method.getName(), getMethodDescriptor(method), false);
                            }

                            mv.visitInsn(RETURN);

                        });

                    }
                }.loadAs(ComponentService.class)
                        .getConstructor(Environment.class, ComponentFactory.class, ExecutionInterceptor.class, MethodProxy.class, Method.class, Class[].class)
                        .newInstance(env, components, advice, initProxy, initMethod, initMethod.getParameterTypes());
            } catch (Exception cause) {
                throw new RuntimeException("EnhancedComponentService instantiation failed", cause);
            }
        });

    }

    static MethodHandleFactory newMethodHandleFactory() {
        try {
            return new OnTheFlyClass("MethodHandleFactory", MagicAccessorImpl, MethodHandleFactory.class) {
                @Override
                protected void emit() {

                    field(ACC_PRIVATE_FINAL, MethodHandles.Lookup.class, LOOKUP);

                    ctor(ACC_PUBLIC)
                            .aload_0()
                            .invoke_special(MagicAccessorImpl, void.class, ctor)
                            .aload_0()
                            .nеw(MethodHandles.Lookup.class)
                            .dup()
                            .aload_0()
                            .invoke_virtual(Class.class, GET_CLASS)
                            .get_static(Utils.class, "LOOKUP_ACCESS")
                            .invoke_special(MethodHandles.Lookup.class, void.class, ctor, Class.class, int.class)
                            .put_field(LOOKUP)
                            .rеturn();

                    method(ACC_PUBLIC_FINAL, MethodHandle.class, BIND, Method.class)
                            .aload_0()
                            .get_field(LOOKUP)
                            .aload_1()
                            .invoke_virtual(MethodHandles.Lookup.class, MethodHandle.class, UNREFLECT, Method.class)
                            .arеturn();

                }
            }.loadAs(MethodHandleFactory.class).newInstance();
        } catch (Exception cause) {
            throw new RuntimeException("MethodHandleFactory instantiation failed", cause);
        }
    }

    static ComponentService newComponentService(Constructor<?> componentConstructor, Class<?> componentType, Environment env, ComponentFactory components) {

        Class<?> actualComponentType = getActualComponentType(componentType);

        return serviceCache.computeIfAbsent(componentType, $ -> {
            try {
                return new OnTheFlyClass("ComponentService", MagicAccessorImpl, ComponentService.class) {
                    @Override
                    protected void emit() {

                        field(ACC_PRIVATE_FINAL, Environment.class, ENV);
                        field(ACC_PRIVATE_FINAL, ComponentFactory.class, COMPONENTS);

                        ctor(Environment.class, ComponentFactory.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(ENV)
                                .aload_0()
                                .aload_2()
                                .put_field(COMPONENTS)
                                .rеturn();

                        MethodBody resolveMethodBody = method(ACC_PUBLIC_FINAL, void.class, RESOLVE, Object.class);

                        for (Field field : listVerifiedPropertyInjectPoints(actualComponentType)) {
                            resolveMethodBody.aload_1()
                                    .aload_0()
                                    .get_field(ENV)
                                    .ldc(field.getAnnotation(Property.class).value())
                                    .invoke_interface(Environment.class, String.class, GET_PROPERTY, String.class)
                                    .visit(unboxing(field.getType()))
                                    .put_field(actualComponentType, field.getName());
                        }

                        resolveMethodBody.rеturn();

                        MethodBody constructMethodBody = method(ACC_PUBLIC_FINAL, Object.class, INIT, Object.class);

                        constructMethodBody
                                .aload_1()
                                .dup();

                        for (Parameter parameter : componentConstructor.getParameters()) {
                            if (parameter.isAnnotationPresent(Qualified.class)) {
                                constructMethodBody.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getAnnotation(Qualified.class).value())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, String.class)
                                        .visit(unboxing(parameter.getType()));
                            } else {
                                constructMethodBody.aload_0()
                                        .get_field(COMPONENTS)
                                        .ldc(parameter.getType())
                                        .invoke_interface(ComponentFactory.class, Object.class, GET, Class.class)
                                        .visit(unboxing(parameter.getType()));
                            }
                        }

                        constructMethodBody.invoke_special(actualComponentType, void.class, ctor, componentConstructor.getParameterTypes())
                                .arеturn();

                        method(ACC_PUBLIC_FINAL, void.class, INITIALIZE, Object.class).visit(mv -> {

                            for (Method method : listVerifiedInitializeMethod(actualComponentType)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), method.getName(), getMethodDescriptor(method), false);
                            }

                            mv.visitInsn(RETURN);

                        });

                        method(ACC_PUBLIC_FINAL, void.class, UTILIZE, Object.class).visit(mv -> {

                            for (Method method : listVerifiedUtilizeMethod(actualComponentType)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), method.getName(), getMethodDescriptor(method), false);
                            }

                            mv.visitInsn(RETURN);

                        });
                    }
                }.loadAs(ComponentService.class)
                        .getConstructor(Environment.class, ComponentFactory.class)
                        .newInstance(env, components);
            } catch (Exception cause) {
                throw new RuntimeException("ComponentService instantiation failed", cause);
            }
        });

    }

    static ComponentService newComponentService(Environment env, String utilizeBy, String initializeBy, Class<?> componentType) {

        Class<?> actualComponentType = getActualComponentType(componentType);

        return serviceCache.computeIfAbsent(componentType, $ -> {
            try {
                return new OnTheFlyClass("ComponentService", MagicAccessorImpl, ComponentService.class) {
                    @Override
                    protected void emit() {

                        field(ACC_PRIVATE_FINAL, Environment.class, ENV);

                        ctor(Environment.class)
                                .aload_0()
                                .invoke_special(MagicAccessorImpl, void.class, ctor)
                                .aload_0()
                                .aload_1()
                                .put_field(ENV)
                                .rеturn();

                        MethodBody resolveMethodBody = method(ACC_PUBLIC_FINAL, void.class, RESOLVE, Object.class);

                        for (Field field : listVerifiedPropertyInjectPoints(actualComponentType)) {
                            resolveMethodBody.aload_1()
                                    .aload_0()
                                    .get_field(ENV)
                                    .ldc(field.getAnnotation(Property.class).value())
                                    .invoke_interface(Environment.class, String.class, GET_PROPERTY, String.class)
                                    .visit(unboxing(field.getType()))
                                    .put_field(actualComponentType, field.getName());
                        }

                        resolveMethodBody.rеturn();

                        method(ACC_PUBLIC_FINAL, void.class, INITIALIZE, Object.class).visit(mv -> {

                            if (nonNull(initializeBy)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), initializeBy, getMethodDescriptor(VOID_TYPE), false);
                            }

                            mv.visitInsn(RETURN);

                        });

                        method(ACC_PUBLIC_FINAL, void.class, UTILIZE, Object.class).visit(mv -> {

                            if (nonNull(utilizeBy)) {
                                mv.visitVarInsn(ALOAD, 1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(actualComponentType), utilizeBy, getMethodDescriptor(VOID_TYPE), false);
                            }

                            mv.visitInsn(RETURN);

                        });
                    }
                }.loadAs(ComponentService.class)
                        .getConstructor(Environment.class)
                        .newInstance(env);
            } catch (Exception cause) {
                throw new RuntimeException("ComponentService instantiation failed", cause);
            }
        });
    }
}
