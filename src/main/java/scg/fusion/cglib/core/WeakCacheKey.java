package scg.fusion.cglib.core;

import java.lang.ref.WeakReference;

public class WeakCacheKey<T> extends WeakReference<T> {
    private final int hash;

    public WeakCacheKey(T referent) {
        super(referent);
        this.hash = referent.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WeakCacheKey)) {
            return false;
        }
        Object ours = get();
        Object theirs = ((WeakCacheKey) obj).get();
        return ours != null && ours.equals(theirs);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        T t = get();
        return t == null ? "Clean WeakIdentityKey, hash: " + hash : t.toString();
    }
}

