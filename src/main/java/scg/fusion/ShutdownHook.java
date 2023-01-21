package scg.fusion;

import scg.fusion.annotation.MessageListener;
import scg.fusion.messaging.Message;
import scg.fusion.messaging.ShutdownSignal;

import java.util.concurrent.CountDownLatch;

@FunctionalInterface
public interface ShutdownHook {
    void shutdown();
}

class ShutdownHookImpl implements ShutdownHook {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void shutdown() {
        this.latch.countDown();
    }

    void awaitSignal() throws InterruptedException {
        this.latch.await();
    }

    @MessageListener
    void onShutdownMessage(Message<ShutdownSignal> message) {
        this.latch.countDown();
    }

}