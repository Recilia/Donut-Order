package me.rein.donutorder.commons.objectholders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.sound.SoundWrapper;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@Getter
@AllArgsConstructor
public class NormalItem {
    private final String displayName;
    private List<String> lore;
    private Material material;
    private int customModelData;
    private List<Integer> slots;
    private SoundWrapper sound;

    public int getSlot() {
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    public ItemBuilder getItem() {
        ItemBuilder item = new ItemBuilder(material);

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatUtil.c(displayName));
        meta.setLore(ChatUtil.c(lore));
        if (customModelData != -1) {
            meta.setCustomModelData(customModelData);
        }
        item.setItemMeta(meta);

        return item;
    }
}
