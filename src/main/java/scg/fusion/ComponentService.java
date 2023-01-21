package scg.fusion;

public interface ComponentService {

    void initialize(Object component);

    void resolve(Object component);

    void utilize(Object component);

    default Object nеw() {
        throw new UnsupportedOperationException();
    }

    default Object init(Object component) {
        return this.init(component, (Object[]) null);
    }

    default Object init(Object component, Object[]...args) {
        return component;
    }

}
