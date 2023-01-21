package scg.fusion;

public interface ComponentWiring {

    ComponentWiring NO_OP_WIRING = $ -> {};

    void wire(Object component);

}
