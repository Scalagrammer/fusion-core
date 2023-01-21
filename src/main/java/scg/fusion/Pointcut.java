package scg.fusion;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.*;
import scg.fusion.PointcutExpressionParser.ParameterTypeVarianceContext;
import scg.fusion.exceptions.PointcutExpressionSyntaxError;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.util.Objects.nonNull;
import static scg.fusion.Pointcuts.*;
import static scg.fusion.Utils.*;

public interface Pointcut extends ExecutionPointcut, AutowirePointcut {

    default boolean isExecution() {
        return false;
    }

    default boolean isDivergent() {
        return false;
    }

    default boolean isAutowire() {
        return false;
    }

    default boolean convergentWith(Pointcut that) {
        return (isExecution() & that.isExecution()) ^ (isAutowire() & that.isAutowire());
    }

    static Pointcut parse(String expression) {
        return PointcutParser.parse(expression);
    }

}

interface AutowirePointcut {

    default boolean match(Field field) {
        return match(field.getDeclaringClass(), field);
    }

    default boolean match(Class<?> targetType, Field field) {
        return false;
    }

}

interface ExecutionPointcut {

    default boolean isWithinGuard() {
        return false;
    }

    default boolean match(Class<?> callSide, Method method) {
        return false;
    }

    default boolean matchWithin(Class<?> callSide, Method method) {
        return isWithinGuard() && match(callSide, method);
    }

    default boolean match(Method method) {
        return isWithinGuard() || match((null), method);
    }

}

final class Pointcuts {

    private static final Map<String, Class<?>> paramTypeCache = new ConcurrentHashMap<>();

    static Maybe<Pointcut> divergentCrosscut(List<Pointcut> pointcuts) {

        if (pointcuts.isEmpty()) {
            return Maybe.empty();
        }

        Pointcut crosscut = pointcuts.remove(0);

        for (Pointcut pointcut : pointcuts) {
            crosscut = divergentOr(crosscut, pointcut);
        }

        return Maybe.pure(crosscut);

    }

    static Maybe<Pointcut> executionCrosscut(List<Pointcut> pointcuts) {

        if (pointcuts.isEmpty()) {
            return Maybe.empty();
        }

        Pointcut crosscut = pointcuts.remove(0);

        for (Pointcut pointcut : pointcuts) {
            if (pointcut.convergentWith(crosscut)) {
                crosscut = executionOr(crosscut, pointcut);
            } else {
                throw new DivergentCrosscutException();
            }
        }

        return Maybe.pure(crosscut);

    }

    static Maybe<Pointcut> autowireCrosscut(List<Pointcut> pointcuts) {

        if (pointcuts.isEmpty()) {
            return Maybe.empty();
        }

        Pointcut crosscut = pointcuts.remove(0);

        for (Pointcut pointcut : pointcuts) {
            if (pointcut.convergentWith(crosscut)) {
                crosscut = autowireOr(crosscut, pointcut);
            } else {
                throw new DivergentCrosscutException();
            }
        }

        return Maybe.pure(crosscut);

    }

    static Pointcut negateAutowire(Pointcut pointcut) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return pointcut.isWithinGuard();
            }

            @Override
            public boolean isAutowire() {
                return pointcut.isAutowire();
            }

            @Override
            public boolean match(Class<?> targetType, Field field) {
                return !pointcut.match(targetType, field);
            }

        };
    }

    static Pointcut negateExecution(Pointcut pointcut) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return pointcut.isWithinGuard();
            }

            @Override
            public boolean isExecution() {
                return pointcut.isExecution();
            }

            @Override
            public boolean match(Class<?> callSide, Method method) {
                return !pointcut.match(callSide, method);
            }

        };
    }

    static Pointcut execution(String returnTypeName, String methodName, String[] paramTypeNames) {
        return new Pointcut() {

            final int paramCount = paramTypeNames.length;

            final boolean requiredMethodNameCheck = !methodName.equals("*");
            final boolean requiredReturnTypeNameCheck = !returnTypeName.equals("*");
            final boolean requiredParamTypeNamesCheck = (paramTypeNames.length == 0 || !(paramTypeNames[0].equals("..")));

            final boolean requiredCheck = requiredReturnTypeNameCheck || requiredMethodNameCheck || requiredParamTypeNamesCheck;

            @Override
            public boolean match(Class<?> $, Method method) {

                if (requiredCheck) {

                    boolean success = true;

                    if (requiredReturnTypeNameCheck) {
                        success = returnTypeName.equals(method.getReturnType().getCanonicalName());
                    }

                    if (success && requiredMethodNameCheck) {
                        success = methodName.equals(method.getName());
                    }

                    if (success && requiredParamTypeNamesCheck) {

                        Class<?>[] parameterTypes = method.getParameterTypes();

                        if (paramCount != parameterTypes.length) {
                            return false;
                        } else for (int i = 0; i < paramCount; i++) {

                            String paramTypeName  =  paramTypeNames[i];

                            Class<?> parameterType = parameterTypes[i];

                            if (-1 == paramTypeName.lastIndexOf(PLUS_CHAR)) {
                                success = paramTypeName.equals(parameterType.getCanonicalName());
                            } else {
                                success = getParamTypeForName(paramTypeName).isAssignableFrom(parameterType);
                            }

                            if (!success) {
                                break;
                            }
                        }
                    }

                    return success;

                }

                return true;

            }

            @Override
            public boolean isExecution() {
                return true;
            }

        };
    }

    static Pointcut atExecution(String packageName, String annotationName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredAnnotationNameCheck = !annotationName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredAnnotationNameCheck;

            @Override
            public boolean match(Class<?> $, Method method) {

                Annotation[] declaredAnnotations = method.getDeclaredAnnotations();

                if (requiredCheck) {

                    for (Annotation annotation : declaredAnnotations) {

                        Class<?> annotationType = annotation.annotationType();

                        boolean success = true;

                        if (success && requiredPackageNameCheck) {
                            success = packageName.equals(getPackageName(annotationType));
                        }

                        if (success && requiredAnnotationNameCheck) {
                            success = annotationName.equals(annotationType.getSimpleName());
                        }

                        if (success) {
                            return true;
                        }

                    }

                    return false;

                }

                return declaredAnnotations.length != 0;

            }

            @Override
            public boolean isExecution() {
                return true;
            }

        };
    }

    static Pointcut executionAnd(Pointcut a, Pointcut b) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return a.isWithinGuard() || b.isWithinGuard();
            }

            @Override
            public boolean isExecution() {
                return a.isExecution() & b.isExecution();
            }

            @Override
            public boolean match(Class<?> callSide, Method method) {
                return a.match(callSide, method) && b.match(callSide, method);
            }

        };
    }

    static Pointcut autowireAnd(Pointcut a, Pointcut b) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return a.isWithinGuard() || b.isWithinGuard();
            }

            @Override
            public boolean isAutowire() {
                return a.isAutowire() & b.isAutowire();
            }

            @Override
            public boolean match(Class<?> targetType, Field field) {
                return a.match(targetType, field) && b.match(targetType, field);
            }

        };
    }

    static Pointcut executionOr(Pointcut a, Pointcut b) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return a.isWithinGuard() || b.isWithinGuard();
            }

            @Override
            public boolean isExecution() {
                return a.isExecution() & b.isExecution();
            }

            @Override
            public boolean match(Class<?> callSide, Method method) {
                return a.match(callSide, method) || b.match(callSide, method);
            }

        };
    }

    static Pointcut divergentOr(Pointcut a, Pointcut b) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return a.isWithinGuard() || b.isWithinGuard();
            }

            @Override
            public boolean isDivergent() {
                return true;
            }

            @Override
            public boolean match(Class<?> callSide, Method method) {
                return a.match(callSide, method) || b.match(callSide, method);
            }

            @Override
            public boolean match(Class<?> targetType, Field field) {
                return a.match(targetType, field) || b.match(targetType, field);
            }

        };
    }

    static Pointcut autowireOr(Pointcut a, Pointcut b) {
        return new Pointcut() {

            @Override
            public boolean isWithinGuard() {
                return a.isWithinGuard() || b.isWithinGuard();
            }

            @Override
            public boolean isAutowire() {
                return a.isAutowire() & b.isAutowire();
            }

            @Override
            public boolean match(Class<?> targetType, Field field) {
                return a.match(targetType, field) || b.match(targetType, field);
            }

        };
    }

    static Pointcut autowire(String packageName, String typeName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredTypeNameCheck = !typeName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredTypeNameCheck;

            @Override
            public boolean match(Class<?> $, Field field) {

                if (requiredCheck) {

                    Class<?> fieldType = field.getType();

                    boolean success = true;

                    if (success && requiredPackageNameCheck) {
                        success = packageName.equals(getPackageName(fieldType));
                    }

                    if (success && requiredTypeNameCheck) {
                        success = typeName.equals(fieldType.getSimpleName());
                    }

                    return success;

                }

                return true;

            }

            @Override
            public boolean isAutowire() {
                return true;
            }
        };
    }

    static Pointcut atAutowire(String packageName, String annotationName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredAnnotationNameCheck = !annotationName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredAnnotationNameCheck;

            @Override
            public boolean match(Class<?> $, Field field) {

                Annotation[] declaredAnnotations = field.getDeclaredAnnotations();

                if (requiredCheck) {

                    for (Annotation annotation : declaredAnnotations) {

                        Class<?> annotationType = annotation.annotationType();

                        boolean success = true;

                        if (requiredPackageNameCheck) {
                            success = packageName.equals(getPackageName(annotationType));
                        }

                        if (success && requiredAnnotationNameCheck) {
                            success = annotationName.equals(annotationType.getSimpleName());
                        }

                        if (success) {
                            return true;
                        }

                    }

                    return false;

                }

                return declaredAnnotations.length != 0;

            }

            @Override
            public boolean isAutowire() {
                return true;
            }

        };
    }

    static Pointcut autowireTarget(String packageName, String targetName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredTargetNameCheck = !targetName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredTargetNameCheck;

            @Override
            public boolean match(Class<?> targetType, Field $) {

                if (requiredCheck) {

                    boolean success = true;

                    if (requiredPackageNameCheck) {
                        success = packageName.equals(getPackageName(targetType));
                    }

                    if (success && requiredTargetNameCheck) {
                        success = targetName.equals(targetType.getSimpleName());
                    }

                    return success;

                }

                return true;

            }

            @Override
            public boolean isAutowire() {
                return true;
            }
        };
    }

    static Pointcut executionTarget(String packageName, String targetName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredTargetNameCheck = !targetName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredTargetNameCheck;

            @Override
            public boolean match(Class<?> $, Method method) {

                if (requiredCheck) {

                    boolean success = true;

                    Class<?> targetType = method.getDeclaringClass();

                    if (requiredPackageNameCheck) {
                        success = packageName.equals(getPackageName(targetType));
                    }

                    if (success && requiredTargetNameCheck) {
                        success = targetName.equals(targetType.getSimpleName());
                    }

                    return success;

                }

                return true;

            }

            @Override
            public boolean isExecution() {
                return true;
            }
        };
    }

    static Pointcut within(String packageName, String withinName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredWithinNameCheck = !withinName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredWithinNameCheck;

            @Override
            public boolean isWithinGuard() {
                return requiredCheck;
            }

            @Override
            public boolean isExecution() {
                return true;
            }

            @Override
            public boolean match(Class<?> callSide, Method $) {

                if (requiredCheck) {

                    boolean success = true;

                    if (requiredPackageNameCheck) {
                        success = packageName.equals(getPackageName(callSide));
                    }

                    if (success && requiredWithinNameCheck) {
                        success = withinName.equals(callSide.getSimpleName());
                    }

                    return success;

                }

                return true;

            }
        };
    }

    static Pointcut atWithin(String packageName, String annotationName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredAnnotationNameCheck = !annotationName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredAnnotationNameCheck;

            @Override
            public boolean isWithinGuard() {
                return requiredCheck;
            }

            @Override
            public boolean isExecution() {
                return true;
            }

            @Override
            public boolean match(Class<?> callSide, Method $) {

                Annotation[] declaredAnnotations = callSide.getDeclaredAnnotations();

                if (requiredCheck) {

                    for (Annotation annotation : declaredAnnotations) {

                        Class<?> annotationType = annotation.annotationType();

                        boolean success = true;

                        if (requiredPackageNameCheck) {
                            success = packageName.equals(getPackageName(annotationType));
                        }

                        if (success && requiredAnnotationNameCheck) {
                            success = annotationName.equals(annotationType.getSimpleName());
                        }

                        if (success) {
                            return true;
                        }

                    }

                    return false;

                }

                return declaredAnnotations.length != 0;

            }

        };
    }

    static Pointcut atExecutionTarget(String packageName, String annotationName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredAnnotationNameCheck = !annotationName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredAnnotationNameCheck;

            @Override
            public boolean match(Class<?> $, Method method) {

                Class<?> targetType = method.getDeclaringClass();

                Annotation[] declaredAnnotations = targetType.getDeclaredAnnotations();

                if (requiredCheck) {

                    for (Annotation annotation : declaredAnnotations) {

                        Class<?> annotationType = annotation.annotationType();

                        boolean success = true;

                        if (requiredPackageNameCheck) {
                            success = packageName.equals(getPackageName(annotationType));
                        }

                        if (success && requiredAnnotationNameCheck) {
                            success = annotationName.equals(annotationType.getSimpleName());
                        }

                        if (success) {
                            return true;
                        }

                    }

                    return false;

                }

                return declaredAnnotations.length != 0;

            }

            @Override
            public boolean isExecution() {
                return true;
            }
        };
    }

    static Pointcut atAutowireTarget(String packageName, String annotationName) {
        return new Pointcut() {

            final boolean requiredPackageNameCheck = !packageName.equals("*");
            final boolean requiredAnnotationNameCheck = !annotationName.equals("*");

            final boolean requiredCheck = requiredPackageNameCheck || requiredAnnotationNameCheck;

            @Override
            public boolean match(Class<?> targetType, Field $) {

                Annotation[] declaredAnnotations = targetType.getDeclaredAnnotations();

                if (requiredCheck) {

                    for (Annotation annotation : declaredAnnotations) {

                        Class<?> annotationType = annotation.annotationType();

                        boolean success = true;

                        if (requiredPackageNameCheck) {
                            success = packageName.equals(getPackageName(annotationType));
                        }

                        if (success && requiredAnnotationNameCheck) {
                            success = annotationName.equals(annotationType.getSimpleName());
                        }

                        if (success) {
                            return true;
                        }

                    }

                    return false;

                }

                return declaredAnnotations.length != 0;

            }

            @Override
            public boolean isAutowire() {
                return true;
            }

        };
    }

    private static Class<?> getParamTypeForName(String canonicalName) {
        return paramTypeCache.computeIfAbsent(canonicalName, $ -> {
            try {
                return Class.forName(canonicalName.replace(PLUS_CHAR, EMPTY_CHAR));
            } catch (ClassNotFoundException cause) {
                throw new RuntimeException("Cannot get param type for name", cause);
            }
        });
    }

}

final class PointcutParser {

    private PointcutParser() {
        throw new UnsupportedOperationException();
    }

