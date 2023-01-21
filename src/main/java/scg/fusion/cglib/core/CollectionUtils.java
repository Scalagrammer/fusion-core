package scg.fusion.cglib.core;

import java.util.*;

public class CollectionUtils {
    private CollectionUtils() { }

    public static Map bucket(Collection c, Transformer t) {
        Map buckets = new HashMap();
        for (Object value : c) {
            Object key = t.transform(value);
            List bucket = (List) buckets.get(key);
            if (bucket == null) {
                buckets.put(key, bucket = new LinkedList());
            }
            bucket.add(value);
        }
        return buckets;
    }

    public static void reverse(Map source, Map target) {
        for (Object key : source.keySet()) {
            target.put(source.get(key), key);
        }
    }

    public static Collection filter(Collection c, Predicate p) {
        c.removeIf(o -> !p.evaluate(o));
        return c;
    }

    public static List transform(Collection c, Transformer t) {
        List result = new ArrayList(c.size());
        for (Object o : c) {
            result.add(t.transform(o));
        }
        return result;
    }

    public static Map getIndexMap(List list) {
        Map indexes = new HashMap();
        int index = 0;
        for (Object o : list) {
            indexes.put(o, index++);
        }
        return indexes;
    }
}


