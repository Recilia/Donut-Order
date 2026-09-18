package me.rein.donutorder.commons.objectholders;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class EnchantmentInfo {
    private final Map<Enchantment, Integer> enchantments;

    public EnchantmentInfo(ItemStack item) {
        this.enchantments = getItemEnchantments(item);
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    public boolean hasEnchantments() {
        return !enchantments.isEmpty();
    }

    public String getFormattedEnchantments() {
        if (!hasEnchantments()) return "None";

        return enchantments.entrySet().stream()
                .map(entry -> formatEnchantment(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String formatEnchantment(Enchantment enchantment, int level) {
        String name = enchantment.getKey().getKey()
                .replace("_", " ")
                .toLowerCase();

        name = Arrays.stream(name.split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));

        return name + " " + toRoman(level);
    }

    private String toRoman(int number) {
        if (number <= 0) return "0";

        String[] romanNumerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number <= 10 ? romanNumerals[number] : String.valueOf(number);
    }

    private Map<Enchantment, Integer> getItemEnchantments(ItemStack item) {
        if (item == null) return new HashMap<>();

        if (item.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            return new HashMap<>(meta.getStoredEnchants());
        }

        return new HashMap<>(item.getEnchantments());
    }
}
