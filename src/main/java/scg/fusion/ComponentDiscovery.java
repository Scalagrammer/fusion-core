package scg.fusion;

public interface ComponentDiscovery {

    default void found(Class<?>...componentTypes) {
        for (Class<?> componentType : componentTypes) {
            this.found(componentType);
        }
    }

    void found(Class<?> componentType);

}
