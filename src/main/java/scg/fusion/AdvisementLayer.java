package scg.fusion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scg.fusion.annotation.Around;
import scg.fusion.cglib.proxy.Callback;
import scg.fusion.cglib.proxy.ExecutionInterceptor;
import scg.fusion.cglib.proxy.MethodProxy;
import scg.fusion.exceptions.IllegalContractException;
import scg.fusion.exceptions.PointcutExpressionSyntaxError;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static scg.fusion.OnTheFlyFactory.*;
import static scg.fusion.Pointcut.parse;
import static scg.fusion.Pointcuts.*;
import static scg.fusion.Utils.*;
import static scg.fusion.Utils.getActualComponentType;

final class AdvisementLayer implements ExecutionInterceptor, AutowireInterceptor, ConstructorFactory, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AdvisementLayer.class);

    private static final Map<String, Pointcut> pointcuts = new HashMap<>();

    private boolean defined;

    final Callback[] callbacks = new Callback[]{this};

    private final Set<Class<?>> proxies = new HashSet<>();

    private final Set<Class<?>> aspects = new HashSet<>();

    private final Set<Class<?>> newProxies = new HashSet<>();

    private final Set<Class<?>> initProxies = new HashSet<>();

    private final ConstructorFactory converter = newConstructorConverter();

    private final Map<Class<?>, Map<String, Field>> wireProxies = new HashMap<>();

    private final Map<Field, Set<AutowireAdvisor>> autowireAdvisement = new HashMap<>();

    private final Map<Method, Set<ExecutionAdvisor>> executionAdvisement = new HashMap<>();

    AdvisementLayer(Set<Class<?>> componentTypes, ComponentFactory components) {

        Map<Class<?>, Set<Method>> advices = classifyAdvices(componentTypes);

        for (Class<?> aspectType : advices.keySet()) {

            for (Method advice : advices.get(aspectType)) {

                boolean advised = false;

                for (Pointcut crosscut : parseExecutionCrosscut(advice)) {

                    for (Method method : filterMethods(componentTypes, crosscut)) {

                        Class<?> componentType = method.getDeclaringClass();

                        advised = true;

                        if (isInit(method)) {
                            initProxies.add(componentType);
                        } else if (isNew(method)) {
                            newProxies.add(componentType);
                        } else {
                            proxies.add(componentType);
                        }

                        executionAdvisement.compute(method, append(newExecutionAdvisor(crosscut, aspectType, advice, components.getProvider(aspectType), getPrivilegeLevel(advice))));
                    }
                }

                if (!advised) {
                    log.info("Dummy advice detected [{}]", advice);
                }
            }

            aspects.add(aspectType);
        }

        for (Class<?> aspectType : advices.keySet()) {

            boolean advised = false;

            for (Method advice : advices.get(aspectType)) {

                for (Pointcut crosscut : parseAutowireCrosscut(advice)) {

                    for (Field field : filterFields(componentTypes, crosscut)) {

                        advised = true;

                        if (isAutowired(field)) {
                            wireProxies.compute(field.getDeclaringClass(), append(field));
                        }

                        autowireAdvisement.compute(field, append(newAutowireAdvisor(crosscut, aspectType, advice, components.getProvider(aspectType), -getPrivilegeLevel(advice))));
                    }
                }

                if (!advised) {
                    log.info("Dummy advice detected [{}]", advice);
                }
            }

            aspects.add(aspectType);
        }

        this.defined = !executionAdvisement.isEmpty() || !autowireAdvisement.isEmpty();

        for (Class<?> aspect : advices.keySet()) {
            if (isAspectSpecImpl(aspect)) {
                asList(aspect.getInterfaces()).forEach(componentTypes::remove);
                // swap abstraction with implementation
                componentTypes.add(aspect);
            }
        }

    }

    @Override
    public void close() {
        pointcuts.clear();
        proxies.clear();
        aspects.clear();
        newProxies.clear();
        initProxies.clear();
        wireProxies.clear();
        defined = false;
    }

    @Override
    public Object intercept(Object component, Field field, ComponentProvider<?> dependency) {

        Class<?> targetType = getActualComponentType(component.getClass());

        AutowireInterceptor entryPoint = (null);

        for (AutowireAdvisor advisor : autowireAdvisement.getOrDefault(field, emptySet())) {
            entryPoint = isNull(entryPoint) ? delayJp(advisor) : delayAjp(advisor, entryPoint);
        }

        if (isNull(entryPoint)) {
            return dependency.getComponent();
        } else {
            return entryPoint.intercept(component, field, dependency);
        }
    }

    @Override
    public Object intercept(Class<?> callSide, Object component, Method method, Object[] args, MethodProxy proxy) throws Throwable {

        callSide = getActualComponentType(callSide);

        ExecutionInterceptor entryPoint = (null);

        for (ExecutionAdvisor advisor : executionAdvisement.getOrDefault(method, emptySet())) {
            if (!advisor.isWithinGuard()) {
                entryPoint = isNull(entryPoint) ? delayJp(advisor) : delayAjp(advisor, entryPoint);
            } else if (advisor.match(callSide, method)) {
                entryPoint = isNull(entryPoint) ? delayJp(advisor) : delayAjp(advisor, entryPoint);
            }
        }

        if (isNull(entryPoint)) {
            return proxy.invokeSuper(component, args);
        } else {
            return entryPoint.intercept(callSide, component, method, args, proxy);
        }
    }

    Map<String, Field> getAutowiring(Class<?> componentType) {
        return wireProxies.get(componentType);
    }

    boolean isAspect(Class<?> componentType) {
        return defined && aspects.contains(componentType);
    }

    boolean isProxy(Class<?> componentType) {
        return defined && proxies.contains(componentType);
    }

    boolean isInitProxy(Class<?> componentType) {
        return defined && initProxies.contains(componentType);
    }

    boolean isWireProxy(Class<?> componentType) {
        return defined && wireProxies.containsKey(componentType);
    }

    boolean isNewProxy(Class<?> componentType) {
        return defined && newProxies.contains(componentType);
    }

    @Override
    public Method toInitMethod(Constructor<?> constructor) {

        if (nonNull(constructor)) {

            Class<?> componentType = constructor.getDeclaringClass();

            if (defined && initProxies.contains(componentType)) {
                return this.converter.toInitMethod(constructor);
            }
        }

        return (null);

    }

    @Override
    public Method toNewMethod(Class<?> componentType, int modifiers) {
        return converter.toNewMethod(componentType, modifiers);
    }

    static AutowireInterceptor delayAutowireAdvice() {
        return ($, $$, provider) -> provider.getComponent();
    }

    Method toNewMethod(Constructor<?> constructor) {
        return toNewMethod(constructor.getDeclaringClass(), constructor.getModifiers());
    }

    private static Maybe<Pointcut> parseDivergentCrosscut(Method advice) {

        List<Pointcut> executionCrosscut = new ArrayList<>();
        List<Pointcut> autowireCrosscut  = new ArrayList<>();

        for (Around around : advice.getAnnotationsByType(Around.class)) {

            String expression = around.value();

            Pointcut pointcut = computePointcutExpression(expression);

            if (pointcut.isAutowire()) {
                autowireCrosscut.add(pointcut);
                continue;
            }

            if (pointcut.isExecution()) {
                executionCrosscut.add(pointcut);
                continue;
            }

            throw new IllegalContractException("Unknown advice [%s] pointcut expression [%s]", advice, expression);

        }

        Maybe<Pointcut> maybeAutowirePointcut = autowireCrosscut(autowireCrosscut);

        for (Pointcut executionPointcut : executionCrosscut(executionCrosscut)) {

            for (Pointcut autowirePointcut : maybeAutowirePointcut) {
                return Maybe.pure(divergentOr(autowirePointcut, executionPointcut));
            }

            return Maybe.pure(executionPointcut);

        }

        return maybeAutowirePointcut;

    }

    private static Maybe<Pointcut> parseExecutionCrosscut(Method advice) {

        if (isDivergentCrosscutAdvice(advice)) {
            return parseDivergentCrosscut(advice);
        }

        if (isAutowireCrosscutAdvice(advice)) {
            return Maybe.empty();
        }

        List<Pointcut> crosscut = new ArrayList<>();

        for (Around around : advice.getAnnotationsByType(Around.class)) {

            Pointcut pointcut = computePointcutExpression(around.value());

            if (pointcut.isAutowire()) {
                throw new IllegalContractException("divergent crosscut advice [%s]", advice);
            }

            crosscut.add(pointcut);

        }

        return executionCrosscut(crosscut);
    }

    private static Maybe<Pointcut> parseAutowireCrosscut(Method advice) {

        if (isDivergentCrosscutAdvice(advice)) {
            return parseDivergentCrosscut(advice);
        }

        if (isExecutionCrosscutAdvice(advice)) {
            return Maybe.empty();
        }

        List<Pointcut> crosscut = new ArrayList<>();

        for (Around around : listCrosscut(advice)) {

            Pointcut pointcut = computePointcutExpression(around.value());

            if (pointcut.isExecution()) {
                throw new IllegalContractException("divergent crosscut advice [%s]", advice);
            }

            crosscut.add(pointcut);

        }

        return autowireCrosscut(crosscut);

    }

    private static Pointcut computePointcutExpression(String e) {
        try {
            return pointcuts.compute(e, ($, p) -> nonNull(p) ? p : parse(e));
        } catch (PointcutExpressionSyntaxError cause) {
            throw new IllegalContractException("@Around(%s) %s at position %d", cause.expression, cause.message, cause.position);
        }
    }

    private static ExecutionInterceptor delayExecutionAdvice() {
        return (callSide, component, method, args, proxy) -> proxy.invokeSuper(component, args);
    }

    private static AutowireInterceptor delayAjp(AutowireAdvisor advisor, AutowireInterceptor advice) {
        return (component, field, dependency) -> advisor.advise(new AdvisedAutowireJoinPoint(component, field, dependency, advice));
    }

    private static AutowireInterceptor delayJp(AutowireAdvisor advisor) {
        return (component, field, dependency) -> advisor.advise(new AutowireJoinPointImpl(component, field, dependency));
    }

    private static ExecutionInterceptor delayAjp(ExecutionAdvisor advisor, ExecutionInterceptor advice) {
        return (callSide, component, method, args, proxy) -> advisor.advise(new AdvisedExecutionJoinPoint(callSide, component, method, proxy, advice, args));
    }

    private static ExecutionInterceptor delayJp(ExecutionAdvisor advisor) {
        return (callSide, component, method, args, proxy) -> advisor.advise(new ExecutionJoinPointImpl(callSide, component, method, proxy, args));
    }
}