    private static PointcutExpressionBaseVisitor<String[]> parameterTypeListVisitor = new PointcutExpressionBaseVisitor<String[]>() {
        @Override
        public String[] visitParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx) {
            if (ctx.DOT().isEmpty()) {

                List<ParameterTypeVarianceContext> variance = ctx.parameterTypeVariance();

                return variance.stream()
                        .flatMap(vctx -> vctx.parameterType().stream())
                        .map(parameterTypeContext -> {

                            if (nonNull(parameterTypeContext.covariantReferenceType())) {
                                return parameterTypeContext.covariantReferenceType().getText();
                            }

                            if (nonNull(parameterTypeContext.referenceType())) {
                                return parameterTypeContext.referenceType().getText();
                            }

                            if (nonNull(parameterTypeContext.primitiveType())) {
                                return parameterTypeContext.primitiveType().getText();
                            }

                            if (nonNull(parameterTypeContext.arrayType())) {
                                return parameterTypeContext.arrayType().getText();
                            }

                            if (nonNull(parameterTypeContext.ASTERISK())) {
                                return parameterTypeContext.ASTERISK().getText();
                            }

                            throw new IllegalStateException();

                        }).toArray(String[]::new);
            } else {
                return new String[]{".."};
            }
        }
    };

    private static PointcutExpressionBaseVisitor<String> typeNameVisitor = new PointcutExpressionBaseVisitor<String>() {
        @Override
        public String visitTypeName(PointcutExpressionParser.TypeNameContext ctx) {

            if (nonNull(ctx.Identifier())) {
                return ctx.Identifier().getText();
            }

            if (nonNull(ctx.ASTERISK())) {
                return ctx.ASTERISK().getText();
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<String> returnTypeVisitor = new PointcutExpressionBaseVisitor<String>() {
        @Override
        public String visitReturnType(PointcutExpressionParser.ReturnTypeContext ctx) {

            if (nonNull(ctx.voidType())) {
                return ctx.voidType().getText();
            }

            if (nonNull(ctx.referenceType())) {
                return ctx.referenceType().getText();
            }

            if (nonNull(ctx.primitiveType())) {
                return ctx.primitiveType().getText();
            }

            if (nonNull(ctx.arrayType())) {
                return ctx.arrayType().getText();
            }

            if (nonNull(ctx.ASTERISK())) {
                return ctx.ASTERISK().getText();
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<String> packageNameVisitor = new PointcutExpressionBaseVisitor<String>() {
        @Override
        public String visitPackageName(PointcutExpressionParser.PackageNameContext ctx) {

            if (nonNull(ctx.ASTERISK())) {
                return ctx.ASTERISK().getText();
            }

            if (nonNull(ctx.referenceType())) {
                return ctx.referenceType().getText();
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<String> methodNameVisitor = new PointcutExpressionBaseVisitor<String>() {
        @Override
        public String visitMethodName(PointcutExpressionParser.MethodNameContext ctx) {

            if (nonNull(ctx.Identifier())) {
                return ctx.Identifier().getText();
            }

            if (nonNull(ctx.INIT())) {
                return ctx.INIT().getText();
            }

            if (nonNull(ctx.ASTERISK())) {
                return ctx.ASTERISK().getText();
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> autowireGuardsVisitor = new PointcutExpressionBaseVisitor<Pointcut>() {
        @Override
        public Pointcut visitInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx) {

            PointcutExpressionParser.TargetGuardExpressionContext targetGuardExpression = ctx.targetGuardExpression();

            if (nonNull(targetGuardExpression)) {

                PointcutExpressionParser.AnnotatedTargetGuardContext annotatedTargetGuard = targetGuardExpression.annotatedTargetGuard();

                if (nonNull(annotatedTargetGuard)) {
                    return annotatedTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atAutowireTarget));
                }

                PointcutExpressionParser.TargetGuardContext targetGuard = targetGuardExpression.targetGuard();

                if (nonNull(targetGuard)) {
                    return targetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::autowireTarget));
                }

                PointcutExpressionParser.NegateTargetGuardContext negateTargetGuard = targetGuardExpression.negateTargetGuard();

                if (nonNull(negateTargetGuard)) {
                    return Pointcuts.negateAutowire(negateTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::autowireTarget)));
                }

                PointcutExpressionParser.NegateAnnotatedTargetGuardContext negateAnnotatedTargetGuard = targetGuardExpression.negateAnnotatedTargetGuard();

                if (nonNull(negateAnnotatedTargetGuard)) {
                    return Pointcuts.negateAutowire(negateAnnotatedTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atAutowireTarget)));
                }
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> executionGuardsVisitor = new PointcutExpressionBaseVisitor<Pointcut>() {
        @Override
        public Pointcut visitExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx) {

            PointcutExpressionParser.TargetGuardExpressionContext targetGuardExpression = ctx.targetGuardExpression();

            if (nonNull(targetGuardExpression)) {

                PointcutExpressionParser.AnnotatedTargetGuardContext annotatedTargetGuard = targetGuardExpression.annotatedTargetGuard();

                if (nonNull(annotatedTargetGuard)) {
                    return annotatedTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atExecutionTarget));
                }

                PointcutExpressionParser.TargetGuardContext targetGuard = targetGuardExpression.targetGuard();

                if (nonNull(targetGuard)) {
                    return targetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::executionTarget));
                }

                PointcutExpressionParser.NegateTargetGuardContext negateTargetGuard = targetGuardExpression.negateTargetGuard();

                if (nonNull(negateTargetGuard)) {
                    return negateExecution(negateTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::executionTarget)));
                }

                PointcutExpressionParser.NegateAnnotatedTargetGuardContext negateAnnotatedTargetGuard = targetGuardExpression.negateAnnotatedTargetGuard();

                if (nonNull(negateAnnotatedTargetGuard)) {
                    return negateExecution(negateAnnotatedTargetGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atExecutionTarget)));
                }

                throw new IllegalStateException();

            }

            PointcutExpressionParser.WithinGuardExpressionContext withinGuardExpression = ctx.withinGuardExpression();

            if (nonNull(withinGuardExpression)) {

                PointcutExpressionParser.AnnotatedWithinGuardContext annotatedWithinGuard = withinGuardExpression.annotatedWithinGuard();

                if (nonNull(annotatedWithinGuard)) {
                    return annotatedWithinGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atWithin));
                }

                PointcutExpressionParser.NegateWithinGuardContext negateWithinGuard = withinGuardExpression.negateWithinGuard();

                if (nonNull(negateWithinGuard)) {
                    return negateExecution(negateWithinGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::within)));
                }

                PointcutExpressionParser.WithinGuardContext withinGuard = withinGuardExpression.withinGuard();

                if (nonNull(withinGuard)) {
                    return withinGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::within));
                }

                PointcutExpressionParser.NegateAnnotatedWithinGuardContext negateAnnotatedWithinGuard = withinGuardExpression.negateAnnotatedWithinGuard();

                if (nonNull(negateAnnotatedWithinGuard)) {
                    return negateExecution(negateAnnotatedWithinGuard.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::within)));
                }
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> executionPointcutExpressionVisitor = new PointcutExpressionBaseVisitor<Pointcut>() {
        @Override
        public Pointcut visitExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx) {

            Pointcut execution = (null);

            PointcutExpressionParser.MethodExecutionContext methodExecution = ctx.methodExecution();

            PointcutExpressionParser.NegateMethodExecutionContext negateMethodExecution = ctx.negateMethodExecution();

            PointcutExpressionParser.AnnotatedMethodExecutionContext annotatedMethodExecution = ctx.annotatedMethodExecution();

            PointcutExpressionParser.NegateAnnotatedMethodExecutionContext negateAnnotatedMethodExecution = ctx.negateAnnotatedMethodExecution();

            if (nonNull(methodExecution)) {

                PointcutExpressionParser.MethodExpressionContext methodExpression = methodExecution.methodExpression();

                if (nonNull(methodExpression)) {
                    execution = methodExpression.accept(pointcutMethodExpressionFactory(Pointcuts::execution));
                } else {
                    throw new IllegalStateException();
                }

            } else if (nonNull(annotatedMethodExecution)) {
                execution = annotatedMethodExecution.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atExecution));
            } else if (nonNull(negateAnnotatedMethodExecution)) {
                execution = negateExecution(negateAnnotatedMethodExecution.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atExecution)));
            } else if (nonNull(negateMethodExecution)) {

                PointcutExpressionParser.MethodExpressionContext methodExpression = negateMethodExecution.methodExpression();

                if (nonNull(methodExpression)) {
                    execution = negateExecution(methodExpression.accept(pointcutMethodExpressionFactory(Pointcuts::execution)));
                } else {
                    throw new IllegalStateException();
                }

            }

            if (nonNull(execution)) {

                for (PointcutExpressionParser.ExecutionGuardsContext guard : ctx.executionGuards()) {

                    if (nonNull(guard.AND())) {
                        execution = executionAnd(execution, guard.accept(executionGuardsVisitor));
                        continue;
                    }

                    if (nonNull(guard.OR())) {
                        execution = executionOr(execution, guard.accept(executionGuardsVisitor));
                        continue;
                    }

                    throw new IllegalStateException();

                }

                return execution;

            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> autowirePointcutExpressionVisitor = new PointcutExpressionBaseVisitor<Pointcut>() {
        @Override
        public Pointcut visitInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx) {

            PointcutExpressionParser.InjectContext inject = ctx.inject();

            if (nonNull(inject)) {
                return inject.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::autowire));
            }

            PointcutExpressionParser.AnnotatedInjectContext annotatedInject = ctx.annotatedInject();

            if (nonNull(annotatedInject)) {
                return annotatedInject.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atAutowire));
            }

            PointcutExpressionParser.NegateInjectContext negateInject = ctx.negateInject();

            if (nonNull(negateInject)) {
                return Pointcuts.negateAutowire(negateInject.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::autowire)));
            }

            PointcutExpressionParser.NegateAnnotatedInjectContext negateAnnotatedInject = ctx.negateAnnotatedInject();

            if (nonNull(negateAnnotatedInject)) {
                return Pointcuts.negateAutowire(negateAnnotatedInject.typeExpression().accept(pointcutTypeExpressionFactory(Pointcuts::atAutowire)));
            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> pointcutExpressionVisitor = new PointcutExpressionBaseVisitor<Pointcut>() {
        @Override
        public Pointcut visitExpression(PointcutExpressionParser.ExpressionContext ctx) {

            PointcutExpressionParser.ExecutionPointcutExpressionContext executionPointcutExpression = ctx.executionPointcutExpression();

            if (nonNull(executionPointcutExpression)) {

                Pointcut execution = executionPointcutExpression.accept(executionPointcutExpressionVisitor);

                for (PointcutExpressionParser.ExecutionGuardsContext guard : executionPointcutExpression.executionGuards()) {

                    if (nonNull(guard.AND())) {
                        execution = executionAnd(execution, guard.accept(executionGuardsVisitor));
                        continue;
                    }

                    if (nonNull(guard.OR())) {
                        execution = executionOr(execution, guard.accept(executionGuardsVisitor));
                        continue;
                    }

                    throw new IllegalStateException();

                }

                return execution;

            }

            PointcutExpressionParser.InjectPointcutExpressionContext injectPointcutExpression = ctx.injectPointcutExpression();

            if (nonNull(injectPointcutExpression)) {

                Pointcut inject = injectPointcutExpression.accept(autowirePointcutExpressionVisitor);

                for (PointcutExpressionParser.InjectGuardsContext guard : injectPointcutExpression.injectGuards()) {

                    if (nonNull(guard.AND())) {
                        inject = autowireAnd(inject, guard.accept(autowireGuardsVisitor));
                        continue;
                    }

                    if (nonNull(guard.OR())) {
                        inject = autowireOr(inject, guard.accept(autowireGuardsVisitor));
                        continue;
                    }

                    throw new IllegalStateException();

                }

                return inject;

            }

            throw new IllegalStateException();

        }
    };

    private static PointcutExpressionBaseVisitor<Pointcut> pointcutTypeExpressionFactory(BiFunction<String, String, Pointcut> by) {
        return new PointcutExpressionBaseVisitor<Pointcut>() {
            @Override
            public Pointcut visitTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx) {

                String packageName = ctx.packageName().getText();

                String typeName = ctx.typeName().getText();

                return by.apply(packageName, typeName);

            }
        };
    }

    private static PointcutExpressionBaseVisitor<Pointcut> pointcutMethodExpressionFactory(TripleFunction<String, String, String[], Pointcut> by) {
        return new PointcutExpressionBaseVisitor<Pointcut>() {
            @Override
            public Pointcut visitMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx) {

                String methodName = ctx.methodName().getText();

                String returnTypeName = ctx.returnType().getText();

                PointcutExpressionParser.ParameterTypeListContext parameterTypeList = ctx.parameterTypeList();

                String[] parameterTypeNames = {};

                if (ctx.parameterTypeList() != null) {
                    parameterTypeNames = ctx.parameterTypeList().accept(parameterTypeListVisitor);
                }

                return by.apply(returnTypeName, methodName, parameterTypeNames);

            }
        };
    }

    private static BaseErrorListener parserErrorListener(String expression) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new PointcutExpressionSyntaxError(expression, msg, charPositionInLine);
            }
        };
    }

    static Pointcut parse(String expression) {

        CharStream expressionInputStream = new ANTLRInputStream(expression);

        BaseErrorListener errorListener = parserErrorListener(expression);

        PointcutExpressionLexer expressionLexer = new PointcutExpressionLexer(expressionInputStream);

        expressionLexer.getErrorListeners().clear();
        expressionLexer.addErrorListener(errorListener);

        CommonTokenStream tokenStream = new CommonTokenStream(expressionLexer);

        PointcutExpressionParser expressionParser = new PointcutExpressionParser(tokenStream);

        expressionParser.getErrorListeners().clear();
        expressionParser.addErrorListener(errorListener);
        expressionParser.getErrorListeners().clear();
        expressionParser.addErrorListener(errorListener);

        PointcutExpressionParser.ExpressionContext expressionContext = expressionParser.expression();

        return expressionContext.accept(pointcutExpressionVisitor);

    }

}

