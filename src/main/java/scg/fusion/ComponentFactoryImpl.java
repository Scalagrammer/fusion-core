package scg.fusion;

import scg.fusion.cglib.core.DefaultGeneratorStrategy;
import scg.fusion.cglib.core.DefaultNamingPolicy;
import scg.fusion.cglib.proxy.Enhancer;
import scg.fusion.exceptions.IllegalContractException;
import scg.fusion.exceptions.PointcutExpressionSyntaxError;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isStatic;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static scg.fusion.ComponentScopeService.newAspectScope;
import static scg.fusion.ComponentScopeServiceDecorator.instance;
import static scg.fusion.OnTheFlyFactory.*;
import static scg.fusion.Pointcut.parse;
import static scg.fusion.Utils.*;

import static scg.fusion.Verification.listVerifiedFactories;
import static scg.fusion.cglib.proxy.Enhancer.registerStaticCallbacks;

class ComponentFactoryImpl extends MessageBrokerImpl implements ComponentFactory, LifecycleListener {

    boolean override = false;

    final Map<Class<?>, Map<Class<?>, Set<AutowiringHook>>> autowiringCache = new ConcurrentHashMap<>();

    final Map<Class<?>, ComponentScope> byTypeStore = new LinkedHashMap<>();

    final AdvisementLayer advisement;

    final Environment environment;

    private ComponentFactoryImpl(AdvisementLayer advisement, Environment environment) {
        this.advisement = advisement;
        this.environment = environment;
    }

    ComponentFactoryImpl(Set<Class<?>> componentTypes, Environment environment) {

        this.environment = environment;

        this.advisement = new AdvisementLayer(componentTypes, this);

        for (Class<?> componentType : componentTypes) {

            if (isAbstract(componentType.getModifiers()) || componentType.isInterface()) {
                throw new IllegalContractException("component cannot be an interface or an abstract class [%s]", componentType);
            }

            ComponentScopeServiceDecorator scopeService;

            Constructor<?> constructor = getPrimaryConstructor(componentType); // FIXME

            if (advisement.isProxy(componentType)) {

                Enhancer enhancer = new Enhancer();

                enhancer.setUseFactory(false);
                enhancer.setSuperclass(componentType);
                enhancer.setInterceptDuringConstruction(true);
                enhancer.setExecutionInterceptorCallbackType();
                enhancer.setInterfaces(componentType.getInterfaces());
                enhancer.setNamingPolicy(DefaultNamingPolicy.INSTANCE);
                enhancer.setStrategy(DefaultGeneratorStrategy.INSTANCE);

                Class enhancedComponentType = enhancer.createClass();

                registerStaticCallbacks(enhancedComponentType, advisement.callbacks);

                scopeService = newScope(componentType);

                if (advisement.isNewProxy(componentType)) {
                    scopeService.componentAllocator = newAllocator(advisement, advisement.toNewMethod(constructor), enhancedComponentType);
                } else {
                    scopeService.componentAllocator = newAllocator(enhancedComponentType);
                }

                if (advisement.isInitProxy(componentType)) {
                    scopeService.componentService = newComponentService(advisement, advisement.toInitMethod(constructor), enhancedComponentType, this.environment, this);
                } else {
                    scopeService.componentService = newComponentService(constructor, enhancedComponentType, this.environment, this);
                }

            } else if (advisement.isAspect(componentType)) {
                scopeService                    = newAspectScope(componentType);
                scopeService.componentAllocator = newAllocator(componentType);
                scopeService.componentService   = newComponentService(constructor, componentType, this.environment, this);
            } else {

                scopeService = newScope(componentType);

                if (advisement.isNewProxy(componentType)) {
                    scopeService.componentAllocator = newAllocator(advisement, advisement.toNewMethod(constructor), componentType);
                } else {
                    scopeService.componentAllocator = newAllocator(componentType);
                }

                if (advisement.isInitProxy(componentType)) {
                    scopeService.componentService = newComponentService(advisement, advisement.toInitMethod(constructor), componentType, this.environment, this);
                } else {
                    scopeService.componentService = newComponentService(constructor, componentType, this.environment, this);
                }
            }

            if (advisement.isWireProxy(componentType)) {
                scopeService.componentAutowiring = newComponentWiring(componentType, advisement, advisement.getAutowiring(componentType), this);
            } else {
                scopeService.componentAutowiring = newComponentWiring(componentType, this);
            }

            if (isAspectSpecImpl(componentType)) {
                registerTypes(scopeService, componentType);
            } else {
                registerTypes(scopeService, componentType);
                registerActor(componentType, scopeService);
                registerFactories(scopeService, componentType);
            }
        }

        advisement.close();

    }

    @Override
    public Iterator<Class<?>> iterator() {
        return byTypeStore.keySet().iterator();
    }

    @Override
    public ComponentFactory swap(Object...swap) {

        Map<Class<?>, Object> override = classifyByType(swap);

        ComponentFactoryImpl components = new ComponentFactoryImpl(advisement, environment);

        components.byTypeStore.put(ComponentFactory.class, instance(components));

        for (Class<?> componentType : override.keySet()) {
            components.byTypeStore.put(componentType, instance(override.get(componentType)));
        }

        return components;

    }

