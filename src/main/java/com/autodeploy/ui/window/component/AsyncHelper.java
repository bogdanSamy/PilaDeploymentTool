package com.autodeploy.ui.window.component;

import javafx.concurrent.Task;

/**
 * Helper pentru lansarea task-urilor asincrone pe thread-uri daemon.
 * Elimină pattern-ul repetat: new Thread(task, name); setDaemon(true); start();
 */
public final class AsyncHelper {

    private AsyncHelper() {}

    /**
     * Lansează un Task pe un thread daemon cu numele specificat.
     */
    public static <T> void runDaemon(Task<T> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Lansează un Runnable pe un thread daemon cu numele specificat.
     */
    public static void runDaemon(Runnable runnable, String threadName) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}