@SuppressWarnings("CheckReturnValue")
class PointcutExpressionBaseListener implements PointcutExpressionListener {
    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterExpression(PointcutExpressionParser.ExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitExpression(PointcutExpressionParser.ExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateInject(PointcutExpressionParser.NegateInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateInject(PointcutExpressionParser.NegateInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInject(PointcutExpressionParser.InjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInject(PointcutExpressionParser.InjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTargetGuard(PointcutExpressionParser.TargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTargetGuard(PointcutExpressionParser.TargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterWithinGuard(PointcutExpressionParser.WithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitWithinGuard(PointcutExpressionParser.WithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterPackageName(PointcutExpressionParser.PackageNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitPackageName(PointcutExpressionParser.PackageNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTypeName(PointcutExpressionParser.TypeNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTypeName(PointcutExpressionParser.TypeNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterMethodName(PointcutExpressionParser.MethodNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitMethodName(PointcutExpressionParser.MethodNameContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterReturnType(PointcutExpressionParser.ReturnTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitReturnType(PointcutExpressionParser.ReturnTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterParameterTypeVariance(ParameterTypeVarianceContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitParameterTypeVariance(ParameterTypeVarianceContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterParameterType(PointcutExpressionParser.ParameterTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitParameterType(PointcutExpressionParser.ParameterTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterArrayType(PointcutExpressionParser.ArrayTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitArrayType(PointcutExpressionParser.ArrayTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterVoidType(PointcutExpressionParser.VoidTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitVoidType(PointcutExpressionParser.VoidTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterWithin(PointcutExpressionParser.WithinContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitWithin(PointcutExpressionParser.WithinContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterExecution(PointcutExpressionParser.ExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitExecution(PointcutExpressionParser.ExecutionContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterAutowire(PointcutExpressionParser.AutowireContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitAutowire(PointcutExpressionParser.AutowireContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTarget(PointcutExpressionParser.TargetContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTarget(PointcutExpressionParser.TargetContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void visitTerminal(TerminalNode node) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void visitErrorNode(ErrorNode node) {
    }
}

@SuppressWarnings("CheckReturnValue")
class PointcutExpressionBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements PointcutExpressionVisitor<T> {
    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitExpression(PointcutExpressionParser.ExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateInject(PointcutExpressionParser.NegateInjectContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitInject(PointcutExpressionParser.InjectContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTargetGuard(PointcutExpressionParser.TargetGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitWithinGuard(PointcutExpressionParser.WithinGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitPackageName(PointcutExpressionParser.PackageNameContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTypeName(PointcutExpressionParser.TypeNameContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitMethodName(PointcutExpressionParser.MethodNameContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitReturnType(PointcutExpressionParser.ReturnTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitParameterTypeVariance(ParameterTypeVarianceContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitParameterType(PointcutExpressionParser.ParameterTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitArrayType(PointcutExpressionParser.ArrayTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitVoidType(PointcutExpressionParser.VoidTypeContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitWithin(PointcutExpressionParser.WithinContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitExecution(PointcutExpressionParser.ExecutionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitAutowire(PointcutExpressionParser.AutowireContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTarget(PointcutExpressionParser.TargetContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx) {
        return visitChildren(ctx);
    }
}

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
class PointcutExpressionLexer extends Lexer {
    static {
        RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int
            T__0 = 1, T__1 = 2, T__2 = 3, T__3 = 4, T__4 = 5, T__5 = 6, T__6 = 7, T__7 = 8, T__8 = 9,
            T__9 = 10, T__10 = 11, T__11 = 12, T__12 = 13, Identifier = 14, LBRACK = 15, RBRACK = 16,
            LPAREN = 17, RPAREN = 18, INIT = 19, AND = 20, OR = 21, SEP = 22, AT_SIGN = 23, NEGATION = 24,
            PLUS = 25, ASTERISK = 26, DOT = 27, COMMA = 28, WS = 29;
    public static String[] channelNames = {
            "DEFAULT_TOKEN_CHANNEL", "HIDDEN"
    };

    public static String[] modeNames = {
            "DEFAULT_MODE"
    };

    private static String[] makeRuleNames() {
        return new String[]{
                "T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8",
                "T__9", "T__10", "T__11", "T__12", "Identifier", "JavaLetter", "JavaLetterOrDigit",
                "LBRACK", "RBRACK", "LPAREN", "RPAREN", "INIT", "AND", "OR", "SEP", "AT_SIGN",
                "NEGATION", "PLUS", "ASTERISK", "DOT", "COMMA", "WS"
        };
    }

    public static final String[] ruleNames = makeRuleNames();

    private static String[] makeLiteralNames() {
        return new String[]{
                null, "'byte'", "'short'", "'char'", "'int'", "'long'", "'double'", "'float'",
                "'boolean'", "'void'", "'within'", "'execution'", "'autowire'", "'target'",
                null, "'['", "']'", "'('", "')'", "'<init>'", "'&&'", "'||'", "'|'",
                "'@'", "'!'", "'+'", "'*'", "'.'", "','"
        };
    }

    private static final String[] _LITERAL_NAMES = makeLiteralNames();

    private static String[] makeSymbolicNames() {
        return new String[]{
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, "Identifier", "LBRACK", "RBRACK", "LPAREN", "RPAREN", "INIT",
                "AND", "OR", "SEP", "AT_SIGN", "NEGATION", "PLUS", "ASTERISK", "DOT",
                "COMMA", "WS"
        };
    }

    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override

    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }


    public PointcutExpressionLexer(CharStream input) {
        super(input);
        _interp = new LexerATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @Override
    public String getGrammarFileName() {
        return "PointcutExpression.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public String[] getChannelNames() {
        return channelNames;
    }

    @Override
    public String[] getModeNames() {
        return modeNames;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    @Override
    public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
        switch (ruleIndex) {
            case 14:
                return JavaLetter_sempred((RuleContext) _localctx, predIndex);
            case 15:
                return JavaLetterOrDigit_sempred((RuleContext) _localctx, predIndex);
        }
        return true;
    }

    private boolean JavaLetter_sempred(RuleContext _localctx, int predIndex) {
        switch (predIndex) {
            case 0:
                return Character.isJavaIdentifierStart(_input.LA(-1));
            case 1:
                return Character.isJavaIdentifierStart(Character.toCodePoint((char) _input.LA(-2), (char) _input.LA(-1)));
        }
        return true;
    }

    private boolean JavaLetterOrDigit_sempred(RuleContext _localctx, int predIndex) {
        switch (predIndex) {
            case 2:
                return Character.isJavaIdentifierPart(_input.LA(-1));
            case 3:
                return Character.isJavaIdentifierPart(Character.toCodePoint((char) _input.LA(-2), (char) _input.LA(-1)));
        }
        return true;
    }

    public static final String _serializedATN =
            "\u0004\u0000\u001d\u00d4\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002" +
                    "\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002" +
                    "\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002" +
                    "\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002" +
                    "\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e" +
                    "\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011" +
                    "\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014" +
                    "\u0002\u0015\u0007\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017" +
                    "\u0002\u0018\u0007\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a" +
                    "\u0002\u001b\u0007\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d" +
                    "\u0002\u001e\u0007\u001e\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000" +
                    "\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001" +
                    "\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002" +
                    "\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004" +
                    "\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005" +
                    "\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006" +
                    "\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007" +
                    "\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007" +
                    "\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001" +
                    "\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001" +
                    "\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b" +
                    "\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b" +
                    "\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001" +
                    "\r\u0005\r\u0096\b\r\n\r\f\r\u0099\t\r\u0001\u000e\u0001\u000e\u0001\u000e" +
                    "\u0001\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u00a1\b\u000e\u0001\u000f" +
                    "\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0003\u000f" +
                    "\u00a9\b\u000f\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0012" +
                    "\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014" +
                    "\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015" +
                    "\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017" +
                    "\u0001\u0018\u0001\u0018\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a" +
                    "\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d" +
                    "\u0001\u001e\u0004\u001e\u00cf\b\u001e\u000b\u001e\f\u001e\u00d0\u0001" +
                    "\u001e\u0001\u001e\u0000\u0000\u001f\u0001\u0001\u0003\u0002\u0005\u0003" +
                    "\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u000f\b\u0011\t\u0013\n\u0015" +
                    "\u000b\u0017\f\u0019\r\u001b\u000e\u001d\u0000\u001f\u0000!\u000f#\u0010" +
                    "%\u0011\'\u0012)\u0013+\u0014-\u0015/\u00161\u00173\u00185\u00197\u001a" +
                    "9\u001b;\u001c=\u001d\u0001\u0000\u0006\u0004\u0000$$AZ__az\u0002\u0000" +
                    "\u0000\u007f\u8000\ud800\u8000\udbff\u0001\u0000\u8000\ud800\u8000\udbff" +
                    "\u0001\u0000\u8000\udc00\u8000\udfff\u0005\u0000$$09AZ__az\u0003\u0000" +
                    "\t\n\f\r  \u00d7\u0000\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001" +
                    "\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001" +
                    "\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000" +
                    "\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000" +
                    "\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000" +
                    "\u0000\u0000\u0015\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000" +
                    "\u0000\u0000\u0019\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000" +
                    "\u0000\u0000!\u0001\u0000\u0000\u0000\u0000#\u0001\u0000\u0000\u0000\u0000" +
                    "%\u0001\u0000\u0000\u0000\u0000\'\u0001\u0000\u0000\u0000\u0000)\u0001" +
                    "\u0000\u0000\u0000\u0000+\u0001\u0000\u0000\u0000\u0000-\u0001\u0000\u0000" +
                    "\u0000\u0000/\u0001\u0000\u0000\u0000\u00001\u0001\u0000\u0000\u0000\u0000" +
                    "3\u0001\u0000\u0000\u0000\u00005\u0001\u0000\u0000\u0000\u00007\u0001" +
                    "\u0000\u0000\u0000\u00009\u0001\u0000\u0000\u0000\u0000;\u0001\u0000\u0000" +
                    "\u0000\u0000=\u0001\u0000\u0000\u0000\u0001?\u0001\u0000\u0000\u0000\u0003" +
                    "D\u0001\u0000\u0000\u0000\u0005J\u0001\u0000\u0000\u0000\u0007O\u0001" +
                    "\u0000\u0000\u0000\tS\u0001\u0000\u0000\u0000\u000bX\u0001\u0000\u0000" +
                    "\u0000\r_\u0001\u0000\u0000\u0000\u000fe\u0001\u0000\u0000\u0000\u0011" +
                    "m\u0001\u0000\u0000\u0000\u0013r\u0001\u0000\u0000\u0000\u0015y\u0001" +
                    "\u0000\u0000\u0000\u0017\u0083\u0001\u0000\u0000\u0000\u0019\u008c\u0001" +
                    "\u0000\u0000\u0000\u001b\u0093\u0001\u0000\u0000\u0000\u001d\u00a0\u0001" +
                    "\u0000\u0000\u0000\u001f\u00a8\u0001\u0000\u0000\u0000!\u00aa\u0001\u0000" +
                    "\u0000\u0000#\u00ac\u0001\u0000\u0000\u0000%\u00ae\u0001\u0000\u0000\u0000" +
                    "\'\u00b0\u0001\u0000\u0000\u0000)\u00b2\u0001\u0000\u0000\u0000+\u00b9" +
                    "\u0001\u0000\u0000\u0000-\u00bc\u0001\u0000\u0000\u0000/\u00bf\u0001\u0000" +
                    "\u0000\u00001\u00c1\u0001\u0000\u0000\u00003\u00c3\u0001\u0000\u0000\u0000" +
                    "5\u00c5\u0001\u0000\u0000\u00007\u00c7\u0001\u0000\u0000\u00009\u00c9" +
                    "\u0001\u0000\u0000\u0000;\u00cb\u0001\u0000\u0000\u0000=\u00ce\u0001\u0000" +
                    "\u0000\u0000?@\u0005b\u0000\u0000@A\u0005y\u0000\u0000AB\u0005t\u0000" +
                    "\u0000BC\u0005e\u0000\u0000C\u0002\u0001\u0000\u0000\u0000DE\u0005s\u0000" +
                    "\u0000EF\u0005h\u0000\u0000FG\u0005o\u0000\u0000GH\u0005r\u0000\u0000" +
                    "HI\u0005t\u0000\u0000I\u0004\u0001\u0000\u0000\u0000JK\u0005c\u0000\u0000" +
                    "KL\u0005h\u0000\u0000LM\u0005a\u0000\u0000MN\u0005r\u0000\u0000N\u0006" +
                    "\u0001\u0000\u0000\u0000OP\u0005i\u0000\u0000PQ\u0005n\u0000\u0000QR\u0005" +
                    "t\u0000\u0000R\b\u0001\u0000\u0000\u0000ST\u0005l\u0000\u0000TU\u0005" +
                    "o\u0000\u0000UV\u0005n\u0000\u0000VW\u0005g\u0000\u0000W\n\u0001\u0000" +
                    "\u0000\u0000XY\u0005d\u0000\u0000YZ\u0005o\u0000\u0000Z[\u0005u\u0000" +
                    "\u0000[\\\u0005b\u0000\u0000\\]\u0005l\u0000\u0000]^\u0005e\u0000\u0000" +
                    "^\f\u0001\u0000\u0000\u0000_`\u0005f\u0000\u0000`a\u0005l\u0000\u0000" +
                    "ab\u0005o\u0000\u0000bc\u0005a\u0000\u0000cd\u0005t\u0000\u0000d\u000e" +
                    "\u0001\u0000\u0000\u0000ef\u0005b\u0000\u0000fg\u0005o\u0000\u0000gh\u0005" +
                    "o\u0000\u0000hi\u0005l\u0000\u0000ij\u0005e\u0000\u0000jk\u0005a\u0000" +
                    "\u0000kl\u0005n\u0000\u0000l\u0010\u0001\u0000\u0000\u0000mn\u0005v\u0000" +
                    "\u0000no\u0005o\u0000\u0000op\u0005i\u0000\u0000pq\u0005d\u0000\u0000" +
                    "q\u0012\u0001\u0000\u0000\u0000rs\u0005w\u0000\u0000st\u0005i\u0000\u0000" +
                    "tu\u0005t\u0000\u0000uv\u0005h\u0000\u0000vw\u0005i\u0000\u0000wx\u0005" +
                    "n\u0000\u0000x\u0014\u0001\u0000\u0000\u0000yz\u0005e\u0000\u0000z{\u0005" +
                    "x\u0000\u0000{|\u0005e\u0000\u0000|}\u0005c\u0000\u0000}~\u0005u\u0000" +
                    "\u0000~\u007f\u0005t\u0000\u0000\u007f\u0080\u0005i\u0000\u0000\u0080" +
                    "\u0081\u0005o\u0000\u0000\u0081\u0082\u0005n\u0000\u0000\u0082\u0016\u0001" +
                    "\u0000\u0000\u0000\u0083\u0084\u0005a\u0000\u0000\u0084\u0085\u0005u\u0000" +
                    "\u0000\u0085\u0086\u0005t\u0000\u0000\u0086\u0087\u0005o\u0000\u0000\u0087" +
                    "\u0088\u0005w\u0000\u0000\u0088\u0089\u0005i\u0000\u0000\u0089\u008a\u0005" +
                    "r\u0000\u0000\u008a\u008b\u0005e\u0000\u0000\u008b\u0018\u0001\u0000\u0000" +
                    "\u0000\u008c\u008d\u0005t\u0000\u0000\u008d\u008e\u0005a\u0000\u0000\u008e" +
                    "\u008f\u0005r\u0000\u0000\u008f\u0090\u0005g\u0000\u0000\u0090\u0091\u0005" +
                    "e\u0000\u0000\u0091\u0092\u0005t\u0000\u0000\u0092\u001a\u0001\u0000\u0000" +
                    "\u0000\u0093\u0097\u0003\u001d\u000e\u0000\u0094\u0096\u0003\u001f\u000f" +
                    "\u0000\u0095\u0094\u0001\u0000\u0000\u0000\u0096\u0099\u0001\u0000\u0000" +
                    "\u0000\u0097\u0095\u0001\u0000\u0000\u0000\u0097\u0098\u0001\u0000\u0000" +
                    "\u0000\u0098\u001c\u0001\u0000\u0000\u0000\u0099\u0097\u0001\u0000\u0000" +
                    "\u0000\u009a\u00a1\u0007\u0000\u0000\u0000\u009b\u009c\b\u0001\u0000\u0000" +
                    "\u009c\u00a1\u0004\u000e\u0000\u0000\u009d\u009e\u0007\u0002\u0000\u0000" +
                    "\u009e\u009f\u0007\u0003\u0000\u0000\u009f\u00a1\u0004\u000e\u0001\u0000" +
                    "\u00a0\u009a\u0001\u0000\u0000\u0000\u00a0\u009b\u0001\u0000\u0000\u0000" +
                    "\u00a0\u009d\u0001\u0000\u0000\u0000\u00a1\u001e\u0001\u0000\u0000\u0000" +
                    "\u00a2\u00a9\u0007\u0004\u0000\u0000\u00a3\u00a4\b\u0001\u0000\u0000\u00a4" +
                    "\u00a9\u0004\u000f\u0002\u0000\u00a5\u00a6\u0007\u0002\u0000\u0000\u00a6" +
                    "\u00a7\u0007\u0003\u0000\u0000\u00a7\u00a9\u0004\u000f\u0003\u0000\u00a8" +
                    "\u00a2\u0001\u0000\u0000\u0000\u00a8\u00a3\u0001\u0000\u0000\u0000\u00a8" +
                    "\u00a5\u0001\u0000\u0000\u0000\u00a9 \u0001\u0000\u0000\u0000\u00aa\u00ab" +
                    "\u0005[\u0000\u0000\u00ab\"\u0001\u0000\u0000\u0000\u00ac\u00ad\u0005" +
                    "]\u0000\u0000\u00ad$\u0001\u0000\u0000\u0000\u00ae\u00af\u0005(\u0000" +
                    "\u0000\u00af&\u0001\u0000\u0000\u0000\u00b0\u00b1\u0005)\u0000\u0000\u00b1" +
                    "(\u0001\u0000\u0000\u0000\u00b2\u00b3\u0005<\u0000\u0000\u00b3\u00b4\u0005" +
                    "i\u0000\u0000\u00b4\u00b5\u0005n\u0000\u0000\u00b5\u00b6\u0005i\u0000" +
                    "\u0000\u00b6\u00b7\u0005t\u0000\u0000\u00b7\u00b8\u0005>\u0000\u0000\u00b8" +
                    "*\u0001\u0000\u0000\u0000\u00b9\u00ba\u0005&\u0000\u0000\u00ba\u00bb\u0005" +
                    "&\u0000\u0000\u00bb,\u0001\u0000\u0000\u0000\u00bc\u00bd\u0005|\u0000" +
                    "\u0000\u00bd\u00be\u0005|\u0000\u0000\u00be.\u0001\u0000\u0000\u0000\u00bf" +
                    "\u00c0\u0005|\u0000\u0000\u00c00\u0001\u0000\u0000\u0000\u00c1\u00c2\u0005" +
                    "@\u0000\u0000\u00c22\u0001\u0000\u0000\u0000\u00c3\u00c4\u0005!\u0000" +
                    "\u0000\u00c44\u0001\u0000\u0000\u0000\u00c5\u00c6\u0005+\u0000\u0000\u00c6" +
                    "6\u0001\u0000\u0000\u0000\u00c7\u00c8\u0005*\u0000\u0000\u00c88\u0001" +
                    "\u0000\u0000\u0000\u00c9\u00ca\u0005.\u0000\u0000\u00ca:\u0001\u0000\u0000" +
                    "\u0000\u00cb\u00cc\u0005,\u0000\u0000\u00cc<\u0001\u0000\u0000\u0000\u00cd" +
                    "\u00cf\u0007\u0005\u0000\u0000\u00ce\u00cd\u0001\u0000\u0000\u0000\u00cf" +
                    "\u00d0\u0001\u0000\u0000\u0000\u00d0\u00ce\u0001\u0000\u0000\u0000\u00d0" +
                    "\u00d1\u0001\u0000\u0000\u0000\u00d1\u00d2\u0001\u0000\u0000\u0000\u00d2" +
                    "\u00d3\u0006\u001e\u0000\u0000\u00d3>\u0001\u0000\u0000\u0000\u0005\u0000" +
                    "\u0097\u00a0\u00a8\u00d0\u0001\u0006\u0000\u0000";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}

interface PointcutExpressionListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#expression}.
     *
     * @param ctx the parse tree
     */
    void enterExpression(PointcutExpressionParser.ExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#expression}.
     *
     * @param ctx the parse tree
     */
    void exitExpression(PointcutExpressionParser.ExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#injectPointcutExpression}.
     *
     * @param ctx the parse tree
     */
    void enterInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#injectPointcutExpression}.
     *
     * @param ctx the parse tree
     */
    void exitInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateInject}.
     *
     * @param ctx the parse tree
     */
    void enterNegateInject(PointcutExpressionParser.NegateInjectContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateInject}.
     *
     * @param ctx the parse tree
     */
    void exitNegateInject(PointcutExpressionParser.NegateInjectContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#inject}.
     *
     * @param ctx the parse tree
     */
    void enterInject(PointcutExpressionParser.InjectContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#inject}.
     *
     * @param ctx the parse tree
     */
    void exitInject(PointcutExpressionParser.InjectContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#annotatedInject}.
     *
     * @param ctx the parse tree
     */
    void enterAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#annotatedInject}.
     *
     * @param ctx the parse tree
     */
    void exitAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedInject}.
     *
     * @param ctx the parse tree
     */
    void enterNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedInject}.
     *
     * @param ctx the parse tree
     */
    void exitNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#executionPointcutExpression}.
     *
     * @param ctx the parse tree
     */
    void enterExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#executionPointcutExpression}.
     *
     * @param ctx the parse tree
     */
    void exitExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#methodExecution}.
     *
     * @param ctx the parse tree
     */
    void enterMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#methodExecution}.
     *
     * @param ctx the parse tree
     */
    void exitMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void enterNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void exitNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#annotatedMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void enterAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#annotatedMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void exitAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void enterNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedMethodExecution}.
     *
     * @param ctx the parse tree
     */
    void exitNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#executionGuards}.
     *
     * @param ctx the parse tree
     */
    void enterExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#executionGuards}.
     *
     * @param ctx the parse tree
     */
    void exitExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#injectGuards}.
     *
     * @param ctx the parse tree
     */
    void enterInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#injectGuards}.
     *
     * @param ctx the parse tree
     */
    void exitInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#targetGuardExpression}.
     *
     * @param ctx the parse tree
     */
    void enterTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#targetGuardExpression}.
     *
     * @param ctx the parse tree
     */
    void exitTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#targetGuard}.
     *
     * @param ctx the parse tree
     */
    void enterTargetGuard(PointcutExpressionParser.TargetGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#targetGuard}.
     *
     * @param ctx the parse tree
     */
    void exitTargetGuard(PointcutExpressionParser.TargetGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void enterNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void exitNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#annotatedTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void enterAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#annotatedTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void exitAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void enterNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedTargetGuard}.
     *
     * @param ctx the parse tree
     */
    void exitNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#withinGuardExpression}.
     *
     * @param ctx the parse tree
     */
    void enterWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#withinGuardExpression}.
     *
     * @param ctx the parse tree
     */
    void exitWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#withinGuard}.
     *
     * @param ctx the parse tree
     */
    void enterWithinGuard(PointcutExpressionParser.WithinGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#withinGuard}.
     *
     * @param ctx the parse tree
     */
    void exitWithinGuard(PointcutExpressionParser.WithinGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void enterNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void exitNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#annotatedWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void enterAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#annotatedWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void exitAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void enterNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedWithinGuard}.
     *
     * @param ctx the parse tree
     */
    void exitNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#typeExpression}.
     *
     * @param ctx the parse tree
     */
    void enterTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#typeExpression}.
     *
     * @param ctx the parse tree
     */
    void exitTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#packageName}.
     *
     * @param ctx the parse tree
     */
    void enterPackageName(PointcutExpressionParser.PackageNameContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#packageName}.
     *
     * @param ctx the parse tree
     */
    void exitPackageName(PointcutExpressionParser.PackageNameContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#typeName}.
     *
     * @param ctx the parse tree
     */
    void enterTypeName(PointcutExpressionParser.TypeNameContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#typeName}.
     *
     * @param ctx the parse tree
     */
    void exitTypeName(PointcutExpressionParser.TypeNameContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#methodExpression}.
     *
     * @param ctx the parse tree
     */
    void enterMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#methodExpression}.
     *
     * @param ctx the parse tree
     */
    void exitMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#methodName}.
     *
     * @param ctx the parse tree
     */
    void enterMethodName(PointcutExpressionParser.MethodNameContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#methodName}.
     *
     * @param ctx the parse tree
     */
    void exitMethodName(PointcutExpressionParser.MethodNameContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#returnType}.
     *
     * @param ctx the parse tree
     */
    void enterReturnType(PointcutExpressionParser.ReturnTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#returnType}.
     *
     * @param ctx the parse tree
     */
    void exitReturnType(PointcutExpressionParser.ReturnTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#parameterTypeList}.
     *
     * @param ctx the parse tree
     */
    void enterParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#parameterTypeList}.
     *
     * @param ctx the parse tree
     */
    void exitParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#parameterTypeVariance}.
     *
     * @param ctx the parse tree
     */
    void enterParameterTypeVariance(ParameterTypeVarianceContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#parameterTypeVariance}.
     *
     * @param ctx the parse tree
     */
    void exitParameterTypeVariance(ParameterTypeVarianceContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#parameterType}.
     *
     * @param ctx the parse tree
     */
    void enterParameterType(PointcutExpressionParser.ParameterTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#parameterType}.
     *
     * @param ctx the parse tree
     */
    void exitParameterType(PointcutExpressionParser.ParameterTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#covariantReferenceType}.
     *
     * @param ctx the parse tree
     */
    void enterCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#covariantReferenceType}.
     *
     * @param ctx the parse tree
     */
    void exitCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#arrayType}.
     *
     * @param ctx the parse tree
     */
    void enterArrayType(PointcutExpressionParser.ArrayTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#arrayType}.
     *
     * @param ctx the parse tree
     */
    void exitArrayType(PointcutExpressionParser.ArrayTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#arraySuffix}.
     *
     * @param ctx the parse tree
     */
    void enterArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#arraySuffix}.
     *
     * @param ctx the parse tree
     */
    void exitArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#primitiveType}.
     *
     * @param ctx the parse tree
     */
    void enterPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#primitiveType}.
     *
     * @param ctx the parse tree
     */
    void exitPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#voidType}.
     *
     * @param ctx the parse tree
     */
    void enterVoidType(PointcutExpressionParser.VoidTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#voidType}.
     *
     * @param ctx the parse tree
     */
    void exitVoidType(PointcutExpressionParser.VoidTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#within}.
     *
     * @param ctx the parse tree
     */
    void enterWithin(PointcutExpressionParser.WithinContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#within}.
     *
     * @param ctx the parse tree
     */
    void exitWithin(PointcutExpressionParser.WithinContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#execution}.
     *
     * @param ctx the parse tree
     */
    void enterExecution(PointcutExpressionParser.ExecutionContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#execution}.
     *
     * @param ctx the parse tree
     */
    void exitExecution(PointcutExpressionParser.ExecutionContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#autowire}.
     *
     * @param ctx the parse tree
     */
    void enterAutowire(PointcutExpressionParser.AutowireContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#autowire}.
     *
     * @param ctx the parse tree
     */
    void exitAutowire(PointcutExpressionParser.AutowireContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#target}.
     *
     * @param ctx the parse tree
     */
    void enterTarget(PointcutExpressionParser.TargetContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#target}.
     *
     * @param ctx the parse tree
     */
    void exitTarget(PointcutExpressionParser.TargetContext ctx);

    /**
     * Enter a parse tree produced by {@link PointcutExpressionParser#referenceType}.
     *
     * @param ctx the parse tree
     */
    void enterReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link PointcutExpressionParser#referenceType}.
     *
     * @param ctx the parse tree
     */
    void exitReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx);
}

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
class PointcutExpressionParser extends Parser {
    static {
        RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int
            T__0 = 1, T__1 = 2, T__2 = 3, T__3 = 4, T__4 = 5, T__5 = 6, T__6 = 7, T__7 = 8, T__8 = 9,
            T__9 = 10, T__10 = 11, T__11 = 12, T__12 = 13, Identifier = 14, LBRACK = 15, RBRACK = 16,
            LPAREN = 17, RPAREN = 18, INIT = 19, AND = 20, OR = 21, SEP = 22, AT_SIGN = 23, NEGATION = 24,
            PLUS = 25, ASTERISK = 26, DOT = 27, COMMA = 28, WS = 29;
    public static final int
            RULE_expression = 0, RULE_injectPointcutExpression = 1, RULE_negateInject = 2,
            RULE_inject = 3, RULE_annotatedInject = 4, RULE_negateAnnotatedInject = 5,
            RULE_executionPointcutExpression = 6, RULE_methodExecution = 7, RULE_negateMethodExecution = 8,
            RULE_annotatedMethodExecution = 9, RULE_negateAnnotatedMethodExecution = 10,
            RULE_executionGuards = 11, RULE_injectGuards = 12, RULE_targetGuardExpression = 13,
            RULE_targetGuard = 14, RULE_negateTargetGuard = 15, RULE_annotatedTargetGuard = 16,
            RULE_negateAnnotatedTargetGuard = 17, RULE_withinGuardExpression = 18,
            RULE_withinGuard = 19, RULE_negateWithinGuard = 20, RULE_annotatedWithinGuard = 21,
            RULE_negateAnnotatedWithinGuard = 22, RULE_typeExpression = 23, RULE_packageName = 24,
            RULE_typeName = 25, RULE_methodExpression = 26, RULE_methodName = 27,
            RULE_returnType = 28, RULE_parameterTypeList = 29, RULE_parameterTypeVariance = 30,
            RULE_parameterType = 31, RULE_covariantReferenceType = 32, RULE_arrayType = 33,
            RULE_arraySuffix = 34, RULE_primitiveType = 35, RULE_voidType = 36, RULE_within = 37,
            RULE_execution = 38, RULE_autowire = 39, RULE_target = 40, RULE_referenceType = 41;

    private static String[] makeRuleNames() {
        return new String[]{
                "expression", "injectPointcutExpression", "negateInject", "inject", "annotatedInject",
                "negateAnnotatedInject", "executionPointcutExpression", "methodExecution",
                "negateMethodExecution", "annotatedMethodExecution", "negateAnnotatedMethodExecution",
                "executionGuards", "injectGuards", "targetGuardExpression", "targetGuard",
                "negateTargetGuard", "annotatedTargetGuard", "negateAnnotatedTargetGuard",
                "withinGuardExpression", "withinGuard", "negateWithinGuard", "annotatedWithinGuard",
                "negateAnnotatedWithinGuard", "typeExpression", "packageName", "typeName",
                "methodExpression", "methodName", "returnType", "parameterTypeList",
                "parameterTypeVariance", "parameterType", "covariantReferenceType", "arrayType",
                "arraySuffix", "primitiveType", "voidType", "within", "execution", "autowire",
                "target", "referenceType"
        };
    }

    public static final String[] ruleNames = makeRuleNames();

    private static String[] makeLiteralNames() {
        return new String[]{
                null, "'byte'", "'short'", "'char'", "'int'", "'long'", "'double'", "'float'",
                "'boolean'", "'void'", "'within'", "'execution'", "'autowire'", "'target'",
                null, "'['", "']'", "'('", "')'", "'<init>'", "'&&'", "'||'", "'|'",
                "'@'", "'!'", "'+'", "'*'", "'.'", "','"
        };
    }

    private static final String[] _LITERAL_NAMES = makeLiteralNames();

    private static String[] makeSymbolicNames() {
        return new String[]{
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, "Identifier", "LBRACK", "RBRACK", "LPAREN", "RPAREN", "INIT",
                "AND", "OR", "SEP", "AT_SIGN", "NEGATION", "PLUS", "ASTERISK", "DOT",
                "COMMA", "WS"
        };
    }

    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override

    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }

    @Override
    public String getGrammarFileName() {
        return "java-escape";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public PointcutExpressionParser(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ExpressionContext extends ParserRuleContext {
        public InjectPointcutExpressionContext injectPointcutExpression() {
            return getRuleContext(InjectPointcutExpressionContext.class, 0);
        }

        public ExecutionPointcutExpressionContext executionPointcutExpression() {
            return getRuleContext(ExecutionPointcutExpressionContext.class, 0);
        }

        public ExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_expression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ExpressionContext expression() throws RecognitionException {
        ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_expression);
        try {
            setState(86);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 0, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(84);
                    injectPointcutExpression();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(85);
                    executionPointcutExpression();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class InjectPointcutExpressionContext extends ParserRuleContext {
        public NegateInjectContext negateInject() {
            return getRuleContext(NegateInjectContext.class, 0);
        }

        public InjectContext inject() {
            return getRuleContext(InjectContext.class, 0);
        }

        public AnnotatedInjectContext annotatedInject() {
            return getRuleContext(AnnotatedInjectContext.class, 0);
        }

        public NegateAnnotatedInjectContext negateAnnotatedInject() {
            return getRuleContext(NegateAnnotatedInjectContext.class, 0);
        }

        public List<InjectGuardsContext> injectGuards() {
            return getRuleContexts(InjectGuardsContext.class);
        }

        public InjectGuardsContext injectGuards(int i) {
            return getRuleContext(InjectGuardsContext.class, i);
        }

        public InjectPointcutExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_injectPointcutExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterInjectPointcutExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitInjectPointcutExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitInjectPointcutExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InjectPointcutExpressionContext injectPointcutExpression() throws RecognitionException {
        InjectPointcutExpressionContext _localctx = new InjectPointcutExpressionContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_injectPointcutExpression);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(92);
                _errHandler.sync(this);
                switch (getInterpreter().adaptivePredict(_input, 1, _ctx)) {
                    case 1: {
                        setState(88);
                        negateInject();
                    }
                    break;
                    case 2: {
                        setState(89);
                        inject();
                    }
                    break;
                    case 3: {
                        setState(90);
                        annotatedInject();
                    }
                    break;
                    case 4: {
                        setState(91);
                        negateAnnotatedInject();
                    }
                    break;
                }
                setState(97);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == AND || _la == OR) {
                    {
                        {
                            setState(94);
                            injectGuards();
                        }
                    }
                    setState(99);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateInjectContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public AutowireContext autowire() {
            return getRuleContext(AutowireContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateInjectContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateInject;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateInject(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateInject(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateInject(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateInjectContext negateInject() throws RecognitionException {
        NegateInjectContext _localctx = new NegateInjectContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_negateInject);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(100);
                match(NEGATION);
                setState(101);
                autowire();
                setState(102);
                match(LPAREN);
                setState(103);
                typeExpression();
                setState(104);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class InjectContext extends ParserRuleContext {
        public AutowireContext autowire() {
            return getRuleContext(AutowireContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public InjectContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_inject;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterInject(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitInject(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitInject(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InjectContext inject() throws RecognitionException {
        InjectContext _localctx = new InjectContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_inject);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(106);
                autowire();
                setState(107);
                match(LPAREN);
                setState(108);
                typeExpression();
                setState(109);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class AnnotatedInjectContext extends ParserRuleContext {
        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public AutowireContext autowire() {
            return getRuleContext(AutowireContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public AnnotatedInjectContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_annotatedInject;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterAnnotatedInject(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitAnnotatedInject(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitAnnotatedInject(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AnnotatedInjectContext annotatedInject() throws RecognitionException {
        AnnotatedInjectContext _localctx = new AnnotatedInjectContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_annotatedInject);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(111);
                match(AT_SIGN);
                setState(112);
                autowire();
                setState(113);
                match(LPAREN);
                setState(114);
                typeExpression();
                setState(115);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateAnnotatedInjectContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public AutowireContext autowire() {
            return getRuleContext(AutowireContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateAnnotatedInjectContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateAnnotatedInject;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateAnnotatedInject(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateAnnotatedInject(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateAnnotatedInject(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateAnnotatedInjectContext negateAnnotatedInject() throws RecognitionException {
        NegateAnnotatedInjectContext _localctx = new NegateAnnotatedInjectContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_negateAnnotatedInject);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(117);
                match(NEGATION);
                setState(118);
                match(AT_SIGN);
                setState(119);
                autowire();
                setState(120);
                match(LPAREN);
                setState(121);
                typeExpression();
                setState(122);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ExecutionPointcutExpressionContext extends ParserRuleContext {
        public MethodExecutionContext methodExecution() {
            return getRuleContext(MethodExecutionContext.class, 0);
        }

        public NegateMethodExecutionContext negateMethodExecution() {
            return getRuleContext(NegateMethodExecutionContext.class, 0);
        }

        public AnnotatedMethodExecutionContext annotatedMethodExecution() {
            return getRuleContext(AnnotatedMethodExecutionContext.class, 0);
        }

        public NegateAnnotatedMethodExecutionContext negateAnnotatedMethodExecution() {
            return getRuleContext(NegateAnnotatedMethodExecutionContext.class, 0);
        }

        public List<ExecutionGuardsContext> executionGuards() {
            return getRuleContexts(ExecutionGuardsContext.class);
        }

        public ExecutionGuardsContext executionGuards(int i) {
            return getRuleContext(ExecutionGuardsContext.class, i);
        }

        public ExecutionPointcutExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_executionPointcutExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterExecutionPointcutExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitExecutionPointcutExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitExecutionPointcutExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ExecutionPointcutExpressionContext executionPointcutExpression() throws RecognitionException {
        ExecutionPointcutExpressionContext _localctx = new ExecutionPointcutExpressionContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_executionPointcutExpression);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(128);
                _errHandler.sync(this);
                switch (getInterpreter().adaptivePredict(_input, 3, _ctx)) {
                    case 1: {
                        setState(124);
                        methodExecution();
                    }
                    break;
                    case 2: {
                        setState(125);
                        negateMethodExecution();
                    }
                    break;
                    case 3: {
                        setState(126);
                        annotatedMethodExecution();
                    }
                    break;
                    case 4: {
                        setState(127);
                        negateAnnotatedMethodExecution();
                    }
                    break;
                }
                setState(133);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == AND || _la == OR) {
                    {
                        {
                            setState(130);
                            executionGuards();
                        }
                    }
                    setState(135);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class MethodExecutionContext extends ParserRuleContext {
        public ExecutionContext execution() {
            return getRuleContext(ExecutionContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public MethodExpressionContext methodExpression() {
            return getRuleContext(MethodExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public MethodExecutionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_methodExecution;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterMethodExecution(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitMethodExecution(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitMethodExecution(this);
            else return visitor.visitChildren(this);
        }
    }

    public final MethodExecutionContext methodExecution() throws RecognitionException {
        MethodExecutionContext _localctx = new MethodExecutionContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_methodExecution);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(136);
                execution();
                setState(137);
                match(LPAREN);
                setState(138);
                methodExpression();
                setState(139);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateMethodExecutionContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public ExecutionContext execution() {
            return getRuleContext(ExecutionContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public MethodExpressionContext methodExpression() {
            return getRuleContext(MethodExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateMethodExecutionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateMethodExecution;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateMethodExecution(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateMethodExecution(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateMethodExecution(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateMethodExecutionContext negateMethodExecution() throws RecognitionException {
        NegateMethodExecutionContext _localctx = new NegateMethodExecutionContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_negateMethodExecution);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(141);
                match(NEGATION);
                setState(142);
                execution();
                setState(143);
                match(LPAREN);
                setState(144);
                methodExpression();
                setState(145);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class AnnotatedMethodExecutionContext extends ParserRuleContext {
        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public ExecutionContext execution() {
            return getRuleContext(ExecutionContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public AnnotatedMethodExecutionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_annotatedMethodExecution;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterAnnotatedMethodExecution(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitAnnotatedMethodExecution(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitAnnotatedMethodExecution(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AnnotatedMethodExecutionContext annotatedMethodExecution() throws RecognitionException {
        AnnotatedMethodExecutionContext _localctx = new AnnotatedMethodExecutionContext(_ctx, getState());
        enterRule(_localctx, 18, RULE_annotatedMethodExecution);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(147);
                match(AT_SIGN);
                setState(148);
                execution();
                setState(149);
                match(LPAREN);
                setState(150);
                typeExpression();
                setState(151);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateAnnotatedMethodExecutionContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public ExecutionContext execution() {
            return getRuleContext(ExecutionContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateAnnotatedMethodExecutionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateAnnotatedMethodExecution;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateAnnotatedMethodExecution(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateAnnotatedMethodExecution(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateAnnotatedMethodExecution(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateAnnotatedMethodExecutionContext negateAnnotatedMethodExecution() throws RecognitionException {
        NegateAnnotatedMethodExecutionContext _localctx = new NegateAnnotatedMethodExecutionContext(_ctx, getState());
        enterRule(_localctx, 20, RULE_negateAnnotatedMethodExecution);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(153);
                match(NEGATION);
                setState(154);
                match(AT_SIGN);
                setState(155);
                execution();
                setState(156);
                match(LPAREN);
                setState(157);
                typeExpression();
                setState(158);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ExecutionGuardsContext extends ParserRuleContext {
        public TerminalNode AND() {
            return getToken(PointcutExpressionParser.AND, 0);
        }

        public TerminalNode OR() {
            return getToken(PointcutExpressionParser.OR, 0);
        }

        public WithinGuardExpressionContext withinGuardExpression() {
            return getRuleContext(WithinGuardExpressionContext.class, 0);
        }

        public TargetGuardExpressionContext targetGuardExpression() {
            return getRuleContext(TargetGuardExpressionContext.class, 0);
        }

        public ExecutionGuardsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_executionGuards;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterExecutionGuards(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitExecutionGuards(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitExecutionGuards(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ExecutionGuardsContext executionGuards() throws RecognitionException {
        ExecutionGuardsContext _localctx = new ExecutionGuardsContext(_ctx, getState());
        enterRule(_localctx, 22, RULE_executionGuards);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                {
                    setState(160);
                    _la = _input.LA(1);
                    if (!(_la == AND || _la == OR)) {
                        _errHandler.recoverInline(this);
                    } else {
                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                        _errHandler.reportMatch(this);
                        consume();
                    }
                    setState(163);
                    _errHandler.sync(this);
                    switch (getInterpreter().adaptivePredict(_input, 5, _ctx)) {
                        case 1: {
                            setState(161);
                            withinGuardExpression();
                        }
                        break;
                        case 2: {
                            setState(162);
                            targetGuardExpression();
                        }
                        break;
                    }
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class InjectGuardsContext extends ParserRuleContext {
        public TargetGuardExpressionContext targetGuardExpression() {
            return getRuleContext(TargetGuardExpressionContext.class, 0);
        }

        public TerminalNode AND() {
            return getToken(PointcutExpressionParser.AND, 0);
        }

        public TerminalNode OR() {
            return getToken(PointcutExpressionParser.OR, 0);
        }

        public InjectGuardsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_injectGuards;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterInjectGuards(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitInjectGuards(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitInjectGuards(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InjectGuardsContext injectGuards() throws RecognitionException {
        InjectGuardsContext _localctx = new InjectGuardsContext(_ctx, getState());
        enterRule(_localctx, 24, RULE_injectGuards);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(165);
                _la = _input.LA(1);
                if (!(_la == AND || _la == OR)) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
                setState(166);
                targetGuardExpression();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TargetGuardExpressionContext extends ParserRuleContext {
        public TargetGuardContext targetGuard() {
            return getRuleContext(TargetGuardContext.class, 0);
        }

        public NegateTargetGuardContext negateTargetGuard() {
            return getRuleContext(NegateTargetGuardContext.class, 0);
        }

        public AnnotatedTargetGuardContext annotatedTargetGuard() {
            return getRuleContext(AnnotatedTargetGuardContext.class, 0);
        }

        public NegateAnnotatedTargetGuardContext negateAnnotatedTargetGuard() {
            return getRuleContext(NegateAnnotatedTargetGuardContext.class, 0);
        }

        public TargetGuardExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_targetGuardExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterTargetGuardExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitTargetGuardExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitTargetGuardExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TargetGuardExpressionContext targetGuardExpression() throws RecognitionException {
        TargetGuardExpressionContext _localctx = new TargetGuardExpressionContext(_ctx, getState());
        enterRule(_localctx, 26, RULE_targetGuardExpression);
        try {
            setState(172);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 6, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(168);
                    targetGuard();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(169);
                    negateTargetGuard();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(170);
                    annotatedTargetGuard();
                }
                break;
                case 4:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(171);
                    negateAnnotatedTargetGuard();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TargetGuardContext extends ParserRuleContext {
        public TargetContext target() {
            return getRuleContext(TargetContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public TargetGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_targetGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterTargetGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitTargetGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitTargetGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TargetGuardContext targetGuard() throws RecognitionException {
        TargetGuardContext _localctx = new TargetGuardContext(_ctx, getState());
        enterRule(_localctx, 28, RULE_targetGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(174);
                target();
                setState(175);
                match(LPAREN);
                setState(176);
                typeExpression();
                setState(177);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateTargetGuardContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public TargetContext target() {
            return getRuleContext(TargetContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateTargetGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateTargetGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateTargetGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateTargetGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateTargetGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateTargetGuardContext negateTargetGuard() throws RecognitionException {
        NegateTargetGuardContext _localctx = new NegateTargetGuardContext(_ctx, getState());
        enterRule(_localctx, 30, RULE_negateTargetGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(179);
                match(NEGATION);
                setState(180);
                target();
                setState(181);
                match(LPAREN);
                setState(182);
                typeExpression();
                setState(183);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class AnnotatedTargetGuardContext extends ParserRuleContext {
        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public TargetContext target() {
            return getRuleContext(TargetContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public AnnotatedTargetGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_annotatedTargetGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterAnnotatedTargetGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitAnnotatedTargetGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitAnnotatedTargetGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AnnotatedTargetGuardContext annotatedTargetGuard() throws RecognitionException {
        AnnotatedTargetGuardContext _localctx = new AnnotatedTargetGuardContext(_ctx, getState());
        enterRule(_localctx, 32, RULE_annotatedTargetGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(185);
                match(AT_SIGN);
                setState(186);
                target();
                setState(187);
                match(LPAREN);
                setState(188);
                typeExpression();
                setState(189);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateAnnotatedTargetGuardContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public TargetContext target() {
            return getRuleContext(TargetContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateAnnotatedTargetGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateAnnotatedTargetGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateAnnotatedTargetGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateAnnotatedTargetGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateAnnotatedTargetGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateAnnotatedTargetGuardContext negateAnnotatedTargetGuard() throws RecognitionException {
        NegateAnnotatedTargetGuardContext _localctx = new NegateAnnotatedTargetGuardContext(_ctx, getState());
        enterRule(_localctx, 34, RULE_negateAnnotatedTargetGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(191);
                match(NEGATION);
                setState(192);
                match(AT_SIGN);
                setState(193);
                target();
                setState(194);
                match(LPAREN);
                setState(195);
                typeExpression();
                setState(196);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class WithinGuardExpressionContext extends ParserRuleContext {
        public WithinGuardContext withinGuard() {
            return getRuleContext(WithinGuardContext.class, 0);
        }

        public NegateWithinGuardContext negateWithinGuard() {
            return getRuleContext(NegateWithinGuardContext.class, 0);
        }

        public AnnotatedWithinGuardContext annotatedWithinGuard() {
            return getRuleContext(AnnotatedWithinGuardContext.class, 0);
        }

        public NegateAnnotatedWithinGuardContext negateAnnotatedWithinGuard() {
            return getRuleContext(NegateAnnotatedWithinGuardContext.class, 0);
        }

        public WithinGuardExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_withinGuardExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterWithinGuardExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitWithinGuardExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitWithinGuardExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final WithinGuardExpressionContext withinGuardExpression() throws RecognitionException {
        WithinGuardExpressionContext _localctx = new WithinGuardExpressionContext(_ctx, getState());
        enterRule(_localctx, 36, RULE_withinGuardExpression);
        try {
            setState(202);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 7, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(198);
                    withinGuard();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(199);
                    negateWithinGuard();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(200);
                    annotatedWithinGuard();
                }
                break;
                case 4:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(201);
                    negateAnnotatedWithinGuard();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class WithinGuardContext extends ParserRuleContext {
        public WithinContext within() {
            return getRuleContext(WithinContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public WithinGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_withinGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterWithinGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitWithinGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitWithinGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final WithinGuardContext withinGuard() throws RecognitionException {
        WithinGuardContext _localctx = new WithinGuardContext(_ctx, getState());
        enterRule(_localctx, 38, RULE_withinGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(204);
                within();
                setState(205);
                match(LPAREN);
                setState(206);
                typeExpression();
                setState(207);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateWithinGuardContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public WithinContext within() {
            return getRuleContext(WithinContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateWithinGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateWithinGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateWithinGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateWithinGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateWithinGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateWithinGuardContext negateWithinGuard() throws RecognitionException {
        NegateWithinGuardContext _localctx = new NegateWithinGuardContext(_ctx, getState());
        enterRule(_localctx, 40, RULE_negateWithinGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(209);
                match(NEGATION);
                setState(210);
                within();
                setState(211);
                match(LPAREN);
                setState(212);
                typeExpression();
                setState(213);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class AnnotatedWithinGuardContext extends ParserRuleContext {
        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public WithinContext within() {
            return getRuleContext(WithinContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public AnnotatedWithinGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_annotatedWithinGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterAnnotatedWithinGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitAnnotatedWithinGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitAnnotatedWithinGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AnnotatedWithinGuardContext annotatedWithinGuard() throws RecognitionException {
        AnnotatedWithinGuardContext _localctx = new AnnotatedWithinGuardContext(_ctx, getState());
        enterRule(_localctx, 42, RULE_annotatedWithinGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(215);
                match(AT_SIGN);
                setState(216);
                within();
                setState(217);
                match(LPAREN);
                setState(218);
                typeExpression();
                setState(219);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class NegateAnnotatedWithinGuardContext extends ParserRuleContext {
        public TerminalNode NEGATION() {
            return getToken(PointcutExpressionParser.NEGATION, 0);
        }

        public TerminalNode AT_SIGN() {
            return getToken(PointcutExpressionParser.AT_SIGN, 0);
        }

        public WithinContext within() {
            return getRuleContext(WithinContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TypeExpressionContext typeExpression() {
            return getRuleContext(TypeExpressionContext.class, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public NegateAnnotatedWithinGuardContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_negateAnnotatedWithinGuard;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterNegateAnnotatedWithinGuard(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitNegateAnnotatedWithinGuard(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitNegateAnnotatedWithinGuard(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NegateAnnotatedWithinGuardContext negateAnnotatedWithinGuard() throws RecognitionException {
        NegateAnnotatedWithinGuardContext _localctx = new NegateAnnotatedWithinGuardContext(_ctx, getState());
        enterRule(_localctx, 44, RULE_negateAnnotatedWithinGuard);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(221);
                match(NEGATION);
                setState(222);
                match(AT_SIGN);
                setState(223);
                within();
                setState(224);
                match(LPAREN);
                setState(225);
                typeExpression();
                setState(226);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TypeExpressionContext extends ParserRuleContext {
        public PackageNameContext packageName() {
            return getRuleContext(PackageNameContext.class, 0);
        }

        public TerminalNode DOT() {
            return getToken(PointcutExpressionParser.DOT, 0);
        }

        public TypeNameContext typeName() {
            return getRuleContext(TypeNameContext.class, 0);
        }

        public TypeExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_typeExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterTypeExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitTypeExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitTypeExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TypeExpressionContext typeExpression() throws RecognitionException {
        TypeExpressionContext _localctx = new TypeExpressionContext(_ctx, getState());
        enterRule(_localctx, 46, RULE_typeExpression);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(228);
                packageName();
                setState(229);
                match(DOT);
                setState(230);
                typeName();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class PackageNameContext extends ParserRuleContext {
        public ReferenceTypeContext referenceType() {
            return getRuleContext(ReferenceTypeContext.class, 0);
        }

        public TerminalNode ASTERISK() {
            return getToken(PointcutExpressionParser.ASTERISK, 0);
        }

        public PackageNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_packageName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterPackageName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitPackageName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitPackageName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final PackageNameContext packageName() throws RecognitionException {
        PackageNameContext _localctx = new PackageNameContext(_ctx, getState());
        enterRule(_localctx, 48, RULE_packageName);
        try {
            setState(234);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case Identifier:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(232);
                    referenceType();
                }
                break;
                case ASTERISK:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(233);
                    match(ASTERISK);
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TypeNameContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(PointcutExpressionParser.Identifier, 0);
        }

        public TerminalNode ASTERISK() {
            return getToken(PointcutExpressionParser.ASTERISK, 0);
        }

        public TypeNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_typeName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterTypeName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitTypeName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitTypeName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TypeNameContext typeName() throws RecognitionException {
        TypeNameContext _localctx = new TypeNameContext(_ctx, getState());
        enterRule(_localctx, 50, RULE_typeName);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(236);
                _la = _input.LA(1);
                if (!(_la == Identifier || _la == ASTERISK)) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class MethodExpressionContext extends ParserRuleContext {
        public ReturnTypeContext returnType() {
            return getRuleContext(ReturnTypeContext.class, 0);
        }

        public MethodNameContext methodName() {
            return getRuleContext(MethodNameContext.class, 0);
        }

        public TerminalNode LPAREN() {
            return getToken(PointcutExpressionParser.LPAREN, 0);
        }

        public TerminalNode RPAREN() {
            return getToken(PointcutExpressionParser.RPAREN, 0);
        }

        public ParameterTypeListContext parameterTypeList() {
            return getRuleContext(ParameterTypeListContext.class, 0);
        }

        public MethodExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_methodExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterMethodExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitMethodExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitMethodExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final MethodExpressionContext methodExpression() throws RecognitionException {
        MethodExpressionContext _localctx = new MethodExpressionContext(_ctx, getState());
        enterRule(_localctx, 52, RULE_methodExpression);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(238);
                returnType();
                setState(239);
                methodName();
                setState(240);
                match(LPAREN);
                setState(242);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (((_la) & ~0x3f) == 0 && ((1L << _la) & 201343486L) != 0) {
                    {
                        setState(241);
                        parameterTypeList();
                    }
                }

                setState(244);
                match(RPAREN);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class MethodNameContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(PointcutExpressionParser.Identifier, 0);
        }

        public TerminalNode INIT() {
            return getToken(PointcutExpressionParser.INIT, 0);
        }

        public TerminalNode ASTERISK() {
            return getToken(PointcutExpressionParser.ASTERISK, 0);
        }

        public MethodNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_methodName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterMethodName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitMethodName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitMethodName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final MethodNameContext methodName() throws RecognitionException {
        MethodNameContext _localctx = new MethodNameContext(_ctx, getState());
        enterRule(_localctx, 54, RULE_methodName);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(246);
                _la = _input.LA(1);
                if (!(((_la) & ~0x3f) == 0 && ((1L << _la) & 67649536L) != 0)) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ReturnTypeContext extends ParserRuleContext {
        public VoidTypeContext voidType() {
            return getRuleContext(VoidTypeContext.class, 0);
        }

        public ReferenceTypeContext referenceType() {
            return getRuleContext(ReferenceTypeContext.class, 0);
        }

        public PrimitiveTypeContext primitiveType() {
            return getRuleContext(PrimitiveTypeContext.class, 0);
        }

        public ArrayTypeContext arrayType() {
            return getRuleContext(ArrayTypeContext.class, 0);
        }

        public TerminalNode ASTERISK() {
            return getToken(PointcutExpressionParser.ASTERISK, 0);
        }

        public ReturnTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_returnType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterReturnType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitReturnType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitReturnType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ReturnTypeContext returnType() throws RecognitionException {
        ReturnTypeContext _localctx = new ReturnTypeContext(_ctx, getState());
        enterRule(_localctx, 56, RULE_returnType);
        try {
            setState(253);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 10, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(248);
                    voidType();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(249);
                    referenceType();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(250);
                    primitiveType();
                }
                break;
                case 4:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(251);
                    arrayType();
                }
                break;
                case 5:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(252);
                    match(ASTERISK);
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ParameterTypeListContext extends ParserRuleContext {
        public List<ParameterTypeVarianceContext> parameterTypeVariance() {
            return getRuleContexts(ParameterTypeVarianceContext.class);
        }

        public ParameterTypeVarianceContext parameterTypeVariance(int i) {
            return getRuleContext(ParameterTypeVarianceContext.class, i);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(PointcutExpressionParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(PointcutExpressionParser.COMMA, i);
        }

        public List<TerminalNode> DOT() {
            return getTokens(PointcutExpressionParser.DOT);
        }

        public TerminalNode DOT(int i) {
            return getToken(PointcutExpressionParser.DOT, i);
        }

        public ParameterTypeListContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_parameterTypeList;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterParameterTypeList(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitParameterTypeList(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitParameterTypeList(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ParameterTypeListContext parameterTypeList() throws RecognitionException {
        ParameterTypeListContext _localctx = new ParameterTypeListContext(_ctx, getState());
        enterRule(_localctx, 58, RULE_parameterTypeList);
        try {
            int _alt;
            setState(266);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case T__0:
                case T__1:
                case T__2:
                case T__3:
                case T__4:
                case T__5:
                case T__6:
                case T__7:
                case Identifier:
                case ASTERISK:
                    enterOuterAlt(_localctx, 1);
                {
                    {
                        setState(260);
                        _errHandler.sync(this);
                        _alt = getInterpreter().adaptivePredict(_input, 11, _ctx);
                        while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                            if (_alt == 1) {
                                {
                                    {
                                        setState(255);
                                        parameterTypeVariance();
                                        setState(256);
                                        match(COMMA);
                                    }
                                }
                            }
                            setState(262);
                            _errHandler.sync(this);
                            _alt = getInterpreter().adaptivePredict(_input, 11, _ctx);
                        }
                        setState(263);
                        parameterTypeVariance();
                    }
                }
                break;
                case DOT:
                    enterOuterAlt(_localctx, 2);
                {
                    {
                        setState(264);
                        match(DOT);
                        setState(265);
                        match(DOT);
                    }
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ParameterTypeVarianceContext extends ParserRuleContext {
        public List<ParameterTypeContext> parameterType() {
            return getRuleContexts(ParameterTypeContext.class);
        }

        public ParameterTypeContext parameterType(int i) {
            return getRuleContext(ParameterTypeContext.class, i);
        }

        public List<TerminalNode> SEP() {
            return getTokens(PointcutExpressionParser.SEP);
        }

        public TerminalNode SEP(int i) {
            return getToken(PointcutExpressionParser.SEP, i);
        }

        public ParameterTypeVarianceContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_parameterTypeVariance;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterParameterTypeVariance(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitParameterTypeVariance(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitParameterTypeVariance(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ParameterTypeVarianceContext parameterTypeVariance() throws RecognitionException {
        ParameterTypeVarianceContext _localctx = new ParameterTypeVarianceContext(_ctx, getState());
        enterRule(_localctx, 60, RULE_parameterTypeVariance);
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(273);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 13, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        {
                            {
                                setState(268);
                                parameterType();
                                setState(269);
                                match(SEP);
                            }
                        }
                    }
                    setState(275);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 13, _ctx);
                }
                setState(276);
                parameterType();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ParameterTypeContext extends ParserRuleContext {
        public ReferenceTypeContext referenceType() {
            return getRuleContext(ReferenceTypeContext.class, 0);
        }

        public CovariantReferenceTypeContext covariantReferenceType() {
            return getRuleContext(CovariantReferenceTypeContext.class, 0);
        }

        public PrimitiveTypeContext primitiveType() {
            return getRuleContext(PrimitiveTypeContext.class, 0);
        }

        public ArrayTypeContext arrayType() {
            return getRuleContext(ArrayTypeContext.class, 0);
        }

        public TerminalNode ASTERISK() {
            return getToken(PointcutExpressionParser.ASTERISK, 0);
        }

        public ParameterTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_parameterType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterParameterType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitParameterType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitParameterType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ParameterTypeContext parameterType() throws RecognitionException {
        ParameterTypeContext _localctx = new ParameterTypeContext(_ctx, getState());
        enterRule(_localctx, 62, RULE_parameterType);
        try {
            setState(283);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 14, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(278);
                    referenceType();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(279);
                    covariantReferenceType();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(280);
                    primitiveType();
                }
                break;
                case 4:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(281);
                    arrayType();
                }
                break;
                case 5:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(282);
                    match(ASTERISK);
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class CovariantReferenceTypeContext extends ParserRuleContext {
        public ReferenceTypeContext referenceType() {
            return getRuleContext(ReferenceTypeContext.class, 0);
        }

        public TerminalNode PLUS() {
            return getToken(PointcutExpressionParser.PLUS, 0);
        }

        public CovariantReferenceTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_covariantReferenceType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterCovariantReferenceType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitCovariantReferenceType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitCovariantReferenceType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final CovariantReferenceTypeContext covariantReferenceType() throws RecognitionException {
        CovariantReferenceTypeContext _localctx = new CovariantReferenceTypeContext(_ctx, getState());
        enterRule(_localctx, 64, RULE_covariantReferenceType);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(285);
                referenceType();
                setState(286);
                match(PLUS);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArrayTypeContext extends ParserRuleContext {
        public PrimitiveTypeContext primitiveType() {
            return getRuleContext(PrimitiveTypeContext.class, 0);
        }

        public ReferenceTypeContext referenceType() {
            return getRuleContext(ReferenceTypeContext.class, 0);
        }

        public List<ArraySuffixContext> arraySuffix() {
            return getRuleContexts(ArraySuffixContext.class);
        }

        public ArraySuffixContext arraySuffix(int i) {
            return getRuleContext(ArraySuffixContext.class, i);
        }

        public ArrayTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterArrayType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitArrayType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitArrayType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArrayTypeContext arrayType() throws RecognitionException {
        ArrayTypeContext _localctx = new ArrayTypeContext(_ctx, getState());
        enterRule(_localctx, 66, RULE_arrayType);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(290);
                _errHandler.sync(this);
                switch (_input.LA(1)) {
                    case T__0:
                    case T__1:
                    case T__2:
                    case T__3:
                    case T__4:
                    case T__5:
                    case T__6:
                    case T__7: {
                        setState(288);
                        primitiveType();
                    }
                    break;
                    case Identifier: {
                        setState(289);
                        referenceType();
                    }
                    break;
                    default:
                        throw new NoViableAltException(this);
                }
                setState(293);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(292);
                            arraySuffix();
                        }
                    }
                    setState(295);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                } while (_la == LBRACK);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArraySuffixContext extends ParserRuleContext {
        public TerminalNode LBRACK() {
            return getToken(PointcutExpressionParser.LBRACK, 0);
        }

        public TerminalNode RBRACK() {
            return getToken(PointcutExpressionParser.RBRACK, 0);
        }

        public ArraySuffixContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arraySuffix;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterArraySuffix(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitArraySuffix(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitArraySuffix(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArraySuffixContext arraySuffix() throws RecognitionException {
        ArraySuffixContext _localctx = new ArraySuffixContext(_ctx, getState());
        enterRule(_localctx, 68, RULE_arraySuffix);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(297);
                match(LBRACK);
                setState(298);
                match(RBRACK);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class PrimitiveTypeContext extends ParserRuleContext {
        public PrimitiveTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_primitiveType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterPrimitiveType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitPrimitiveType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitPrimitiveType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final PrimitiveTypeContext primitiveType() throws RecognitionException {
        PrimitiveTypeContext _localctx = new PrimitiveTypeContext(_ctx, getState());
        enterRule(_localctx, 70, RULE_primitiveType);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(300);
                _la = _input.LA(1);
                if (!(((_la) & ~0x3f) == 0 && ((1L << _la) & 510L) != 0)) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class VoidTypeContext extends ParserRuleContext {
        public VoidTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_voidType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterVoidType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitVoidType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitVoidType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final VoidTypeContext voidType() throws RecognitionException {
        VoidTypeContext _localctx = new VoidTypeContext(_ctx, getState());
        enterRule(_localctx, 72, RULE_voidType);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(302);
                match(T__8);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class WithinContext extends ParserRuleContext {
        public WithinContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_within;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterWithin(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitWithin(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitWithin(this);
            else return visitor.visitChildren(this);
        }
    }

    public final WithinContext within() throws RecognitionException {
        WithinContext _localctx = new WithinContext(_ctx, getState());
        enterRule(_localctx, 74, RULE_within);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(304);
                match(T__9);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ExecutionContext extends ParserRuleContext {
        public ExecutionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_execution;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterExecution(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitExecution(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitExecution(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ExecutionContext execution() throws RecognitionException {
        ExecutionContext _localctx = new ExecutionContext(_ctx, getState());
        enterRule(_localctx, 76, RULE_execution);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(306);
                match(T__10);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class AutowireContext extends ParserRuleContext {
        public AutowireContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_autowire;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterAutowire(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitAutowire(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitAutowire(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AutowireContext autowire() throws RecognitionException {
        AutowireContext _localctx = new AutowireContext(_ctx, getState());
        enterRule(_localctx, 78, RULE_autowire);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(308);
                match(T__11);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TargetContext extends ParserRuleContext {
        public TargetContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_target;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterTarget(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitTarget(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitTarget(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TargetContext target() throws RecognitionException {
        TargetContext _localctx = new TargetContext(_ctx, getState());
        enterRule(_localctx, 80, RULE_target);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(310);
                match(T__12);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ReferenceTypeContext extends ParserRuleContext {
        public List<TerminalNode> Identifier() {
            return getTokens(PointcutExpressionParser.Identifier);
        }

        public TerminalNode Identifier(int i) {
            return getToken(PointcutExpressionParser.Identifier, i);
        }

        public List<TerminalNode> DOT() {
            return getTokens(PointcutExpressionParser.DOT);
        }

        public TerminalNode DOT(int i) {
            return getToken(PointcutExpressionParser.DOT, i);
        }

        public ReferenceTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_referenceType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).enterReferenceType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof PointcutExpressionListener)
                ((PointcutExpressionListener) listener).exitReferenceType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof PointcutExpressionVisitor)
                return ((PointcutExpressionVisitor<? extends T>) visitor).visitReferenceType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ReferenceTypeContext referenceType() throws RecognitionException {
        ReferenceTypeContext _localctx = new ReferenceTypeContext(_ctx, getState());
        enterRule(_localctx, 82, RULE_referenceType);
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(316);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 17, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        {
                            {
                                setState(312);
                                match(Identifier);
                                setState(313);
                                match(DOT);
                            }
                        }
                    }
                    setState(318);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 17, _ctx);
                }
                setState(319);
                match(Identifier);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static final String _serializedATN =
            "\u0004\u0001\u001d\u0142\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001" +
                    "\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004" +
                    "\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007" +
                    "\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b" +
                    "\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007" +
                    "\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007" +
                    "\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007" +
                    "\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007" +
                    "\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007" +
                    "\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007" +
                    "\u001e\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007" +
                    "\"\u0002#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007" +
                    "\'\u0002(\u0007(\u0002)\u0007)\u0001\u0000\u0001\u0000\u0003\u0000W\b" +
                    "\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001]\b" +
                    "\u0001\u0001\u0001\u0005\u0001`\b\u0001\n\u0001\f\u0001c\t\u0001\u0001" +
                    "\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001" +
                    "\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001" +
                    "\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001" +
                    "\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001" +
                    "\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u0081\b\u0006\u0001" +
                    "\u0006\u0005\u0006\u0084\b\u0006\n\u0006\f\u0006\u0087\t\u0006\u0001\u0007" +
                    "\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001" +
                    "\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001" +
                    "\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b" +
                    "\u0001\u000b\u0001\u000b\u0003\u000b\u00a4\b\u000b\u0001\f\u0001\f\u0001" +
                    "\f\u0001\r\u0001\r\u0001\r\u0001\r\u0003\r\u00ad\b\r\u0001\u000e\u0001" +
                    "\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001" +
                    "\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001" +
                    "\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001" +
                    "\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001" +
                    "\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u00cb\b\u0012\u0001\u0013\u0001" +
                    "\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001" +
                    "\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0001" +
                    "\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001" +
                    "\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001" +
                    "\u0017\u0001\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0003\u0018\u00eb" +
                    "\b\u0018\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001" +
                    "\u001a\u0003\u001a\u00f3\b\u001a\u0001\u001a\u0001\u001a\u0001\u001b\u0001" +
                    "\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0003" +
                    "\u001c\u00fe\b\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0005\u001d\u0103" +
                    "\b\u001d\n\u001d\f\u001d\u0106\t\u001d\u0001\u001d\u0001\u001d\u0001\u001d" +
                    "\u0003\u001d\u010b\b\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0005\u001e" +
                    "\u0110\b\u001e\n\u001e\f\u001e\u0113\t\u001e\u0001\u001e\u0001\u001e\u0001" +
                    "\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0003\u001f\u011c" +
                    "\b\u001f\u0001 \u0001 \u0001 \u0001!\u0001!\u0003!\u0123\b!\u0001!\u0004" +
                    "!\u0126\b!\u000b!\f!\u0127\u0001\"\u0001\"\u0001\"\u0001#\u0001#\u0001" +
                    "$\u0001$\u0001%\u0001%\u0001&\u0001&\u0001\'\u0001\'\u0001(\u0001(\u0001" +
                    ")\u0001)\u0005)\u013b\b)\n)\f)\u013e\t)\u0001)\u0001)\u0001)\u0000\u0000" +
                    "*\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a" +
                    "\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPR\u0000\u0004\u0001\u0000\u0014" +
                    "\u0015\u0002\u0000\u000e\u000e\u001a\u001a\u0003\u0000\u000e\u000e\u0013" +
                    "\u0013\u001a\u001a\u0001\u0000\u0001\b\u0137\u0000V\u0001\u0000\u0000" +
                    "\u0000\u0002\\\u0001\u0000\u0000\u0000\u0004d\u0001\u0000\u0000\u0000" +
                    "\u0006j\u0001\u0000\u0000\u0000\bo\u0001\u0000\u0000\u0000\nu\u0001\u0000" +
                    "\u0000\u0000\f\u0080\u0001\u0000\u0000\u0000\u000e\u0088\u0001\u0000\u0000" +
                    "\u0000\u0010\u008d\u0001\u0000\u0000\u0000\u0012\u0093\u0001\u0000\u0000" +
                    "\u0000\u0014\u0099\u0001\u0000\u0000\u0000\u0016\u00a0\u0001\u0000\u0000" +
                    "\u0000\u0018\u00a5\u0001\u0000\u0000\u0000\u001a\u00ac\u0001\u0000\u0000" +
                    "\u0000\u001c\u00ae\u0001\u0000\u0000\u0000\u001e\u00b3\u0001\u0000\u0000" +
                    "\u0000 \u00b9\u0001\u0000\u0000\u0000\"\u00bf\u0001\u0000\u0000\u0000" +
                    "$\u00ca\u0001\u0000\u0000\u0000&\u00cc\u0001\u0000\u0000\u0000(\u00d1" +
                    "\u0001\u0000\u0000\u0000*\u00d7\u0001\u0000\u0000\u0000,\u00dd\u0001\u0000" +
                    "\u0000\u0000.\u00e4\u0001\u0000\u0000\u00000\u00ea\u0001\u0000\u0000\u0000" +
                    "2\u00ec\u0001\u0000\u0000\u00004\u00ee\u0001\u0000\u0000\u00006\u00f6" +
                    "\u0001\u0000\u0000\u00008\u00fd\u0001\u0000\u0000\u0000:\u010a\u0001\u0000" +
                    "\u0000\u0000<\u0111\u0001\u0000\u0000\u0000>\u011b\u0001\u0000\u0000\u0000" +
                    "@\u011d\u0001\u0000\u0000\u0000B\u0122\u0001\u0000\u0000\u0000D\u0129" +
                    "\u0001\u0000\u0000\u0000F\u012c\u0001\u0000\u0000\u0000H\u012e\u0001\u0000" +
                    "\u0000\u0000J\u0130\u0001\u0000\u0000\u0000L\u0132\u0001\u0000\u0000\u0000" +
                    "N\u0134\u0001\u0000\u0000\u0000P\u0136\u0001\u0000\u0000\u0000R\u013c" +
                    "\u0001\u0000\u0000\u0000TW\u0003\u0002\u0001\u0000UW\u0003\f\u0006\u0000" +
                    "VT\u0001\u0000\u0000\u0000VU\u0001\u0000\u0000\u0000W\u0001\u0001\u0000" +
                    "\u0000\u0000X]\u0003\u0004\u0002\u0000Y]\u0003\u0006\u0003\u0000Z]\u0003" +
                    "\b\u0004\u0000[]\u0003\n\u0005\u0000\\X\u0001\u0000\u0000\u0000\\Y\u0001" +
                    "\u0000\u0000\u0000\\Z\u0001\u0000\u0000\u0000\\[\u0001\u0000\u0000\u0000" +
                    "]a\u0001\u0000\u0000\u0000^`\u0003\u0018\f\u0000_^\u0001\u0000\u0000\u0000" +
                    "`c\u0001\u0000\u0000\u0000a_\u0001\u0000\u0000\u0000ab\u0001\u0000\u0000" +
                    "\u0000b\u0003\u0001\u0000\u0000\u0000ca\u0001\u0000\u0000\u0000de\u0005" +
                    "\u0018\u0000\u0000ef\u0003N\'\u0000fg\u0005\u0011\u0000\u0000gh\u0003" +
                    ".\u0017\u0000hi\u0005\u0012\u0000\u0000i\u0005\u0001\u0000\u0000\u0000" +
                    "jk\u0003N\'\u0000kl\u0005\u0011\u0000\u0000lm\u0003.\u0017\u0000mn\u0005" +
                    "\u0012\u0000\u0000n\u0007\u0001\u0000\u0000\u0000op\u0005\u0017\u0000" +
                    "\u0000pq\u0003N\'\u0000qr\u0005\u0011\u0000\u0000rs\u0003.\u0017\u0000" +
                    "st\u0005\u0012\u0000\u0000t\t\u0001\u0000\u0000\u0000uv\u0005\u0018\u0000" +
                    "\u0000vw\u0005\u0017\u0000\u0000wx\u0003N\'\u0000xy\u0005\u0011\u0000" +
                    "\u0000yz\u0003.\u0017\u0000z{\u0005\u0012\u0000\u0000{\u000b\u0001\u0000" +
                    "\u0000\u0000|\u0081\u0003\u000e\u0007\u0000}\u0081\u0003\u0010\b\u0000" +
                    "~\u0081\u0003\u0012\t\u0000\u007f\u0081\u0003\u0014\n\u0000\u0080|\u0001" +
                    "\u0000\u0000\u0000\u0080}\u0001\u0000\u0000\u0000\u0080~\u0001\u0000\u0000" +
                    "\u0000\u0080\u007f\u0001\u0000\u0000\u0000\u0081\u0085\u0001\u0000\u0000" +
                    "\u0000\u0082\u0084\u0003\u0016\u000b\u0000\u0083\u0082\u0001\u0000\u0000" +
                    "\u0000\u0084\u0087\u0001\u0000\u0000\u0000\u0085\u0083\u0001\u0000\u0000" +
                    "\u0000\u0085\u0086\u0001\u0000\u0000\u0000\u0086\r\u0001\u0000\u0000\u0000" +
                    "\u0087\u0085\u0001\u0000\u0000\u0000\u0088\u0089\u0003L&\u0000\u0089\u008a" +
                    "\u0005\u0011\u0000\u0000\u008a\u008b\u00034\u001a\u0000\u008b\u008c\u0005" +
                    "\u0012\u0000\u0000\u008c\u000f\u0001\u0000\u0000\u0000\u008d\u008e\u0005" +
                    "\u0018\u0000\u0000\u008e\u008f\u0003L&\u0000\u008f\u0090\u0005\u0011\u0000" +
                    "\u0000\u0090\u0091\u00034\u001a\u0000\u0091\u0092\u0005\u0012\u0000\u0000" +
                    "\u0092\u0011\u0001\u0000\u0000\u0000\u0093\u0094\u0005\u0017\u0000\u0000" +
                    "\u0094\u0095\u0003L&\u0000\u0095\u0096\u0005\u0011\u0000\u0000\u0096\u0097" +
                    "\u0003.\u0017\u0000\u0097\u0098\u0005\u0012\u0000\u0000\u0098\u0013\u0001" +
                    "\u0000\u0000\u0000\u0099\u009a\u0005\u0018\u0000\u0000\u009a\u009b\u0005" +
                    "\u0017\u0000\u0000\u009b\u009c\u0003L&\u0000\u009c\u009d\u0005\u0011\u0000" +
                    "\u0000\u009d\u009e\u0003.\u0017\u0000\u009e\u009f\u0005\u0012\u0000\u0000" +
                    "\u009f\u0015\u0001\u0000\u0000\u0000\u00a0\u00a3\u0007\u0000\u0000\u0000" +
                    "\u00a1\u00a4\u0003$\u0012\u0000\u00a2\u00a4\u0003\u001a\r\u0000\u00a3" +
                    "\u00a1\u0001\u0000\u0000\u0000\u00a3\u00a2\u0001\u0000\u0000\u0000\u00a4" +
                    "\u0017\u0001\u0000\u0000\u0000\u00a5\u00a6\u0007\u0000\u0000\u0000\u00a6" +
                    "\u00a7\u0003\u001a\r\u0000\u00a7\u0019\u0001\u0000\u0000\u0000\u00a8\u00ad" +
                    "\u0003\u001c\u000e\u0000\u00a9\u00ad\u0003\u001e\u000f\u0000\u00aa\u00ad" +
                    "\u0003 \u0010\u0000\u00ab\u00ad\u0003\"\u0011\u0000\u00ac\u00a8\u0001" +
                    "\u0000\u0000\u0000\u00ac\u00a9\u0001\u0000\u0000\u0000\u00ac\u00aa\u0001" +
                    "\u0000\u0000\u0000\u00ac\u00ab\u0001\u0000\u0000\u0000\u00ad\u001b\u0001" +
                    "\u0000\u0000\u0000\u00ae\u00af\u0003P(\u0000\u00af\u00b0\u0005\u0011\u0000" +
                    "\u0000\u00b0\u00b1\u0003.\u0017\u0000\u00b1\u00b2\u0005\u0012\u0000\u0000" +
                    "\u00b2\u001d\u0001\u0000\u0000\u0000\u00b3\u00b4\u0005\u0018\u0000\u0000" +
                    "\u00b4\u00b5\u0003P(\u0000\u00b5\u00b6\u0005\u0011\u0000\u0000\u00b6\u00b7" +
                    "\u0003.\u0017\u0000\u00b7\u00b8\u0005\u0012\u0000\u0000\u00b8\u001f\u0001" +
                    "\u0000\u0000\u0000\u00b9\u00ba\u0005\u0017\u0000\u0000\u00ba\u00bb\u0003" +
                    "P(\u0000\u00bb\u00bc\u0005\u0011\u0000\u0000\u00bc\u00bd\u0003.\u0017" +
                    "\u0000\u00bd\u00be\u0005\u0012\u0000\u0000\u00be!\u0001\u0000\u0000\u0000" +
                    "\u00bf\u00c0\u0005\u0018\u0000\u0000\u00c0\u00c1\u0005\u0017\u0000\u0000" +
                    "\u00c1\u00c2\u0003P(\u0000\u00c2\u00c3\u0005\u0011\u0000\u0000\u00c3\u00c4" +
                    "\u0003.\u0017\u0000\u00c4\u00c5\u0005\u0012\u0000\u0000\u00c5#\u0001\u0000" +
                    "\u0000\u0000\u00c6\u00cb\u0003&\u0013\u0000\u00c7\u00cb\u0003(\u0014\u0000" +
                    "\u00c8\u00cb\u0003*\u0015\u0000\u00c9\u00cb\u0003,\u0016\u0000\u00ca\u00c6" +
                    "\u0001\u0000\u0000\u0000\u00ca\u00c7\u0001\u0000\u0000\u0000\u00ca\u00c8" +
                    "\u0001\u0000\u0000\u0000\u00ca\u00c9\u0001\u0000\u0000\u0000\u00cb%\u0001" +
                    "\u0000\u0000\u0000\u00cc\u00cd\u0003J%\u0000\u00cd\u00ce\u0005\u0011\u0000" +
                    "\u0000\u00ce\u00cf\u0003.\u0017\u0000\u00cf\u00d0\u0005\u0012\u0000\u0000" +
                    "\u00d0\'\u0001\u0000\u0000\u0000\u00d1\u00d2\u0005\u0018\u0000\u0000\u00d2" +
                    "\u00d3\u0003J%\u0000\u00d3\u00d4\u0005\u0011\u0000\u0000\u00d4\u00d5\u0003" +
                    ".\u0017\u0000\u00d5\u00d6\u0005\u0012\u0000\u0000\u00d6)\u0001\u0000\u0000" +
                    "\u0000\u00d7\u00d8\u0005\u0017\u0000\u0000\u00d8\u00d9\u0003J%\u0000\u00d9" +
                    "\u00da\u0005\u0011\u0000\u0000\u00da\u00db\u0003.\u0017\u0000\u00db\u00dc" +
                    "\u0005\u0012\u0000\u0000\u00dc+\u0001\u0000\u0000\u0000\u00dd\u00de\u0005" +
                    "\u0018\u0000\u0000\u00de\u00df\u0005\u0017\u0000\u0000\u00df\u00e0\u0003" +
                    "J%\u0000\u00e0\u00e1\u0005\u0011\u0000\u0000\u00e1\u00e2\u0003.\u0017" +
                    "\u0000\u00e2\u00e3\u0005\u0012\u0000\u0000\u00e3-\u0001\u0000\u0000\u0000" +
                    "\u00e4\u00e5\u00030\u0018\u0000\u00e5\u00e6\u0005\u001b\u0000\u0000\u00e6" +
                    "\u00e7\u00032\u0019\u0000\u00e7/\u0001\u0000\u0000\u0000\u00e8\u00eb\u0003" +
                    "R)\u0000\u00e9\u00eb\u0005\u001a\u0000\u0000\u00ea\u00e8\u0001\u0000\u0000" +
                    "\u0000\u00ea\u00e9\u0001\u0000\u0000\u0000\u00eb1\u0001\u0000\u0000\u0000" +
                    "\u00ec\u00ed\u0007\u0001\u0000\u0000\u00ed3\u0001\u0000\u0000\u0000\u00ee" +
                    "\u00ef\u00038\u001c\u0000\u00ef\u00f0\u00036\u001b\u0000\u00f0\u00f2\u0005" +
                    "\u0011\u0000\u0000\u00f1\u00f3\u0003:\u001d\u0000\u00f2\u00f1\u0001\u0000" +
                    "\u0000\u0000\u00f2\u00f3\u0001\u0000\u0000\u0000\u00f3\u00f4\u0001\u0000" +
                    "\u0000\u0000\u00f4\u00f5\u0005\u0012\u0000\u0000\u00f55\u0001\u0000\u0000" +
                    "\u0000\u00f6\u00f7\u0007\u0002\u0000\u0000\u00f77\u0001\u0000\u0000\u0000" +
                    "\u00f8\u00fe\u0003H$\u0000\u00f9\u00fe\u0003R)\u0000\u00fa\u00fe\u0003" +
                    "F#\u0000\u00fb\u00fe\u0003B!\u0000\u00fc\u00fe\u0005\u001a\u0000\u0000" +
                    "\u00fd\u00f8\u0001\u0000\u0000\u0000\u00fd\u00f9\u0001\u0000\u0000\u0000" +
                    "\u00fd\u00fa\u0001\u0000\u0000\u0000\u00fd\u00fb\u0001\u0000\u0000\u0000" +
                    "\u00fd\u00fc\u0001\u0000\u0000\u0000\u00fe9\u0001\u0000\u0000\u0000\u00ff" +
                    "\u0100\u0003<\u001e\u0000\u0100\u0101\u0005\u001c\u0000\u0000\u0101\u0103" +
                    "\u0001\u0000\u0000\u0000\u0102\u00ff\u0001\u0000\u0000\u0000\u0103\u0106" +
                    "\u0001\u0000\u0000\u0000\u0104\u0102\u0001\u0000\u0000\u0000\u0104\u0105" +
                    "\u0001\u0000\u0000\u0000\u0105\u0107\u0001\u0000\u0000\u0000\u0106\u0104" +
                    "\u0001\u0000\u0000\u0000\u0107\u010b\u0003<\u001e\u0000\u0108\u0109\u0005" +
                    "\u001b\u0000\u0000\u0109\u010b\u0005\u001b\u0000\u0000\u010a\u0104\u0001" +
                    "\u0000\u0000\u0000\u010a\u0108\u0001\u0000\u0000\u0000\u010b;\u0001\u0000" +
                    "\u0000\u0000\u010c\u010d\u0003>\u001f\u0000\u010d\u010e\u0005\u0016\u0000" +
                    "\u0000\u010e\u0110\u0001\u0000\u0000\u0000\u010f\u010c\u0001\u0000\u0000" +
                    "\u0000\u0110\u0113\u0001\u0000\u0000\u0000\u0111\u010f\u0001\u0000\u0000" +
                    "\u0000\u0111\u0112\u0001\u0000\u0000\u0000\u0112\u0114\u0001\u0000\u0000" +
                    "\u0000\u0113\u0111\u0001\u0000\u0000\u0000\u0114\u0115\u0003>\u001f\u0000" +
                    "\u0115=\u0001\u0000\u0000\u0000\u0116\u011c\u0003R)\u0000\u0117\u011c" +
                    "\u0003@ \u0000\u0118\u011c\u0003F#\u0000\u0119\u011c\u0003B!\u0000\u011a" +
                    "\u011c\u0005\u001a\u0000\u0000\u011b\u0116\u0001\u0000\u0000\u0000\u011b" +
                    "\u0117\u0001\u0000\u0000\u0000\u011b\u0118\u0001\u0000\u0000\u0000\u011b" +
                    "\u0119\u0001\u0000\u0000\u0000\u011b\u011a\u0001\u0000\u0000\u0000\u011c" +
                    "?\u0001\u0000\u0000\u0000\u011d\u011e\u0003R)\u0000\u011e\u011f\u0005" +
                    "\u0019\u0000\u0000\u011fA\u0001\u0000\u0000\u0000\u0120\u0123\u0003F#" +
                    "\u0000\u0121\u0123\u0003R)\u0000\u0122\u0120\u0001\u0000\u0000\u0000\u0122" +
                    "\u0121\u0001\u0000\u0000\u0000\u0123\u0125\u0001\u0000\u0000\u0000\u0124" +
                    "\u0126\u0003D\"\u0000\u0125\u0124\u0001\u0000\u0000\u0000\u0126\u0127" +
                    "\u0001\u0000\u0000\u0000\u0127\u0125\u0001\u0000\u0000\u0000\u0127\u0128" +
                    "\u0001\u0000\u0000\u0000\u0128C\u0001\u0000\u0000\u0000\u0129\u012a\u0005" +
                    "\u000f\u0000\u0000\u012a\u012b\u0005\u0010\u0000\u0000\u012bE\u0001\u0000" +
                    "\u0000\u0000\u012c\u012d\u0007\u0003\u0000\u0000\u012dG\u0001\u0000\u0000" +
                    "\u0000\u012e\u012f\u0005\t\u0000\u0000\u012fI\u0001\u0000\u0000\u0000" +
                    "\u0130\u0131\u0005\n\u0000\u0000\u0131K\u0001\u0000\u0000\u0000\u0132" +
                    "\u0133\u0005\u000b\u0000\u0000\u0133M\u0001\u0000\u0000\u0000\u0134\u0135" +
                    "\u0005\f\u0000\u0000\u0135O\u0001\u0000\u0000\u0000\u0136\u0137\u0005" +
                    "\r\u0000\u0000\u0137Q\u0001\u0000\u0000\u0000\u0138\u0139\u0005\u000e" +
                    "\u0000\u0000\u0139\u013b\u0005\u001b\u0000\u0000\u013a\u0138\u0001\u0000" +
                    "\u0000\u0000\u013b\u013e\u0001\u0000\u0000\u0000\u013c\u013a\u0001\u0000" +
                    "\u0000\u0000\u013c\u013d\u0001\u0000\u0000\u0000\u013d\u013f\u0001\u0000" +
                    "\u0000\u0000\u013e\u013c\u0001\u0000\u0000\u0000\u013f\u0140\u0005\u000e" +
                    "\u0000\u0000\u0140S\u0001\u0000\u0000\u0000\u0012V\\a\u0080\u0085\u00a3" +
                    "\u00ac\u00ca\u00ea\u00f2\u00fd\u0104\u010a\u0111\u011b\u0122\u0127\u013c";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}

interface PointcutExpressionVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExpression(PointcutExpressionParser.ExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#injectPointcutExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInjectPointcutExpression(PointcutExpressionParser.InjectPointcutExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateInject}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateInject(PointcutExpressionParser.NegateInjectContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#inject}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInject(PointcutExpressionParser.InjectContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#annotatedInject}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAnnotatedInject(PointcutExpressionParser.AnnotatedInjectContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedInject}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateAnnotatedInject(PointcutExpressionParser.NegateAnnotatedInjectContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#executionPointcutExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExecutionPointcutExpression(PointcutExpressionParser.ExecutionPointcutExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#methodExecution}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMethodExecution(PointcutExpressionParser.MethodExecutionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateMethodExecution}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateMethodExecution(PointcutExpressionParser.NegateMethodExecutionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#annotatedMethodExecution}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAnnotatedMethodExecution(PointcutExpressionParser.AnnotatedMethodExecutionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedMethodExecution}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateAnnotatedMethodExecution(PointcutExpressionParser.NegateAnnotatedMethodExecutionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#executionGuards}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExecutionGuards(PointcutExpressionParser.ExecutionGuardsContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#injectGuards}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInjectGuards(PointcutExpressionParser.InjectGuardsContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#targetGuardExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTargetGuardExpression(PointcutExpressionParser.TargetGuardExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#targetGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTargetGuard(PointcutExpressionParser.TargetGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateTargetGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateTargetGuard(PointcutExpressionParser.NegateTargetGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#annotatedTargetGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAnnotatedTargetGuard(PointcutExpressionParser.AnnotatedTargetGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedTargetGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateAnnotatedTargetGuard(PointcutExpressionParser.NegateAnnotatedTargetGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#withinGuardExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitWithinGuardExpression(PointcutExpressionParser.WithinGuardExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#withinGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitWithinGuard(PointcutExpressionParser.WithinGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateWithinGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateWithinGuard(PointcutExpressionParser.NegateWithinGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#annotatedWithinGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAnnotatedWithinGuard(PointcutExpressionParser.AnnotatedWithinGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#negateAnnotatedWithinGuard}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNegateAnnotatedWithinGuard(PointcutExpressionParser.NegateAnnotatedWithinGuardContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#typeExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTypeExpression(PointcutExpressionParser.TypeExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#packageName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPackageName(PointcutExpressionParser.PackageNameContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#typeName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTypeName(PointcutExpressionParser.TypeNameContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#methodExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMethodExpression(PointcutExpressionParser.MethodExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#methodName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMethodName(PointcutExpressionParser.MethodNameContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#returnType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReturnType(PointcutExpressionParser.ReturnTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#parameterTypeList}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParameterTypeList(PointcutExpressionParser.ParameterTypeListContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#parameterTypeVariance}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParameterTypeVariance(ParameterTypeVarianceContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#parameterType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParameterType(PointcutExpressionParser.ParameterTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#covariantReferenceType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitCovariantReferenceType(PointcutExpressionParser.CovariantReferenceTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#arrayType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArrayType(PointcutExpressionParser.ArrayTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#arraySuffix}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArraySuffix(PointcutExpressionParser.ArraySuffixContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#primitiveType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPrimitiveType(PointcutExpressionParser.PrimitiveTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#voidType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitVoidType(PointcutExpressionParser.VoidTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#within}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitWithin(PointcutExpressionParser.WithinContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#execution}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExecution(PointcutExpressionParser.ExecutionContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#autowire}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAutowire(PointcutExpressionParser.AutowireContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#target}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTarget(PointcutExpressionParser.TargetContext ctx);

    /**
     * Visit a parse tree produced by {@link PointcutExpressionParser#referenceType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReferenceType(PointcutExpressionParser.ReferenceTypeContext ctx);
}


