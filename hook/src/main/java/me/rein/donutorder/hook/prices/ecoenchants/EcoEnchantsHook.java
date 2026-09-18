package me.rein.donutorder.hook.prices.ecoenchants;

import com.willfp.ecoenchants.enchant.EcoEnchant;
import com.willfp.ecoenchants.enchant.EcoEnchants;
import me.rein.donutorder.commons.manager.price.PriceManager;
import me.rein.donutorder.commons.util.xseries.XUtil;
import me.rein.donutorder.hook.Hook;
import me.rein.donutorder.commons.manager.ValuesManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public class EcoEnchantsHook extends Hook {

    public EcoEnchantsHook() {}

    @Override
    public void init(JavaPlugin plugin) {
        Collection<EcoEnchant> allEnchants = EcoEnchants.INSTANCE.values();

        for (EcoEnchant enchant : allEnchants) {
            ItemStack book = new ItemStack(XUtil.crossMaterial("ENCHANTED_BOOK"));
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(enchant.getEnchantment(), 1, true);
            book.setItemMeta(meta);

            if (!ValuesManager.getInstance().isStopAutoMigrationOnStart())
                PriceManager.getInstance().addOrUpdatePrice(book, 200.0);
        }
    }
}
