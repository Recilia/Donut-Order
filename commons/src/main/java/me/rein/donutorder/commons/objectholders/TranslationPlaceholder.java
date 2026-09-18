package me.rein.donutorder.commons.objectholders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;

@Getter
@AllArgsConstructor
public class TranslationPlaceholder {
    private final String placeholder;
    private final String translationKey;

    public Component getComponent() {
        return Component.translatable(translationKey);
    }
}
