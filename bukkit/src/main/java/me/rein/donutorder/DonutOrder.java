package me.rein.donutorder;

import lombok.Getter;
import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.api.event.CurrencyRegistrationCompleteEvent;
import me.rein.donutorder.api.event.ShopRegistrationCompleteEvent;
import me.rein.donutorder.api.shop.ShopManager;
import me.rein.donutorder.commands.OrderCommand;
import me.rein.donutorder.commons.Commons;
import me.rein.donutorder.commons.manager.*;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.hook.Hook;
import me.rein.donutorder.hook.currency.SuperMobCoinsCurrency;
import me.rein.donutorder.hook.currency.VaultCurrency;
import me.rein.donutorder.hook.prices.ecoenchants.EcoEnchantsHook;
import me.rein.donutorder.hook.shops.donutworth.DonutWorthBridge;
import me.rein.donutorder.hook.shops.economyshop.EconomyShopGUIBridge;
import me.rein.donutorder.hook.shops.excellentshop.ExcellentShopBridge;
import me.rein.donutorder.hook.shops.shopguiplus.ShopGuiPlusBridge;
import me.rein.donutorder.hook.shops.zshop.ZShopBridge;
import me.rein.donutorder.listener.ExpirationOrderEvent;
import me.rein.donutorder.listener.OrderAnnouncementEvent;
import me.rein.donutorder.listener.UnclaimedOrdersCheckEvent;
import me.rein.donutorder.inventory.gui.GUIListener;
import me.rein.donutorder.inventory.gui.GUIManager;
import me.rein.donutorder.inventory.impl.admin.AdminListener;
import me.rein.donutorder.commons.manager.price.PriceManager;
import me.rein.donutorder.commons.papi.PAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import me.rein.donutorder.commons.util.debug.Timer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public final class DonutOrder extends JavaPlugin {

    private static @Getter DonutOrder instance;
    private static GUIManager guiManager;

    final String RESET = "\u001B[0m";
    final String BOLD_WHITE = "\u001B[1;97m"; // Bold White (for the title)
    final String YELLOW = "\u001B[33m";   // Yellow (for version)
    final String GRAY = "\u001B[90m";     // Subtle Gray (for additional info)

    @Override
    public void onEnable() {
        instance = this;
        Commons.load(this);

        /*
         * Some VMs are in turkish for e.g.
         * that means whenever using toLowerCase/toUpperCase/etc.
         * the letters also change, as with i -> ı
         */
        Locale.setDefault(Locale.ENGLISH);

        Bukkit.getConsoleSender().sendMessage("\n\n" + BOLD_WHITE +
                        YELLOW +  "  ___         _         \n" +
                        YELLOW + " / _ \\ _ _ __| |___ _ _ "
                        + BOLD_WHITE + "   DonutOrder " + YELLOW + this.getDescription().getVersion() + "\n" +
                        YELLOW + "| (_) | '_/ _` / -_) '_|"
                        + GRAY + "   Supports Paper & Folia\n" +
                        YELLOW + " \\___/|_| \\__,_\\___|_|" + RESET + "\n"
                );

        Arrays.stream(ConfigSelector.values()).toList()
                .forEach(ConfigSelector::initialize);

        if (!new File(getDataFolder(), "gui/FILLER_ITEMS.txt").exists())
            saveResource("gui/FILLER_ITEMS.txt", false);

        guiManager = new GUIManager();

        List<String> aliases = getConfig().contains("aliases")
                ? getConfig().getStringList("aliases")
                : new ArrayList<>();

        PluginCommand ordercmd = getCommand("order");
        ordercmd.setExecutor(new OrderCommand());
        ordercmd.setTabCompleter(new OrderCommand.OrderCommandTabCompleter());

        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            aliases.forEach(alias -> {
                commandMap.register(alias, "me/rein/donutorder", ordercmd);
            });
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        GUIListener guiListener = new GUIListener(guiManager);
        Bukkit.getPluginManager().registerEvents(guiListener, this);

        Bukkit.getPluginManager().registerEvents(new AdminListener(), this);
        Bukkit.getPluginManager().registerEvents(new UnclaimedOrdersCheckEvent(), this);
        Bukkit.getPluginManager().registerEvents(new OrderAnnouncementEvent(), this);
        Bukkit.getPluginManager().registerEvents(new ExpirationOrderEvent(), this);

        load();
    }

    public void load() {
        Messages.load(ConfigSelector.MESSAGES);

        /*
         * IMPORTANT
         */
        DatabaseManager.getInstance().load();
        ValuesManager.getInstance().load();
        WebhookManager.getInstance().load();
//      TODO  BlacklistManager.getInstance().load();

        PAPI.registerPlaceholders();

        Timer timer = new Timer().start();
        PriceManager.getInstance().load();
        Bukkit.getLogger().info("Prices loaded in " + timer.get() + "ms");
        MaterialManager.getInstance().initializeMaterials(PriceManager.getInstance().getPrices());

        /*
         * HOOKS
         */
        HooksManager.getInstance().load();
        enableHooks();

        /*
         * AFTER
         */
        SoundManager.getInstance().loadSounds();
        ItemManager.getInstance().load();
        OrderManager.getInstance().load();
        OrderAntiDupeManager.getInstance().load();
        PermissionManager.getInstance().load();
    }

    @Override
    public void onDisable() {
        GUIManager.getRecentlyClosedInventories().close();
    }

    public static GUIManager getGUIManager() {
        return guiManager;
    }

    public void enableHooks() {
        /*
         * Shops
         */
        switch (HooksManager.getInstance().getCurrentShop().toLowerCase()) {
            case "economyshopgui":
                if (Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI")
                        || Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI-Premium")) {
                    ShopManager.getInstance().registerShop(new EconomyShopGUIBridge());
                }
                break;
            case "shopgui+":
                if (Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus")) {
                    ShopManager.getInstance().registerShop(new ShopGuiPlusBridge());
                }
                break;
            case "zshop":
                if (Bukkit.getPluginManager().isPluginEnabled("ZShop")) {
                    ShopManager.getInstance().registerShop(new ZShopBridge());
                }
                break;
            case "excellentshop":
                if (Bukkit.getPluginManager().isPluginEnabled("ExcellentShop")) {
                    ShopManager.getInstance().registerShop(new ExcellentShopBridge());
                }
                break;
            case "donutworth":
                if (Bukkit.getPluginManager().isPluginEnabled("DonutWorth")) {
                    ShopManager.getInstance().registerShop(new DonutWorthBridge());
                }
                break;
        }

        Bukkit.getPluginManager().callEvent(new ShopRegistrationCompleteEvent(
                ShopManager.getInstance().getRegisteredShops(),
                ShopManager.getInstance().getActiveShop()
        ));

        ShopManager.getInstance().setActiveShop(HooksManager.getInstance().getCurrentShop());

        ShopManager.getInstance().syncPrices();

        /*
         * Currencies
         */
        switch (HooksManager.getInstance().getCurrentCurrency().toLowerCase()) {
            case "vault":
//                if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                    CurrencyManager.getInstance().registerCurrency(new VaultCurrency());
//                }
                break;
            case "supermobcoins":
                if (Bukkit.getPluginManager().isPluginEnabled("SuperMobCoins")) {
                    CurrencyManager.getInstance().registerCurrency(new SuperMobCoinsCurrency());
                }
                break;
        }

        Bukkit.getPluginManager().callEvent(new CurrencyRegistrationCompleteEvent(
                CurrencyManager.getInstance().getRegisteredCurrencies(),
                CurrencyManager.getInstance().getActiveCurrency()
        ));

        CurrencyManager.getInstance().setActiveCurrency(HooksManager.getInstance().getCurrentCurrency());

        if (CurrencyManager.getInstance().getActiveCurrency() == null) {
            System.out.println("------------");
            System.out.println("------------");
            System.out.println("CURRENCY NOT FOUND");
            System.out.println("------------");
            System.out.println("------------");
        }

        List<Hook> enabledHooks = getHooks();
    }

    private List<Hook> getHooks() {
        Map<String, Class<? extends Hook>> hookList = new HashMap<>() {{
            put("EcoEnchants", EcoEnchantsHook.class);
        }};

        List<Hook> enabledHooks = new ArrayList<>();

        hookList.forEach((pluginName, hookClass) -> {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                Hook hook = null;

                try {
                    hook = hookClass.getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }

                hook.init(this);
                hook.setEnabled(true);
                enabledHooks.add(hook);
            }
        });

        return enabledHooks;
    }
}
