package scg.fusion;

import scg.fusion.annotation.*;
import scg.fusion.aop.JoinPoint;
import scg.fusion.exceptions.IllegalContractException;
import scg.fusion.messaging.DeadLetter;
import scg.fusion.messaging.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.reflect.Modifier.*;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.of;

final class Verification {

    private Verification() {
        throw new UnsupportedOperationException();
    }

    static ComponentScopeServiceDecorator getVerifiedScopeDecorator(AnnotatedElement componentTypeOrMethod) {

        boolean success = false;

        Annotation annotation = (null);

        Class<? extends ComponentScopeServiceDecorator> decoratorType = (null);

        for (Annotation declaredAnnotation : componentTypeOrMethod.getDeclaredAnnotations()) {
            if (success) {
                throw new IllegalContractException("duplicated @Scope found, component type [%s]", componentTypeOrMethod);
            } else if (isScopeAnnotationPresent(declaredAnnotation)) {
                if (success) {
                    throw new IllegalContractException("duplicated @Scope found, component type [%s]", componentTypeOrMethod);
                } else {

                    Class<? extends Annotation> annotationType = declaredAnnotation.annotationType();

                    Class<? extends ComponentScopeServiceDecorator> scopeDecoratorType = (annotationType.getAnnotation(Scope.class)).decorator();

                    if (ComponentScopeServiceDecorator.class == scopeDecoratorType.getSuperclass()) {

                        Type actualAnnotationType = (((ParameterizedType) scopeDecoratorType.getGenericSuperclass()).getActualTypeArguments())[0];

                        if (annotationType == actualAnnotationType) {
                            decoratorType = scopeDecoratorType;
                            annotation = declaredAnnotation;
                            success = true;
                        } else {
                            throw new IllegalContractException("component scope decorator [%s] has illegal annotation type parameter [%s] expected [%s]", decoratorType, actualAnnotationType, annotationType);
                        }
                    } else {
                        throw new IllegalContractException("component scope decorator [%s] must extends [%s] directly", decoratorType, ComponentScopeServiceDecorator.class);
                    }

                }
            }
        }

        if (success) try {

            ComponentScopeServiceDecorator decorator = decoratorType.newInstance();

            decorator.annotation = annotation;

            return decorator;

        } catch (InstantiationException | IllegalAccessException cause) {
            throw new RuntimeException(format("Cannot instantiate component scope decorator [%s]", decoratorType), cause);
        }

        return new SingletonComponentScopeServiceDecorator();

    }

    static boolean isScopeAnnotationPresent(Annotation annotation) {
        return (annotation.annotationType()).isAnnotationPresent(Scope.class);
    }

    static Iterable<Field> listVerifiedPropertyInjectPoints(Class<?> componentType) {

        HashSet<Field> fields = new HashSet<>();

//        for (Field field : componentType.getDeclaredFields()) {
//            if (isVerifiedPropertyInjectPoint(field)) {
//                fields.add(field);
//            }
//        }

        return fields;

    }

    static boolean isVerifiedPropertyInjectPoint(Field field) {

        if (field.isAnnotationPresent(Property.class)) {

//            Class<?> type = field.getType();

//            if (!(type.isPrimitive()) || type != String.class) {
//                throw new IllegalContractException("@Property [%s] must be type of primitive ot java.lang.String", field);
//            }

//            int modifiers = type.getModifiers();
//
//            if (isStatic(modifiers)) {
//                throw new IllegalContractException("@Property [%s] cannot be static", field);
//            }

//            if (isFinal(modifiers)) {
//                throw new IllegalContractException("@Property [%s] cannot be final", field);
//            }

            return true;

        }

        return false;

    }

    static Iterable<Field> listVerifiedInjectPoints(Class<?> componentType) {

        HashSet<Field> fields = new HashSet<>();

        for (Field field : componentType.getDeclaredFields()) {
            if (isVerifiedInjectPoint(field)) {
                fields.add(field);
            }
        }

        return fields;

    }

