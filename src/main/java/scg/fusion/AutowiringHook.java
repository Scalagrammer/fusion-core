package scg.fusion;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@FunctionalInterface
public interface AutowiringHook extends Iterable<Object> {

    <R> R autowireWith(ComponentFactory components);

    @Override
    default Iterator<Object> iterator() {
        return new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Object next() {
                return autowire();
            }
        };
    }

    default <R> R autowire() {
        return autowireWith(null);
    }

    default <R> R autowireAs(Class<R> expectedResultType) {
        return expectedResultType.cast(autowireWith(null));
    }

    default Runnable toRunnable() {
        return this::autowire;
    }

    default Runnable toRunnable(BiConsumer<Throwable, Object> callback) {
        return () -> {
            try {
                callback.accept((null), autowire());
            } catch (Throwable cause) {
                callback.accept(cause, (null));
            }
        };
    }

    default Supplier<Object> toSupplier() {
        return (() -> autowire());
    }

    default <R> Supplier<R> toSupplierAs(Class<R> expectedReturnType) {
        return (() -> autowireAs(expectedReturnType));
    }

    default Callable<Object> toCallable() {
        return (() -> autowire());
    }

    default <R> Callable<R> toCallableAs(Class<R> expectedReturnType) {
        return (() -> autowireAs(expectedReturnType));
    }

    default Stream<Object> toStream() {
        return Stream.generate(toSupplier());
    }

    default <R> Stream<R> toStreamAs(Class<R> expectedReturnType) {
        return Stream.generate(toSupplierAs(expectedReturnType));
    }

}
