package me.rein.donutorder.commons.objectholders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.sound.SoundWrapper;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class BulletItem {
    private final String displayName;
    private final Material material;
    private int customModelData;
    private int slot;
    private final String selectedColor;
    private final String unselectedColor;
    private final String bulletIcon;
    private final List<String> options;
    private final Class<? extends Enum<?>> enumClass;
    private final SoundWrapper sound;

    public ItemBuilder getItem(Enum<?> selectedEnum) {
        String selectedOption = getDisplayName(selectedEnum);

        ItemBuilder item = new ItemBuilder(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatUtil.c(displayName));

        List<String> lore = new ArrayList<>();
        for (String option : options) {
            Messages messageEnum = Messages.valueOf(option);
            String translatedOption = messageEnum.getMessage();
            String color = translatedOption.equals(selectedOption) ? selectedColor : unselectedColor;
            lore.add(ChatUtil.c(color + bulletIcon + translatedOption));
        }

        meta.setLore(lore);
        if (customModelData != -1) {
            meta.setCustomModelData(customModelData);
        }
        item.setItemMeta(meta);

        return item;
    }

    public ItemBuilder getItem(String selectedOption) {
        ItemBuilder item = new ItemBuilder(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatUtil.c(displayName));

        List<String> lore = new ArrayList<>();
        for (String option : options) {
            Messages messageEnum = Messages.valueOf(option);
            String translatedOption = messageEnum.getMessage();
            String color = translatedOption.equals(selectedOption) ? selectedColor : unselectedColor;
            lore.add(ChatUtil.c(color + bulletIcon + translatedOption));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String getDisplayName(Enum<?> selectedEnum) {
        if (selectedEnum == null) return null;

        return switch (selectedEnum.getClass().getSimpleName()) {
            case "Filter" -> ((Filter) selectedEnum).getDisplayName();
            case "SortOrders" -> ((SortOrders) selectedEnum).getDisplayName();
            case "SortSelectItem" -> ((SortSelectItem) selectedEnum).getDisplayName();
            default -> selectedEnum.name();
        };
    }

    public Enum<?> getNextEnumOption(Enum<?> current) {
        if (current == null || enumClass == null) return null;

        return switch (current.getClass().getSimpleName()) {
            case "Filter" -> {
                int nextOrdinal = (current.ordinal() + 1) % Filter.values().length;
                yield Filter.values()[nextOrdinal];
            }
            case "SortOrders" -> {
                int nextOrdinal = (current.ordinal() + 1) % SortOrders.values().length;
                yield SortOrders.values()[nextOrdinal];
            }
            case "SortSelectItem" -> {
                int nextOrdinal = (current.ordinal() + 1) % SortSelectItem.values().length;
                yield SortSelectItem.values()[nextOrdinal];
            }
            default -> current;
        };
    }

    public String getNextOption(String currentOption) {
        int currentIndex = options.indexOf(currentOption);
        return options.get((currentIndex + 1) % options.size());
    }
}
