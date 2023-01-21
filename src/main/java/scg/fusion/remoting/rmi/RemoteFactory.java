package scg.fusion.remoting.rmi;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import static scg.fusion.remoting.rmi.RMIUtils.getRemoteName;

public interface RemoteFactory {

    <R extends Remote> R lookup(Class<R> componentType) throws NotBoundException, RemoteException;

    static RemoteFactory remoteRegistry() throws RemoteException {
        return remoteRegistry(Registry.REGISTRY_PORT);
    }

    static RemoteFactory remoteRegistry(String host) throws RemoteException {
        return remoteRegistry(host, Registry.REGISTRY_PORT);
    }

    static RemoteFactory remoteRegistry(int port) throws RemoteException {
        return remoteRegistry(null, port);
    }

    static RemoteFactory remoteRegistry(String host, int port) throws RemoteException {
        return new RemoteFactory() {

            final Registry registry = LocateRegistry.getRegistry(host, port);

            @Override
            public <R extends Remote> R lookup(Class<R> componentType) throws NotBoundException, RemoteException {
                return (R) registry.lookup(getRemoteName(componentType));
            }
        };
    }
}
