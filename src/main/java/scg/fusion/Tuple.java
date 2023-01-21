package scg.fusion;

import java.util.function.Function;

public final class Tuple<A, B> {

    final A _1;
    final B _2;

    Tuple(A a, B b) {
        _1 = a;
        _2 = b;
    }

    A _1() {
        return _1;
    }

    B _2() {
        return _2;
    }

    static <A, B> Function<A, Tuple<A, B>> tupled(B b) {
        return a -> new Tuple<>(a, b);
    }

}
