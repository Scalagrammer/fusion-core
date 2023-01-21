package scg.fusion.remoting.rmi;

import java.rmi.Remote;

import static scg.fusion.cglib.proxy.Enhancer.isEnhanced;

final class RMIUtils {

    private RMIUtils() {
        throw new UnsupportedOperationException();
    }

    static String getRemoteName(Class<? extends Remote> componentType) {
        return (isEnhanced(componentType) ? componentType.getSuperclass() : componentType).getCanonicalName();
    }
}

