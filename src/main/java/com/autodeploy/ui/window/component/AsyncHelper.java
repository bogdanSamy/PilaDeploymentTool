package com.autodeploy.ui.window.component;

import javafx.concurrent.Task;

public final class AsyncHelper {

    private AsyncHelper() {}

    /**
     * Lansează un Task pe un VIRTUAL thread (Java 21+).
     * Ideal pentru operații I/O-bound: SSH, SFTP, sleep-uri.
     * Virtual thread-ul e automat daemon (nu ține JVM-ul viu).
     */
    public static <T> void runVirtual(Task<T> task, String threadName) {
        Thread.ofVirtual()
                .name(threadName)
                .start(task);
    }

    /**
     * Lansează un Runnable pe un virtual thread.
     */
    public static void runVirtual(Runnable runnable, String threadName) {
        Thread.ofVirtual()
                .name(threadName)
                .start(runnable);
    }

    /**
     * Lansează un Task pe un PLATFORM thread daemon.
     * Folosit DOAR pentru operații CPU-bound (ex: Ant build).
     */
    public static <T> void runDaemon(Task<T> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    public static void runDaemon(Runnable runnable, String threadName) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}