package scg.fusion;

import scg.fusion.messaging.MessageBroker;
import scg.fusion.messaging.MessagePublisher;

import java.util.*;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.getenv;
import static scg.fusion.ComponentScopeServiceDecorator.instance;
import static scg.fusion.Utils.*;
import static scg.fusion.messaging.StartupSignal.STARTUP_SIGNAL;

public abstract class Application implements ComponentDiscoveryService, ApplicationStarter {

    public static ComponentFactory upload(ComponentDiscoveryService discoveryService, String...args) throws Throwable {

        long start = currentTimeMillis();

        HashMap<String, String> env = new HashMap<>(getenv());

        for (int i = 0; i < args.length; i++) {

            String arg = args[i];

            String key = "arg[" + i + "]";

            env.put(key, arg);

        }

        Set<Class<?>> componentTypes = new HashSet<>();

        discoveryService.discover(componentTypes::add);

        componentTypes.add(ShutdownHookImpl.class);

        ComponentFactoryImpl context = new ComponentFactoryImpl(componentTypes, env::get);

        { // register embedded components
            //
            ComponentScope contextInstance     = instance(context);
            ComponentScope environmentInstance = instance(context.environment);
            //
            context.byTypeStore.put(MessageBroker.class,       contextInstance);
            context.byTypeStore.put(MessagePublisher.class,    contextInstance);
            context.byTypeStore.put(ComponentFactory.class,    contextInstance);
            context.byTypeStore.put(Environment.class,     environmentInstance);
        }

        context.onLoad();

        context.publish(STARTUP_SIGNAL);

        return context;

    }

    public static final void main(String... args) throws Throwable {

        long start = currentTimeMillis();

        Application application = instantiateApplication();

        if (onTheFlyDumpCodeEnabled(application)) {
            System.setProperty(ON_THE_FLY_DUMP_CODE_LOCATION_PROPERTY_NAME, getOnTheFlyDumpCodePath(application));
        }

        HashMap<String, String> env = new HashMap<>(getenv());

        Set<Class<?>> componentTypes = new HashSet<>();

        application.discover(componentTypes::add);

        componentTypes.add(ShutdownHookImpl.class);

        for (int i = 0; i < args.length; i++) {

            String arg = args[i];

            String key = "arg[" + i + "]";

            env.put(key, arg);

        }

        for (Properties properties : listProperties(application)) {
            for (String propertyName : properties.stringPropertyNames()) {
                env.put(propertyName, properties.getProperty(propertyName));
            }
        }

        try (ComponentFactoryImpl context = new ComponentFactoryImpl(componentTypes, env::get)) {

            { // register embedded components
                //
                ComponentScope contextInstance     = instance(context);
                ComponentScope environmentInstance = instance(context.environment);
                //
                context.byTypeStore.put(MessageBroker.class,       contextInstance);
                context.byTypeStore.put(MessagePublisher.class,    contextInstance);
                context.byTypeStore.put(ComponentFactory.class,    contextInstance);
                context.byTypeStore.put(Environment.class,      environmentInstance);
            }

            context.onLoad();

            context.publish(STARTUP_SIGNAL);

            application.startup(context);

            if (awaitShutdownEnabled(application)) {
                context.get(ShutdownHookImpl.class).awaitSignal();
            }
        }
    }

    @Override
    public void startup(ComponentFactory components) throws Throwable {
    }

    private static void enableDebug(String path) {
        System.setProperty("fusion.onthefly.debugLocation", path);
    }

}
