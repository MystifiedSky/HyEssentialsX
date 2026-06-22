package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public final class Log {
    private static HytaleLogger logger;

    private Log() {}

    public static void init(@Nonnull HytaleLogger log) {
        logger = log;
    }

    public static void info(@Nonnull String msg) {
        if (logger != null) logger.at(Level.INFO).log(msg);
    }

    public static void warn(@Nonnull String msg) {
        if (logger != null) logger.at(Level.WARNING).log(msg);
    }

    public static void error(@Nonnull String msg) {
        if (logger != null) logger.at(Level.SEVERE).log(msg);
    }

    public static void error(@Nonnull String msg, @Nonnull Throwable t) {
        if (logger != null) logger.at(Level.SEVERE).withCause(t).log(msg);
    }
}
