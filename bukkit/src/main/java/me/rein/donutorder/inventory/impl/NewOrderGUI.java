package me.rein.donutorder.inventory.impl;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.exception.SignGUIVersionException;
import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.api.event.cancellable.impl.PlayerOrderItemEvent;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.commons.manager.WebhookManager;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.objectholders.TranslationPlaceholder;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.UUID;

public class NewOrderGUI extends InventoryGUI {

    private final long ONE_DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000L;

    private final long itemAmount;
    private final double itemPrice;
    private final boolean comesFromSelectItem;
    private final ItemBuilder selectItemBuilder;

    public NewOrderGUI(
            long amount,
            double price,
            boolean comesFromSelectItem,
            ItemBuilder selectItemBuilder
    ) {
        super(
                ConfigSelector.NEW_ORDER.getConfig().getString("title")
        );

        this.itemAmount = amount;
        this.itemPrice = price;
        this.comesFromSelectItem = comesFromSelectItem;
        this.selectItemBuilder = selectItemBuilder;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        NormalItem cancel = ItemManager.getInstance().getNormalItem(ConfigType.NEW_ORDER,"cancel");
        this.addButton(cancel.getSlot(), new InventoryButton()
                .sound(cancel.getSound())
                .creator(p -> cancel.getItem())
                .consumer(event -> {
                    if (comesFromSelectItem) {
                        DonutOrder.getGUIManager().openGUI(new SelectItemGUI(selectItemBuilder,
                                        Filter.ALL, SortSelectItem.A_Z, "",1)
                                ,player);
                    } else {
                        DonutOrder.getGUIManager().openGUI(new YourOrdersGUI(), player);
                    }
                }));

        ItemBuilder displayBuilder = createDisplayBuilder();
        this.addButton(12, new InventoryButton()
                .creator(p -> displayBuilder)
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new SelectItemGUI(
                                    selectItemBuilder != null ? selectItemBuilder : displayBuilder,
                                    Filter.ALL, SortSelectItem.A_Z, "",1)
                            ,player);
                }));

        NormalItem amount = ItemManager.getInstance().getNormalItem(ConfigType.NEW_ORDER,"amount");
        this.addButton(amount.getSlot(), new InventoryButton()
                .sound(amount.getSound())
                .creator(p -> amount.getItem()
                        .lorePlaceholders(player, "{currentAmount}", NumberUtil.formatNumber(itemAmount)))
                .consumer(event -> {
                    player.closeInventory();

                    try {
                        SignGUI.builder()
                                .setLines(Messages.SIGN_AMOUNT.getLine(0),
                                        Messages.SIGN_AMOUNT.getLine(1),
                                        Messages.SIGN_AMOUNT.getLine(2),
                                        Messages.SIGN_AMOUNT.getLine(3))
                                .setHandler((p, result) -> {
                                    String searchTerm = result.getLine(0).trim();

                                    long newAmount = NumberUtil.parseAmountLong(searchTerm);
                                    if (newAmount <= 0)
                                        newAmount = itemAmount;

                                    long finalNewAmount = newAmount;
                                    OScheduler.sync().runAtEntity(() -> {
                                        DonutOrder.getGUIManager().openGUI(new NewOrderGUI(
                                                finalNewAmount, itemPrice, comesFromSelectItem, selectItemBuilder
                                        ), player);
                                    }, player);

                                    return Collections.emptyList();
                                })
                                .build()
                                .open(player);
                    } catch (SignGUIVersionException e) {}
                }));

        NormalItem price = ItemManager.getInstance().getNormalItem(ConfigType.NEW_ORDER,"price");
        this.addButton(price.getSlot(), new InventoryButton()
                .sound(price.getSound())
                .creator(p -> price.getItem()
                        .lorePlaceholders(player, "{currentPrice}", NumberUtil.formatNumber(itemPrice)))
                .consumer(event -> {
                    try {
                        SignGUI.builder()
                                .setLines(Messages.SIGN_PRICE_PER_ITEM.getLine(0),
                                        Messages.SIGN_PRICE_PER_ITEM.getLine(1),
                                        Messages.SIGN_PRICE_PER_ITEM.getLine(2),
                                        Messages.SIGN_PRICE_PER_ITEM.getLine(3))
                                .setHandler((p, result) -> {
                                    String searchTerm = result.getLine(0).trim();

                                    double newPrice = NumberUtil.parseAmount(searchTerm);
                                    if (newPrice <= 0 || Double.isNaN(newPrice) || Double.isInfinite(newPrice))
                                        newPrice = itemPrice;

                                    double finalNewPrice = newPrice;
                                    OScheduler.sync().runAtEntity(() -> {
                                        DonutOrder.getGUIManager().openGUI(new NewOrderGUI(
                                                itemAmount, finalNewPrice, comesFromSelectItem, selectItemBuilder
                                        ), player);
                                    }, player);

                                    return Collections.emptyList();
                                })
                                .build()
                                .open(player);
                    } catch (SignGUIVersionException e) {}
                }));

        NormalItem confirm = ItemManager.getInstance().getNormalItem(ConfigType.NEW_ORDER,"confirm");
        this.addButton(confirm.getSlot(), new InventoryButton()
                .sound(confirm.getSound())
                .creator(p -> confirm.getItem()
                        .lorePlaceholders(player, "{totalPrice}", NumberUtil.formatNumber(itemPrice * itemAmount)))
                .consumer(event -> {
                    if (itemAmount <= 0 || itemPrice <= 0
                            || Double.isNaN(itemPrice) || Double.isInfinite(itemPrice))
                        return;

                    double amountToPay = itemPrice * itemAmount;

                    if (itemPrice > Double.MAX_VALUE / itemAmount)
                        return;

                    if (amountToPay <= 0 || Double.isNaN(amountToPay) || Double.isInfinite(amountToPay))
                        return;

                    if (ValuesManager.getInstance().getPricingMin() != -1 &&
                            amountToPay < ValuesManager.getInstance().getPricingMin()) {
                        Messages.PRICING_MIN.send(player,
                                "{minPrice}", ValuesManager.getInstance().getPricingMin());
                        return;
                    }

                    if (ValuesManager.getInstance().getPricingMax() != -1 &&
                            amountToPay > ValuesManager.getInstance().getPricingMax()) {
                        Messages.PRICING_MAX.send(player,
                                "{maxPrice}", ValuesManager.getInstance().getPricingMax());
                        return;
                    }

                    if (ValuesManager.getInstance().getPricingPerUnitMin() != -1 &&
                            itemPrice < ValuesManager.getInstance().getPricingPerUnitMin()) {
                        Messages.PRICING_PER_UNIT_MIN.send(player,
                                "{minPrice}", ValuesManager.getInstance().getPricingPerUnitMin());
                        return;
                    }

                    if (ValuesManager.getInstance().getPricingPerUnitMax() != -1 &&
                            itemPrice > ValuesManager.getInstance().getPricingPerUnitMax()) {
                        Messages.PRICING_PER_UNIT_MAX.send(player,
                                "{maxPrice}", ValuesManager.getInstance().getPricingPerUnitMax());
                        return;
                    }

                    double playerBalance = CurrencyManager.getInstance().getBalance(player);

                    if (playerBalance < amountToPay) {
                        Messages.NOT_ENOUGH_MONEY.sendBoth(player);
                        return;
                    }

                    ItemBuilder createdItemBuilder = new ItemBuilder(createDisplayBuilder().getType());

                    Order order = new Order(
                            UUID.randomUUID(),
                            player.getUniqueId(),
                            player.getName(),
                            selectItemBuilder == null ? createdItemBuilder : selectItemBuilder,
                            itemAmount,
                            itemPrice,
                            0,
                            0,
                            ValuesManager.getInstance().getDurationPublic() * ONE_DAY_IN_MILLISECONDS
//                            10 * 1000L
                    );

                    PlayerOrderItemEvent playerOrderItemEvent = new PlayerOrderItemEvent(
                            player,
                            order
                    );

                    Bukkit.getPluginManager().callEvent(playerOrderItemEvent);

                    if (playerOrderItemEvent.isCancelled()) {
                        return;
                    }

                    double playerDupeBal = CurrencyManager.getInstance().getBalance(player);

                    if (playerDupeBal < amountToPay) {
                        Messages.NOT_ENOUGH_MONEY.sendBoth(player);
                        return;
                    }

                    CurrencyManager.getInstance().withdraw(player, amountToPay);

                    OScheduler.async().run(() -> {
                        OrderManager.getInstance().updateOrder(order);

                        String itemName = selectItemBuilder != null && selectItemBuilder.hasItemMeta()
                                && selectItemBuilder.getItemMeta().hasDisplayName()
                                ? ChatColor.stripColor(selectItemBuilder.getItemMeta().getDisplayName())
                                : NameUtil.formatMaterialName(
                                        "{itemType}", createdItemBuilder.getType().name());

                        if (itemName.equalsIgnoreCase("{itemType}")) {
                            Messages.ON_ORDER_PLACED.sendBoth(player,
                                    "{amount}", order.getTotalAmount()+"",
                                    "{itemType}", new TranslationPlaceholder("{itemType}",
                                            selectItemBuilder == null ? createdItemBuilder.getType().translationKey() :
                                                    selectItemBuilder.getType().translationKey()
                                    )
                            );
                        } else {
                            Messages.ON_ORDER_PLACED.sendBoth(player,
                                    "{amount}", order.getTotalAmount()+"",
                                    "{itemType}", itemName);
                        }

                        /*Messages.ON_ORDER_PLACED.sendBoth(player,
                                "{amount}", order.getTotalAmount()+"",
                                "{itemType}", itemName);*/

                        DatabaseManager.getDatabase().createOrder(order);

                        WebhookManager.getInstance()
                                .logOrderCreate(player, order);
                    });

                    player.closeInventory();

                    /*DonutOrder.getGUIManager().openGUI(
                            new MainGUI(
                                    Filter.ALL,
                                    SortOrders.MOST_PAID,
                                    "",
                                    1,
                                    Collections.emptyList()
                            ),
                            player);*/
                    MainGUI.open(player);
                }));

        fillDecorationsFromConfig(ConfigType.NEW_ORDER);

        super.decorate(player);
    }

    private ItemBuilder createDisplayBuilder() {
        NormalItem currentItem = ItemManager.getInstance().getNormalItem(ConfigType.NEW_ORDER,"current_item");
        ItemBuilder displayBuilder;

        if (comesFromSelectItem && selectItemBuilder != null) {
            displayBuilder = new ItemBuilder(currentItem.getItem())
                    .type(selectItemBuilder.getType())
                    .lorePlaceholders(null, "{currentItem}",
                            selectItemBuilder.hasItemMeta() && selectItemBuilder.getItemMeta().hasDisplayName()
                                    ? ChatColor.stripColor(selectItemBuilder.getItemMeta().getDisplayName())
                                    : NameUtil.formatMaterialName(
                                            "{currentItem}", selectItemBuilder.getType().name()));

            if (ValuesManager.getInstance().isLocalization()) {
                ItemUtil.replaceWithTranslatable(
                        displayBuilder,
                        "{currentItem}",
                        displayBuilder.translationKey()
                );
            }

            if (selectItemBuilder.getItemMeta().hasCustomModelData()) {
                displayBuilder.customModelData(selectItemBuilder.getItemMeta().getCustomModelData());
            }
        } else {
            if (ValuesManager.getInstance().isLocalization()) {
                displayBuilder = new ItemBuilder(currentItem.getItem());
            } else {
                displayBuilder = new ItemBuilder(currentItem.getItem())
                    .lorePlaceholders(null, "{currentItem}",
                            NameUtil.formatMaterialName(
                                    "{currentItem}", currentItem.getItem().getType().name()));
            }

            if (ValuesManager.getInstance().isLocalization()) {
                ItemUtil.replaceWithTranslatable(
                        displayBuilder,
                        "{currentItem}",
                        displayBuilder.translationKey()
                );
            }
        }

        return displayBuilder;
    }

    private ItemBuilder getCleanItem(ItemBuilder builder) {
        return new ItemBuilder(builder.getType());
    }
}

