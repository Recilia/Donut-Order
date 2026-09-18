package me.rein.donutorder.commons.util.message;

import me.rein.donutorder.commons.manager.ValuesManager;

public class NameUtil {

    public static String formatMaterialName(
            String placeholderToRevert,
            String materialName
    ) {
        if (ValuesManager.getInstance().isLocalization())
            return placeholderToRevert;

        String[] words = materialName.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            result.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1));

            if (i < words.length - 1)
                result.append(" ");
        }

        return result.toString();
    }
}
