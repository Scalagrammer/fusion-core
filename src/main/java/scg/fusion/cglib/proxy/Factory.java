package scg.fusion.cglib.proxy;

public interface Factory {
    /**
     * Creates new instance of the same type, using the no-arg constructor.
     * The class of this object must have been created using a single Callback type.
     * If multiple callbacks are required an exception will be thrown.
     * @param callback the new interceptor to use
     * @return new instance of the same type
     */
    Object newInstance(Callback callback);

    /**
     * Creates new instance of the same type, using the no-arg constructor.
     * @param callbacks the new callbacks(s) to use
     * @return new instance of the same type
     */
    Object newInstance(Callback[] callbacks);

    /**
     * Creates a new instance of the same type, using the constructor
     * matching the given signature.
     * @param types the constructor argument types
     * @param args the constructor arguments
     * @param callbacks the new interceptor(s) to use
     * @return new instance of the same type
     */
    Object newInstance(Class[] types, Object[] args, Callback[] callbacks);

    /**
     * Return the <code>Callback</code> implementation at the specified index.
     * @param index the callback index
     * @return the callback implementation
     */
    Callback getCallback(int index);

    /**
     * Set the callback for this object for the given type.
     * @param index the callback index to replace
     * @param callback the new callback
     */
    void setCallback(int index, Callback callback);

    /**
     * Replace all of the callbacks for this object at once.
     * @param callbacks the new callbacks(s) to use
     */
    void setCallbacks(Callback[] callbacks);

    /**
     * Get the current set of callbacks for ths object.
     * @return a new array instance
     */
    Callback[] getCallbacks();
}

