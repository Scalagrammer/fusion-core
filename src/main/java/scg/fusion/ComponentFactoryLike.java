package scg.fusion;

public interface ComponentFactoryLike {

    default  <T> T get(Class<T> expectedComponentType) {
        throw new UnsupportedOperationException();
    }

    default  <T> T get(String expectedComponentAlias) {
        throw new UnsupportedOperationException();
    }

}
