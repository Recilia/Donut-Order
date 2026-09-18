package me.rein.donutorder.commons.util.item;

import me.rein.donutorder.commons.papi.PAPI;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ItemBuilder extends ItemStack {
    private static boolean isLegacy = false;

    public static void initItemBuilder() {
        try {
            Class.forName("org.bukkit.profile.PlayerTextures");
        } catch (Exception e) {
            isLegacy = true;
        }
    }

    /**
     * Initializes the builder with the given {@link Material}
     *
     * @param mat the {@link Material} to start the builder from
     * @since 1.0
     */
    public ItemBuilder(final Material mat) {
        super(mat);
    }

    public ItemBuilder(Material material, short data) {
        super(material, 1, data);
    }

    /**
     * Inits the builder with the given {@link ItemStack}
     *
     * @param is the {@link ItemStack} to start the builder from
     * @since 1.0
     */
    public ItemBuilder(final ItemStack is) {
        super(is);
    }

    public @NotNull String translationKey() {
        return Bukkit.getUnsafe().getTranslationKey(this);
    }

    /**
     * Adds an {@link Enchantment} with the given level to the {@link ItemStack}
     *
     * @param enchantment the enchantment to add
     * @param level       the level of the enchantment
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder enchant(final Enchantment enchantment, final int level) {
        addUnsafeEnchantment(enchantment, level);
        return this;
    }

    /**
     * Adds an {@link Enchantment} with level 1 to the {@link ItemStack}
     *
     * @param enchantment the enchantment to add
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder enchant(final Enchantment enchantment) {
        return enchant(enchantment, 1);
    }

    /**
     * Adds {@link ItemFlag}s to the {@link ItemStack}
     *
     * @param flags the flags to add
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder flag(ItemFlag... flags) {
        return itemflag(flags);
    }

    /**
     * Changes the amount of the {@link ItemStack}
     *
     * @param amount the new amount to set
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder amount(final int amount) {
        setAmount(amount);
        return this;
    }
    /**
     * Changes the display name of the {@link ItemStack}
     *
     * @param name the new display name to set
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder name(final String name) {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;
        meta.setDisplayName(ChatUtil.c(name));
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder skullOwner(String owner) {
        ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        final SkullMeta skullMeta = (SkullMeta) meta;

        Player player = Bukkit.getPlayer(owner);
        if (player != null)
            skullMeta.setOwningPlayer(player);
        setItemMeta(skullMeta);
        return this;
    }

    /**
     * Adds a new line to the lore of the {@link ItemStack}
     *
     * @param text the new line to add
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder lore(OfflinePlayer player, final String... text) {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        List<String> processedLore = ChatUtil.c(Arrays.asList(text.clone()));
        processedLore = PAPI.setPlaceholders(player, processedLore);
        meta.setLore(processedLore);
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder appendLore(OfflinePlayer player, List<String> text) {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        List<String> processedText = text;
        processedText = PAPI.setPlaceholders(player, text);

        if (!hasItemMeta() || !meta.hasLore()) {
            meta.setLore(ChatUtil.c(processedText));
            setItemMeta(meta);
            return this;
        }

        List<String> list = new ArrayList<>(meta.getLore());
        for (String s : processedText) {
            list.add(s);
        }

        meta.setLore(ChatUtil.c(list));
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder appendLore(OfflinePlayer player, String... text) {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        List<String> processedText = Arrays.asList(text);
        processedText = PAPI.setPlaceholders(player, processedText);

        if (!hasItemMeta() || !meta.hasLore()) {
            meta.setLore(ChatUtil.c(processedText));
            setItemMeta(meta);
            return this;
        }

        List<String> list = new ArrayList<>(meta.getLore());
        list.addAll(processedText);

        meta.setLore(ChatUtil.c(list));
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore2(OfflinePlayer player, final List<String> list) {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        List<String> processedList = list;
        processedList = PAPI.setPlaceholders(player, list);

        meta.setLore(ChatUtil.c(processedList));
        setItemMeta(meta);
        return this;
    }

    /**
     * Changes the durability of the {@link ItemStack}
     *
     * @param durability the new durability to set
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder durability(final int durability) {
        setDurability((short) durability);
        return this;
    }

    /**
     * Adds an {@link Enchantment} with the given level to the {@link ItemStack}
     *
     * @param enchantment the enchantment to add
     * @param level       the level of the enchantment
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder enchantment(final Enchantment enchantment, final int level) {
        addUnsafeEnchantment(enchantment, level);
        return this;
    }

    /**
     * Adds an {@link Enchantment} with the level 1 to the {@link ItemStack}
     *
     * @param enchantment the enchantment to add
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder enchantment(final Enchantment enchantment) {
        ItemMeta itM = getItemMeta();
        if (itM == null)
            return this;

        itM.addEnchant(enchantment, 1, true);
        setItemMeta(itM);
        return this;
    }

    /**
     * Changes the {@link Material} of the {@link ItemStack}
     *
     * @param material the new material to set
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder type(final Material material) {
        setType(material);
        return this;
    }

    /**
     * Clears the lore of the {@link ItemStack}
     *
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder clearLore() {
        final ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        meta.setLore(new ArrayList<>());
        setItemMeta(meta);
        return this;
    }

    /**
     * Clears the list of {@link Enchantment}s of the {@link ItemStack}
     *
     * @return this builder for chaining
     * @since 1.0
     */
    public ItemBuilder clearEnchantments() {
        getEnchantments().keySet().forEach(this::removeEnchantment);
        return this;
    }

    /**
     * Sets the {@link Color} of a part of leather armor
     *
     * @param color the {@link Color} to use
     * @return this builder for chaining
     * @since 1.1
     */
    public ItemBuilder color(Color color) {
        if (getType() == XUtil.crossMaterial("LEATHER_BOOTS") || getType() == XUtil.crossMaterial("LEATHER_CHESTPLATE") ||
                getType() == XUtil.crossMaterial("LEATHER_HELMET")
                || getType() == XUtil.crossMaterial("LEATHER_LEGGINGS")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) getItemMeta();
            if (meta == null)
                return this;

            meta.setColor(color);
            setItemMeta(meta);
            return this;
        } else {
            throw new IllegalArgumentException("color() only applicable for leather armor!");
        }
    }

    public ItemBuilder itemflag(ItemFlag... flags) {
        ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        meta.addItemFlags(flags);

        this.setItemMeta(meta);
        return this;
    }

    public ItemBuilder customModelData(int customModelData) {
        ItemMeta meta = getItemMeta();
        if (meta == null)
            return this;

        meta.setCustomModelData(customModelData);

        this.setItemMeta(meta);
        return this;
    }

    public ItemBuilder namePlaceholder(String placeholder, String value) {
        final ItemMeta meta = getItemMeta();
        if (meta == null || !meta.hasDisplayName() || placeholder == null)
            return this;

        String name = meta.getDisplayName();
        String safeValue = value != null ? value : "";
        meta.setDisplayName(name.replace(placeholder, safeValue));
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder namePlaceholders(String... placeholders) {
        if (placeholders == null || placeholders.length % 2 != 0) {
            return this;
        }

        final ItemMeta meta = getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return this;

        String name = meta.getDisplayName();
        String newName = name;

        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = placeholders[i];
            String value = placeholders[i + 1];
            if (placeholder != null) {
                newName = newName.replace(placeholder, value != null ? value : "");
            }
        }

        meta.setDisplayName(newName);
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder lorePlaceholder(OfflinePlayer player, String placeholder, String value) {
        final ItemMeta meta = getItemMeta();
        if (meta == null || !meta.hasLore() || placeholder == null)
            return this;

        List<String> lore = meta.getLore();
        List<String> newLore = new ArrayList<>();
        String safeValue = value != null ? value : "";

        for (String line : lore) {
            if (line != null) {
                newLore.add(line.replace(placeholder, safeValue));
            } else {
                newLore.add("");
            }
        }

        newLore = PAPI.setPlaceholders(player, newLore);

        meta.setLore(newLore);
        setItemMeta(meta);
        return this;
    }

    public ItemBuilder lorePlaceholders(OfflinePlayer player, String... placeholders) {
        if (placeholders == null || placeholders.length % 2 != 0) {
            return this;
        }

        final ItemMeta meta = getItemMeta();
        if (meta == null || !meta.hasLore())
            return this;

        List<String> lore = meta.getLore();
        List<String> newLore = new ArrayList<>();

        for (String line : lore) {
            if (line == null) {
                newLore.add("");
                continue;
            }

            String newLine = line;
            for (int i = 0; i < placeholders.length; i += 2) {
                String placeholder = placeholders[i];
                String value = placeholders[i + 1];
                if (placeholder != null) {
                    newLine = newLine.replace(placeholder, value != null ? value : "");
                }
            }
            newLore.add(newLine);
        }

        newLore = PAPI.setPlaceholders(player, newLore);

        meta.setLore(newLore);
        setItemMeta(meta);
        return this;
    }

    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(getType());
        item.setAmount(getAmount());
        if (getItemMeta() != null) {
            item.setItemMeta(getItemMeta().clone());
        }
        return item;
    }
}
