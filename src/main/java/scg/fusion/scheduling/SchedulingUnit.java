package scg.fusion.scheduling;

import scg.fusion.AutowiringHook;
import scg.fusion.ComponentFactory;
import scg.fusion.Joint;
import scg.fusion.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static scg.fusion.Utils.availableThreads;

public final class SchedulingUnit {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(availableThreads);

    private final Map<Joint, AutowiringHook> tasks;

    private final List<ScheduledFuture<?>> runningTasks = new ArrayList<>();

    private SchedulingUnit(ComponentFactory components) {
        this.tasks = components.by("@execution(%s)", ScheduleAtFixedRate.class);
    }

    @Initialize
    private void start() {
        for (Joint key : tasks.keySet()) {

            ScheduleAtFixedRate annotation = key.getAnnotation(ScheduleAtFixedRate.class);

            TimeUnit timeUnit = annotation.unit();
            long period       = annotation.value();
            long initialDelay = annotation.initialDelay();

            runningTasks.add(scheduler.scheduleAtFixedRate(wrap(tasks.get(key).toRunnable()), initialDelay, period, timeUnit));

        }
    }

    @Utilize
    private void stop() {

        for (ScheduledFuture<?> task : runningTasks) {
            task.cancel(true);
        }

        scheduler.shutdown();

    }

    @Factory
    @Prototype
    private static Instant now() {
        return Instant.now();
    }

    private static Runnable wrap(Runnable autowiring) {
        return () -> {
            try {
                autowiring.run();
            } catch (Throwable cause) {
                cause.printStackTrace();
            }
        };
    }

}
