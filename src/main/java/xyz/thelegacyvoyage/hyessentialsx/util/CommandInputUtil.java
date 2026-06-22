package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandInputUtil {

    private CommandInputUtil() {}

    @Nonnull
    public static List<String> getArgs(@Nonnull CommandContext context) {
        Object raw = tryFetchRaw(context);
        if (raw == null) {
            raw = tryFetchFromFields(context);
        }
        raw = unwrapRaw(raw);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null) out.add(entry.toString());
            }
            return out;
        }
        if (raw instanceof String[] arr) {
            List<String> out = new ArrayList<>();
            Collections.addAll(out, arr);
            return out;
        }
        if (raw instanceof String s) {
            return splitArgs(s);
        }
        return Collections.emptyList();
    }

    @Nullable
    public static String getArg(@Nonnull CommandContext context, int index) {
        List<String> args = getArgs(context);
        if (index < 0 || index >= args.size()) return null;
        return args.get(index);
    }

    private static Object tryFetchRaw(@Nonnull CommandContext context) {
        String[] candidates = {
                "getArguments",
                "getArgs",
                "getRawArguments",
                "getRawArgs",
                "getInput",
                "getRawInput",
                "getInputString",
                "getOriginalInput",
                "getOriginalMessage",
                "getCommandLine",
                "getCommandString",
                "getRawCommand",
                "getRawCommandString",
                "getMessage",
                "getText",
                "getRawText"
        };

        for (String name : candidates) {
            try {
                Method method = context.getClass().getMethod(name);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(context);
                if (value != null) return value;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Object unwrapRaw(@Nullable Object raw) {
        if (raw == null) return null;
        if (raw instanceof String || raw instanceof List<?> || raw instanceof String[]) return raw;

        String[] methods = {"getArgs", "getArguments", "getRawArgs", "getRaw", "getInput", "getCommandLine", "getTokens"};
        for (String name : methods) {
            try {
                Method method = raw.getClass().getMethod(name);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(raw);
                if (value instanceof String || value instanceof List<?> || value instanceof String[]) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    @Nullable
    private static Object tryFetchFromFields(@Nonnull CommandContext context) {
        Class<?> type = context.getClass();
        while (type != null) {
            for (var field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                if (!(name.contains("arg") || name.contains("input") || name.contains("raw") || name.contains("text"))) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(context);
                    if (value instanceof String || value instanceof List<?> || value instanceof String[]) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    @Nonnull
    private static List<String> splitArgs(@Nonnull String input) {
        String trimmed = input.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isBlank()) return Collections.emptyList();

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());

        if (!tokens.isEmpty()) {
            // Drop command label.
            tokens.remove(0);
        }
        return tokens;
    }
}