    static boolean isVerifiedInjectPoint(Field field) {

        if (field.isAnnotationPresent(Autowired.class)) {

            Class<?> type = field.getType();

            if (type.isPrimitive()) {
                throw new IllegalContractException("@Autowired [%s] cannot be type of primitive", field);
            }

            int modifiers = type.getModifiers();

            if (isStatic(modifiers)) {
                throw new IllegalContractException("@Autowired [%s] cannot be static", field);
            }

            if (isFinal(modifiers)) {
                throw new IllegalContractException("@Autowired [%s] cannot be final", field);
            }

            return true;

        }

        return false;

    }

    static Iterable<Method> listVerifiedMessageListeners(Class<?> componentType) {
        return of(componentType.getDeclaredMethods()).filter(method -> isVerifiedMessageListener(method) || isVerifiedDlqListener(method)).collect(toSet());
    }

    static Iterable<Method> listVerifiedFactories(Class<?> componentType) {
        return Stream.of(componentType.getDeclaredMethods()).filter(Verification::isVerifiedFactoryMethod).collect(toSet());
    }

    static Iterable<Method> listVerifiedUtilizeMethod(Class<?> componentType) {

        HashSet<Method> methods = new HashSet<>();

        for (Method method : componentType.getDeclaredMethods()) {
            if (isVerifiedUtilizeHook(method)) {
                methods.add(method);
            }
        }

        return methods;

    }

    static Iterable<Method> listVerifiedInitializeMethod(Class<?> componentType) {

        HashSet<Method> methods = new HashSet<>();

        for (Method method : componentType.getDeclaredMethods()) {
            if (isVerifiedInitializeHook(method)) {
                methods.add(method);
            }
        }

        return methods;

    }

    static boolean isVerifiedUtilizeHook(Method method) {

        if (method.isAnnotationPresent(Utilize.class)) {
            verifyUtilizeHook(method);
            return true;
        }

        return false;
    }

    static boolean isVerifiedInitializeHook(Method method) {

        if (method.isAnnotationPresent(Initialize.class)) {
            verifyInitializeHook(method);
            return true;
        }

        return false;

    }

    static String getVerifiedInitializeByMethodName(Method factory) {

        if (!factory.isAnnotationPresent(InitializeBy.class)) {
            return (null);
        }

        boolean found = false;

        String methodName = factory.getAnnotation(InitializeBy.class).value();

        Class<?> componentType = factory.getReturnType();

        for (Method method : componentType.getDeclaredMethods()) {
            if (!found) {
                if (methodName.equals(method.getName())) {
                    verifyInitializeHook(method);
                    found = true;
                }
            } else {
                break;
            }
        }

        if (!found) {
            throw new IllegalContractException("@InitializeBy [%s] method not found", methodName);
        }

        return methodName;

    }

    static String getVerifiedUtilizeMethodName(Method factory) {

        if (!factory.isAnnotationPresent(UtilizeBy.class)) {
            return (null);
        }

        boolean found = false;

        String methodName = factory.getAnnotation(UtilizeBy.class).value();

        Class<?> componentType = factory.getReturnType();

        for (Method method : componentType.getDeclaredMethods()) {
            if (!found) {
                if (methodName.equals(method.getName())) {
                    verifyUtilizeHook(method);
                    found = true;
                }
            } else {
                break;
            }
        }

        if (!found) {
            throw new IllegalContractException("@UtilizeBy [%s] method not found", methodName);
        }

        return methodName;

    }

