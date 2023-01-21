package scg.fusion;

import java.util.Collections;
import java.util.Iterator;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.singleton;
import static java.util.Objects.isNull;

public interface Maybe<T> extends Iterable<T> {

    default boolean isEmpty() {
        return true;
    }

    default boolean isNotEmpty() {
        return !isEmpty();
    }

    static <T> Maybe<T> empty() {
        return Collections::emptyIterator;
    }

    static <T> Maybe<T> pure(T value) {
        return new Maybe<T>() {

            @Override
            public boolean isEmpty() {
                return isNull(value);
            }

            @Override
            public Iterator<T> iterator() {
                return isEmpty() ? emptyIterator() : singleton(value).iterator();
            }
        };
    }
}
