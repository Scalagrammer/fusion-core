package scg.fusion;

public interface ComponentScope extends ComponentProvider<Object>, LifecycleListener {
    default void afterLoad(ComponentFactory components) {}
}
