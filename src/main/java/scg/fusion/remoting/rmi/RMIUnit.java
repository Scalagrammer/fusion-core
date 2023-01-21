package scg.fusion.remoting.rmi;

import scg.fusion.ComponentFactory;
import scg.fusion.Environment;
import scg.fusion.annotation.Autowired;
import scg.fusion.annotation.Initialize;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.rmi.registry.Registry.REGISTRY_PORT;
import static java.rmi.server.UnicastRemoteObject.exportObject;
import static scg.fusion.remoting.rmi.RMIUtils.getRemoteName;

public final class RMIUnit {

    public static final String RMI_REGISTRY_PORT = "fusion.rmi.registry.port";

    @Autowired
    private ComponentFactory components;
    @Autowired
    private Environment environment;

    private RMIUnit() {
        super();
    }

    @Initialize
    private void export() throws RemoteException {
        if (components.hasSubtypeComponents(Remote.class)) {

            int registryPort = environment.getIntOrDefault(RMI_REGISTRY_PORT, REGISTRY_PORT);

            Registry registry = LocateRegistry.createRegistry(registryPort);

            components.streamAllSubtypes(Remote.class).forEach(export(registry));

        }
    }

    private static Consumer<Remote> export(Registry registry) {
        return api -> {
            try {
                registry.rebind(getRemoteName(api.getClass()), exportObject(api, 0));
            } catch (RemoteException cause) {
                throw new RuntimeException("An error has occurred during RMI endpoint exporting", cause);
            }
        };
    }
}