    public Map<Joint, AutowiringHook> autowiringBy(String pointcut, Object...args) {

        for (int i = 0; i < args.length; i++) {

            Object arg = args[i];

            if (arg instanceof Class) {
                args[i] = ((Class<?>) arg).getCanonicalName();
            }
        }

        try {
            String expression = format(pointcut, args);

            Pointcut matcher = parse(expression);

            if (matcher.isWithinGuard()) {
                throw new IllegalContractException("autowireBy(%s) - 'within' guards are not allowed for autowiring pointcut expression", expression);
            }

            Map<Joint, AutowiringHook> autowiring = new HashMap<>();

            if (matcher.isExecution()) {
                for (Method by : filterMethods(this, matcher)) {
                    autowiring.put(new MethodJoint(by), newAutowiringHook(by, this));
                }
            }

            if (matcher.isAutowire()) {
                for (Field at : filterFields(this, matcher)) {
                    autowiring.put(new FieldJoint(at), newAutowiringHook(advisement, at, this));
                }
            }

            return autowiring;
        } catch (PointcutExpressionSyntaxError cause) {
            throw new IllegalContractException("autowireBy(%s) %s at position %d", cause.expression, cause.message, cause.position);
        }
    }

    @Override
    public boolean hasComponent(Class<?> expectedType) {
        return byTypeStore.containsKey(expectedType);
    }

    @Override
    public boolean hasSubtypeComponents(Class<?> expectedSuperType) {
        return byTypeStore.keySet()
                .stream()
                .anyMatch(expectedSuperType::isAssignableFrom);
    }

    @Override
    public <T> ComponentProvider<T> getProvider(Class<T> expectedType) {
        return (() -> get(expectedType));
    }

    @Override
    public Iterable<Object> listByAnnotation(Class<? extends Annotation> expectedAnnotationType) {
        return byTypeStore.keySet()
                .stream()
                .filter(isAnnotationPresent(expectedAnnotationType))
                .map(byTypeStore::get)
                .map(ComponentProvider::getComponent)
                .collect(toList());
    }

    @Override
    public <T> T get(Class<T> expectedType) {

        ComponentScope componentScope = byTypeStore.get(expectedType);

        if (isNull(componentScope)) {
            throw new NullPointerException(format("component [%s] unregistered", expectedType));
        }

        Object component = componentScope.getComponent();

        if (isNull(component)) {
            throw new NullPointerException(format("componentScope of [%s] return null", expectedType));
        }

        try {
            return expectedType.cast(component);
        } catch (ClassCastException cause) {
            throw new IllegalContractException(format("componentScope of [%s] return [%s]", expectedType, component.getClass()));
        }
    }

    @Override
    public Object[] listAll(Class<?>[] expectedTypes) {

        int length = expectedTypes.length;

        Object[] components = new Object[length];

        for (int i = 0; i < length; i++) {
            components[i] = get(expectedTypes[i]);
        }

        return components;

    }

    @Override
    public <T> Stream<T> streamAllSubtypes(Class<T> expectedSuperType) {
        return byTypeStore.keySet()
                .stream()
                .filter(expectedSuperType::isAssignableFrom)
                .map(byTypeStore::get)
                .collect(toSet())
                .stream()
                .map(ComponentScope::getComponent)
                .map(expectedSuperType::cast);
    }

    @Override
    public void onLoad() {

        for (ComponentScope scope : byTypeStore.values()) {
            scope.onLoad();
        }

        for (ComponentScope scope : byTypeStore.values()) {
            scope.afterLoad(this);
        }

    }

    @Override
    public void onClose() {
        for (LifecycleListener scope : byTypeStore.values()) {
            scope.onClose();
        }
    }

    @Override
    public void close() {
        this.onClose();
    }

    private void registerFactories(ComponentScope componentScope, Class<?> componentType) {
        for (Method method : listVerifiedFactories(componentType)) {
            registerFactoryMethod(componentScope, method);
        }
    }

    private void registerTypes(ComponentScope componentScope, Class<?> componentType) {
        register(componentScope, componentType);

        if (!isAspectSpecImpl(componentType)) {
            for (Class<?> api : listInterfaces(componentType)) {
                register(componentScope, api);
            }
        }
    }

    private void registerFactoryMethod(ComponentScope componentScope, Method method) {

        ComponentScope factoryScope = getFactoryScope(componentScope, method);

        Class<?> componentType = method.getReturnType();

        register(factoryScope, componentType);

        Class<?> componentSuperType = componentType.getSuperclass();

        register(factoryScope, componentSuperType);

        String mainAlias = method.getName();

        for (Class<?> api : listInterfaces(componentType)) {
            register(factoryScope, api);
        }
    }

    private void register(ComponentScope scope, Class<?> componentType) {
        if (byTypeStore.containsKey(componentType) && !override) {
            throw new IllegalContractException("duplicated component type [%s]", componentType);
        } else {
            byTypeStore.put(componentType, scope);
        }
    }

    private ComponentScope getFactoryScope(ComponentScope componentScope, Method method) {
        if (isStatic(method.getModifiers())) {
            return newStaticFactoryScope(method, environment, this);
        } else {
            return newInstanceFactoryScope(componentScope, method, environment, this);
        }
    }

}
