package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.server.core.HytaleServer;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class VaultUnlockedIntegration {

    private static final String VAULT_PLUGIN_ID = "TheNewEconomy:VaultUnlocked";
    private static final String VAULT_MANAGER_CLASS = "net.cfh.vault.VaultUnlockedServicesManager";
    private static final String ECONOMY_INTERFACE_CLASS = "net.milkbowl.vault2.economy.Economy";
    private static final String ECONOMY_RESPONSE_CLASS = "net.milkbowl.vault2.economy.EconomyResponse";
    private static final String ECONOMY_RESPONSE_TYPE_CLASS = "net.milkbowl.vault2.economy.EconomyResponse$ResponseType";
    private static final String PROVIDER_NAME = "HyEssentialsX";
    private static final String DEFAULT_CURRENCY = "default";
    private static final String DEFAULT_CURRENCY_SINGULAR = "Coin";
    private static final String DEFAULT_CURRENCY_PLURAL = "Coins";
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final Object LOCK = new Object();

    private static volatile EconomyManager economyManager;
    private static volatile StorageManager storageManager;

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean registered;
    private static volatile Object manager;
    private static volatile Method registerMethod;
    private static volatile Method unregisterMethod;
    private static volatile Method providerNamesMethod;
    private static volatile Object economyProxy;
    private static volatile Constructor<?> responseCtor;
    private static volatile Class<?> responseTypeClass;

    private VaultUnlockedIntegration() {
    }

    public static void configure(@Nonnull EconomyManager economy, @Nonnull StorageManager storage) {
        economyManager = economy;
        storageManager = storage;
    }

    public static void refresh() {
        EconomyManager economy = economyManager;
        StorageManager storage = storageManager;
        if (economy == null || storage == null) {
            return;
        }
        if (!economy.isEnabled() || !isVaultUnlockedInstalled()) {
            unregister();
            return;
        }
        ensureInitialized();
        if (!available) {
            return;
        }
        synchronized (LOCK) {
            if (!available) {
                return;
            }
            if (economyProxy == null) {
                economyProxy = buildProxy();
            }
            if (economyProxy == null) {
                return;
            }
            if (isRegistered()) {
                registered = true;
                return;
            }
            try {
                registerMethod.invoke(manager, economyProxy);
                registered = true;
                Log.info("[HyEssentialsX] VaultUnlocked economy provider registered.");
            } catch (Throwable t) {
                Log.warn("[HyEssentialsX] Failed to register VaultUnlocked economy provider: " + t.getMessage());
            }
        }
    }

    public static void unregister() {
        Object localManager = manager;
        Object localProxy = economyProxy;
        Method localUnregister = unregisterMethod;
        if (localManager == null || localProxy == null || localUnregister == null) {
            return;
        }
        if (!registered && !isRegistered()) {
            return;
        }
        try {
            localUnregister.invoke(localManager, localProxy);
            registered = false;
            Log.info("[HyEssentialsX] VaultUnlocked economy provider unregistered.");
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to unregister VaultUnlocked economy provider: " + t.getMessage());
        }
    }

    private static boolean isVaultUnlockedInstalled() {
        try {
            HytaleServer server = HytaleServer.get();
            if (server == null || server.getPluginManager() == null) {
                return false;
            }
            return server.getPluginManager().hasPlugin(
                    PluginIdentifier.fromString(VAULT_PLUGIN_ID),
                    SemverRange.WILDCARD
            );
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            initialized = true;
            try {
                ClassLoader loader = VaultUnlockedIntegration.class.getClassLoader();
                Class<?> managerClass = Class.forName(VAULT_MANAGER_CLASS, true, loader);
                Method getMethod = managerClass.getMethod("get");
                manager = getMethod.invoke(null);
                if (manager == null) {
                    available = false;
                    return;
                }
                Class<?> economyInterface = Class.forName(ECONOMY_INTERFACE_CLASS, true, loader);
                registerMethod = managerClass.getMethod("economy", economyInterface);
                unregisterMethod = managerClass.getMethod("unregister", economyInterface);
                providerNamesMethod = managerClass.getMethod("economyProviderNames");
                available = registerMethod != null && unregisterMethod != null;
            } catch (Throwable t) {
                available = false;
            }
        }
    }

    @Nullable
    private static Object buildProxy() {
        try {
            ClassLoader loader = VaultUnlockedIntegration.class.getClassLoader();
            Class<?> economyInterface = Class.forName(ECONOMY_INTERFACE_CLASS, true, loader);
            return Proxy.newProxyInstance(
                    economyInterface.getClassLoader(),
                    new Class<?>[]{economyInterface},
                    new EconomyHandler()
            );
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to create VaultUnlocked economy provider: " + t.getMessage());
            return null;
        }
    }

    private static boolean isRegistered() {
        if (manager == null || providerNamesMethod == null) {
            return false;
        }
        try {
            Object names = providerNamesMethod.invoke(manager);
            if (names instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    if (entry != null && PROVIDER_NAME.equalsIgnoreCase(entry.toString())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Nullable
    private static Object response(@Nonnull BigDecimal amount,
                                   @Nonnull BigDecimal balance,
                                   @Nonnull String typeName,
                                   @Nonnull String message) {
        if (!ensureResponseCtor()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Object type = Enum.valueOf((Class<Enum>) responseTypeClass, typeName);
            return responseCtor.newInstance(amount, balance, type, message);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean ensureResponseCtor() {
        if (responseCtor != null && responseTypeClass != null) {
            return true;
        }
        synchronized (LOCK) {
            if (responseCtor != null && responseTypeClass != null) {
                return true;
            }
            try {
                ClassLoader loader = manager != null ? manager.getClass().getClassLoader() : VaultUnlockedIntegration.class.getClassLoader();
                Class<?> responseClass = Class.forName(ECONOMY_RESPONSE_CLASS, true, loader);
                responseTypeClass = Class.forName(ECONOMY_RESPONSE_TYPE_CLASS, true, loader);
                responseCtor = responseClass.getConstructor(BigDecimal.class, BigDecimal.class, responseTypeClass, String.class);
                return true;
            } catch (Throwable ignored) {
                responseCtor = null;
                responseTypeClass = null;
                return false;
            }
        }
    }

    private static final class EconomyHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            String name = method.getName();
            return switch (name) {
                case "isEnabled" -> isEconomyEnabled();
                case "getName" -> PROVIDER_NAME;
                case "hasSharedAccountSupport" -> false;
                case "hasMultiCurrencySupport" -> false;
                case "fractionalDigits" -> 0;
                case "format" -> formatAmount(extractAmount(args));
                case "hasCurrency" -> isDefaultCurrency(extractStringArg(args, 0));
                case "getDefaultCurrency" -> DEFAULT_CURRENCY;
                case "defaultCurrencyNamePlural" -> DEFAULT_CURRENCY_PLURAL;
                case "defaultCurrencyNameSingular" -> DEFAULT_CURRENCY_SINGULAR;
                case "currencies" -> List.of(DEFAULT_CURRENCY);
                case "createAccount" -> createAccount(args);
                case "getUUIDNameMap" -> getUuidNameMap();
                case "getAccountName" -> getAccountName(extractUuid(args));
                case "hasAccount" -> hasAccount(extractUuid(args));
                case "renameAccount" -> renameAccount(args);
                case "deleteAccount" -> false;
                case "accountSupportsCurrency" -> isDefaultCurrency(extractCurrencyArg(args, 2));
                case "getBalance", "balance" -> getBalance(args);
                case "has" -> hasBalance(args);
                case "set" -> setBalance(args);
                case "canWithdraw", "canDeposit" -> notImplementedResponse();
                case "withdraw" -> withdraw(args);
                case "deposit" -> deposit(args);
                case "createSharedAccount" -> false;
                case "accountsOwnedBy", "accountsMemberOf", "accountsAccessTo",
                        "accountsWithOwnerOf", "accountsWithMembershipTo", "accountsWithAccessTo" -> Collections.emptyList();
                case "isAccountOwner", "setOwner", "isAccountMember", "addAccountMember",
                        "removeAccountMember", "hasAccountPermission", "updateAccountPermission" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> PROVIDER_NAME + " VaultUnlocked Economy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                default -> null;
            };
        }
    }

    private static boolean isEconomyEnabled() {
        EconomyManager economy = economyManager;
        return economy != null && economy.isEnabled();
    }

    private static boolean isDefaultCurrency(@Nullable String currency) {
        if (currency == null) {
            return true;
        }
        return DEFAULT_CURRENCY.equalsIgnoreCase(currency);
    }

    @Nullable
    private static UUID extractUuid(@Nullable Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    @Nullable
    private static BigDecimal extractAmount(@Nullable Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof BigDecimal value) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String extractStringArg(@Nullable Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return null;
        }
        Object value = args[index];
        return value instanceof String ? (String) value : null;
    }

    @Nullable
    private static String extractCurrencyArg(@Nullable Object[] args, int index) {
        return extractStringArg(args, index);
    }

    @Nonnull
    private static String formatAmount(@Nullable BigDecimal amount) {
        EconomyManager economy = economyManager;
        String symbol = economy != null ? economy.getCurrencySymbol() : "";
        long raw = toLong(amount);
        if (raw < 0L) {
            return "-" + symbol + Math.abs(raw);
        }
        return symbol + raw;
    }

    @Nonnull
    private static Map<UUID, String> getUuidNameMap() {
        StorageManager storage = storageManager;
        if (storage == null) {
            return Map.of();
        }
        Map<UUID, String> map = new HashMap<>();
        for (UUID uuid : storage.listPlayerIds()) {
            PlayerDataModel data = storage.getPlayerData(uuid);
            String name = data.getLastKnownName();
            if (name != null && !name.isBlank()) {
                map.put(uuid, name);
            }
        }
        return map;
    }

    private static Object getAccountName(@Nullable UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        StorageManager storage = storageManager;
        if (storage == null) {
            return Optional.empty();
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private static boolean createAccount(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        if (uuid == null) {
            return false;
        }
        StorageManager storage = storageManager;
        if (storage == null) {
            return false;
        }
        String name = null;
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String value) {
                    name = value;
                }
            }
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        if (name != null && !name.isBlank()) {
            storage.updatePlayerName(uuid, name);
        } else {
            storage.savePlayerDataAsync(uuid, data);
        }
        return true;
    }

    private static boolean hasAccount(@Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        StorageManager storage = storageManager;
        if (storage == null) {
            return false;
        }
        Set<UUID> ids = storage.listPlayerIds();
        return ids.contains(uuid);
    }

    private static boolean renameAccount(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        if (uuid == null) {
            return false;
        }
        String name = null;
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String value) {
                    name = value;
                }
            }
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        StorageManager storage = storageManager;
        if (storage == null) {
            return false;
        }
        storage.updatePlayerName(uuid, name);
        return true;
    }

    private static Object getBalance(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        EconomyManager economy = economyManager;
        if (uuid == null || economy == null || !economy.isEnabled()) {
            return BigDecimal.ZERO;
        }
        if (args != null && args.length >= 4 && args[3] instanceof String currency) {
            if (!isDefaultCurrency(currency)) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.valueOf(economy.getBalance(uuid));
    }

    private static Object hasBalance(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        if (uuid == null) {
            return false;
        }
        EconomyManager economy = economyManager;
        if (economy == null || !economy.isEnabled()) {
            return false;
        }
        if (!currencyAllowed(args)) {
            return false;
        }
        long amount = toPositiveLong(extractAmount(args));
        if (amount <= 0L) {
            return true;
        }
        return economy.getBalance(uuid) >= amount;
    }

    private static Object setBalance(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        BigDecimal raw = extractAmount(args);
        EconomyManager economy = economyManager;
        if (uuid == null || economy == null || !economy.isEnabled()) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Economy is disabled.");
        }
        if (!currencyAllowed(args)) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Unsupported currency.");
        }
        long amount = toPositiveLong(raw);
        long balance = economy.setBalance(uuid, amount);
        return response(BigDecimal.valueOf(amount), BigDecimal.valueOf(balance), "SUCCESS", "");
    }

    private static Object withdraw(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        EconomyManager economy = economyManager;
        if (uuid == null || economy == null || !economy.isEnabled()) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Economy is disabled.");
        }
        if (!currencyAllowed(args)) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Unsupported currency.");
        }
        long amount = toPositiveLong(extractAmount(args));
        if (amount <= 0L) {
            long balance = economy.getBalance(uuid);
            return response(BigDecimal.ZERO, BigDecimal.valueOf(balance), "FAILURE", "Invalid amount.");
        }
        boolean success = economy.withdraw(uuid, amount);
        long balance = economy.getBalance(uuid);
        return response(BigDecimal.valueOf(amount), BigDecimal.valueOf(balance), success ? "SUCCESS" : "FAILURE",
                success ? "" : "Insufficient funds.");
    }

    private static Object deposit(@Nullable Object[] args) {
        UUID uuid = extractUuid(args);
        EconomyManager economy = economyManager;
        if (uuid == null || economy == null || !economy.isEnabled()) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Economy is disabled.");
        }
        if (!currencyAllowed(args)) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Unsupported currency.");
        }
        long amount = toPositiveLong(extractAmount(args));
        if (amount <= 0L) {
            long balance = economy.getBalance(uuid);
            return response(BigDecimal.ZERO, BigDecimal.valueOf(balance), "FAILURE", "Invalid amount.");
        }
        long balance = economy.deposit(uuid, amount);
        return response(BigDecimal.valueOf(amount), BigDecimal.valueOf(balance), "SUCCESS", "");
    }

    private static Object notImplementedResponse() {
        return response(BigDecimal.ZERO, BigDecimal.ZERO, "NOT_IMPLEMENTED", "Not implemented.");
    }

    private static boolean currencyAllowed(@Nullable Object[] args) {
        if (args == null) {
            return true;
        }
        if (args.length >= 5 && args[3] instanceof String currency) {
            return isDefaultCurrency(currency);
        }
        return true;
    }

    private static long toPositiveLong(@Nullable BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0L;
        }
        long value = toLong(amount);
        return Math.max(0L, value);
    }

    private static long toLong(@Nullable BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        BigDecimal scaled = amount.setScale(0, RoundingMode.DOWN);
        if (scaled.compareTo(LONG_MAX) > 0) {
            return Long.MAX_VALUE;
        }
        if (scaled.compareTo(LONG_MIN) < 0) {
            return Long.MIN_VALUE;
        }
        return scaled.longValue();
    }

    @Nullable
    private static Object defaultValue(@Nonnull Class<?> type) {
        if (type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == String.class) {
            return "";
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ZERO;
        }
        if (Optional.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        if (Collection.class.isAssignableFrom(type)) {
            if (Set.class.isAssignableFrom(type)) {
                return Collections.emptySet();
            }
            return Collections.emptyList();
        }
        if (Map.class.isAssignableFrom(type)) {
            return Collections.emptyMap();
        }
        if (CompletableFuture.class.isAssignableFrom(type)) {
            return CompletableFuture.completedFuture(null);
        }
        return null;
    }
}
