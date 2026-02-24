package com.autodeploy.service.utility;

/**
 * Utilitar pentru detecția sistemului de operare.
 * Elimină duplicarea isWindows() din multiple servicii.
 */
public final class OsHelper {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    private OsHelper() {}

    public static boolean isWindows() { return OS_NAME.contains("win"); }
    public static boolean isMac() { return OS_NAME.contains("mac"); }
    public static boolean isLinux() { return OS_NAME.contains("nix") || OS_NAME.contains("nux"); }
}