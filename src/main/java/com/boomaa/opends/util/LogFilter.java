package com.boomaa.opends.util;

public final class LogFilter {
    private static volatile boolean infoEnabled = false;
    private static volatile boolean warningEnabled = false;
    private static volatile boolean errorEnabled = true;

    private LogFilter() {
    }

    public static boolean allows(EventSeverity level) {
        if (level == null) {
            return true;
        }
        switch (level) {
            case INFO:
                return infoEnabled;
            case WARNING:
                return warningEnabled;
            case ERROR:
                return errorEnabled;
            default:
                return true;
        }
    }

    public static boolean isInfoEnabled() {
        return infoEnabled;
    }

    public static boolean isWarningEnabled() {
        return warningEnabled;
    }

    public static boolean isErrorEnabled() {
        return errorEnabled;
    }

    public static void setInfoEnabled(boolean enabled) {
        infoEnabled = enabled;
    }

    public static void setWarningEnabled(boolean enabled) {
        warningEnabled = enabled;
    }

    public static void setErrorEnabled(boolean enabled) {
        errorEnabled = enabled;
    }
}
