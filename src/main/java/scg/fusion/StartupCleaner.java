package scg.fusion;

abstract class StartupCleaner implements AutoCloseable {
    @Override
    public void close() {
//        OnTheFlyFactory.serviceCache = null;
//        OnTheFlyFactory.invokerCache = null;
//        Utils.classloaderClass = null;
//        Utils.appClass = null;
    }
}