    static void verifyInitializeHook(Method method) {

        Class<?> returnType = method.getReturnType();

        if (returnType != void.class) {
            throw new IllegalContractException("initialize hook method must have void return type");
        }

        int parameterCount = method.getParameterCount();

        if (parameterCount != 0) {
            throw new IllegalContractException("initialize hook [%s] method must have no params", method);
        }

        int modifiers = method.getModifiers();

        if (isStatic(modifiers)) {
            throw new IllegalContractException("initialize hook [%s] cannot be static", method);
        }

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("initialize hook [%s] cannot be abstract", method);
        }

    }

    static void verifyUtilizeHook(Method method) {

        Class<?> returnType = method.getReturnType();

        if (returnType != void.class) {
            throw new IllegalContractException("utilize hook method must have void return type");
        }

        int parameterCount = method.getParameterCount();

        if (parameterCount != 0) {
            throw new IllegalContractException("utilize hook [%s] method must have no params", method);
        }

        int modifiers = method.getModifiers();

        if (isStatic(modifiers)) {
            throw new IllegalContractException("utilize hook [%s] cannot be static", method);
        }

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("utilize hook [%s] cannot be abstract", method);
        }

    }

    static void verifyAdvice(Method advice) {

        int modifiers = advice.getModifiers();

        if (isStatic(modifiers)) {
            throw new IllegalContractException("advice [%s] cannot be static", advice);
        }

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("advice [%s] cannot be abstract", advice);
        }

        Class<?> returnType = advice.getReturnType();

        if (returnType != Object.class) {
            throw new IllegalContractException("advice [%s] must have return type of java.lang.Object", advice);
        }

        int parameterCount = advice.getParameterCount();

        if (parameterCount != 1) {
            throw new IllegalContractException("advice [%s] has illegal parameters count (must have exactly one)", advice);
        }

        Class<?> parameterType = advice.getParameterTypes()[0];

        if (!JoinPoint.class.isAssignableFrom(parameterType)) {
            throw new IllegalContractException("advice [%s] has illegal parameter type (must have subtype of the scg.fusion.aop.JoinPoint)", advice);
        }
    }

    static void verifyMessageListener(Method listener) {

        int modifiers = listener.getModifiers();

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("@MessageListener [%s] cannot be abstract", listener);
        }

        Class<?> returnType = listener.getReturnType();

        if (returnType != void.class) {
            throw new IllegalContractException("@MessageListener [%s] must have void return type", listener);
        }

        int parameterCount = listener.getParameterCount();

        if (parameterCount != 1) {
            throw new IllegalContractException("@MessageListener [%s] has illegal parameters count (must have exactly one)", listener);
        }

        Class<?> parameterType = listener.getParameterTypes()[0];

        if (parameterType != Message.class) {
            throw new IllegalContractException("@MessageListener [%s] has illegal parameter type (must have exactly type of scg.fusion.messaging.Message)", listener);
        }

    }

    static void verifyDlqListener(Method listener) {

        int modifiers = listener.getModifiers();

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("@DlqListener [%s] cannot be abstract", listener);
        }

        Class<?> returnType = listener.getReturnType();

        if (returnType != void.class) {
            throw new IllegalContractException("@DlqListener [%s] must have void return type", listener);
        }

        int parameterCount = listener.getParameterCount();

        if (parameterCount != 1) {
            throw new IllegalContractException("@DlqListener [%s] has illegal parameters count (must have exactly one)", listener);
        }

        Class<?> parameterType = listener.getParameterTypes()[0];

        if (parameterType != DeadLetter.class) {
            throw new IllegalContractException("@DlqListener [%s] has illegal parameter type (must have exactly type of scg.fusion.messaging.DeadLetter)", listener);
        }

    }

    static void verifyFactory(Method factory) {

        int modifiers = factory.getModifiers();

        if (isAbstract(modifiers)) {
            throw new IllegalContractException("@Factory [%s] cannot be abstract", factory);
        }

        Class<?> returnType = factory.getReturnType();

        if ((returnType.isPrimitive()) || returnType == void.class || returnType == String.class) {
            throw new IllegalContractException("@Factory [%s] has illegal return type (cannot be primitive, void or java.lang.String)", factory);
        }

    }

    static boolean isVerifiedDlqListener(Method method) {
        return isDlqListener(method, true);
    }

    static boolean isDlqListener(Method method, boolean verify) {

        if (method.isAnnotationPresent(DlqListener.class)) {

            if (verify) {
                verifyDlqListener(method);
            }

            return true;

        }

        return false;

    }

    static boolean isVerifiedMessageListener(Method method) {
        return isMessageListener(method, true);
    }

    static boolean isMessageListener(Method method, boolean verify) {

        if (method.isAnnotationPresent(MessageListener.class)) {

            if (verify) {
                verifyMessageListener(method);
            }

            return true;

        }

        return false;

    }

    static boolean isVerifiedFactoryMethod(Method method) {
        return isFactoryMethod(method, true);
    }

    static boolean isFactoryMethod(Method method, boolean verify) {

        if (method.isAnnotationPresent(Factory.class)) {

            if (verify) {
                verifyFactory(method);
            }

            return true;

        }

        return false;

    }

}
