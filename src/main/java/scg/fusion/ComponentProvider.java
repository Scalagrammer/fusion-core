package scg.fusion;

import java.util.Iterator;

public interface ComponentProvider<T> extends Iterable<T> {

    T getComponent();

    @Override
    default Iterator<T> iterator() {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public T next() {
                return getComponent();
            }
        };
    }

}
