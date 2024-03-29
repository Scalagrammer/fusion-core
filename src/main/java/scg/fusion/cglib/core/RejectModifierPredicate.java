package scg.fusion.cglib.core;

import java.lang.reflect.Member;

public class RejectModifierPredicate implements Predicate {
    private int rejectMask;

    public RejectModifierPredicate(int rejectMask) {
        this.rejectMask = rejectMask;
    }

    public boolean evaluate(Object arg) {
        return (((Member)arg).getModifiers() & rejectMask) == 0;
    }
}
