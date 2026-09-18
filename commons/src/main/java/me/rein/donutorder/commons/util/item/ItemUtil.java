package me.rein.donutorder.commons.util.item;

import me.rein.donutorder.commons.manager.ValuesManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ItemUtil {

    /**
     * Replaces a specific placeholder in the item lore with a translatable component.
     *
     * @param item The ItemBuilder to modify the lore of
     * @param placeholderToReplace The placeholder to replace with a component (e.g. "{itemType}")
     * @param translationKey The translation key for the translatable component
     * @return The modified ItemBuilder for method chaining
     */
    public static void replaceWithTranslatable(
            ItemBuilder item,
            String placeholderToReplace,
            String translationKey
    ) {
        if (!ValuesManager.getInstance().isLocalization()) {
            return;
        }

        TranslatableComponent translatableComponent = Component.translatable(translationKey);

        List<Component> currentLore = item.lore();
        if (currentLore != null) {
            List<Component> newLore = new ArrayList<>();

            for (Component loreLine : currentLore) {
                Component newLine = loreLine.replaceText(builder ->
                        builder.match(Pattern.quote(placeholderToReplace)).replacement(translatableComponent));
                newLore.add(newLine);
            }

            item.lore(newLore);
        }
    }

    /**
     * Checks if an item has no custom display name.
     *
     * @param item The ItemStack to check
     * @return true if the item has no display name, false otherwise
     */
    public static boolean hasNoDisplayName(
            ItemStack item
    ) {
        return !(item.hasItemMeta() && item.getItemMeta().hasDisplayName());
    }

    public static boolean hasSimilarMetadata(
            ItemStack item1,
            ItemStack item2
    ) {
        if (item1 == null || item2 == null)
            return false;

        if (item1.getType() != item2.getType())
            return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        if (meta1 == null && meta2 == null)
            return true;

        if (meta1 == null || meta2 == null)
            return false;

        if (meta1.hasDisplayName() != meta2.hasDisplayName())
            return false;

        if (meta1.hasDisplayName() && !meta1.getDisplayName().equals(meta2.getDisplayName()))
            return false;

        if (meta1.hasLore() != meta2.hasLore())
            return false;

        if (meta1.hasLore() && !meta1.getLore().equals(meta2.getLore()))
            return false;

        if (!meta1.getEnchants().equals(meta2.getEnchants()))
            return false;

        if (meta1.hasCustomModelData() != meta2.hasCustomModelData())
            return false;

        if (meta1.hasCustomModelData() && meta1.getCustomModelData() != meta2.getCustomModelData())
            return false;

        if (meta1 instanceof PotionMeta pmeta1 && meta2 instanceof PotionMeta pmeta2) {
            try {
                org.bukkit.potion.PotionData data1 = pmeta1.getBasePotionData();
                org.bukkit.potion.PotionData data2 = pmeta2.getBasePotionData();

                if (!data1.equals(data2))
                    return false;
            } catch (Exception ignored) {}
        }

        if (meta1 instanceof EnchantmentStorageMeta && meta2 instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta emeta1 = (EnchantmentStorageMeta) meta1;
            EnchantmentStorageMeta emeta2 = (EnchantmentStorageMeta) meta2;

            if (!emeta1.getStoredEnchants().equals(emeta2.getStoredEnchants()))
                return false;
        }

        if (ValuesManager.getInstance().isRequireFullDurability())
            return !(meta1 instanceof Damageable damageable) || damageable.getDamage() == 0;

        return true;
    }
}
