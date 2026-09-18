package me.rein.donutorder.commons.util.xseries;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;

public class XUtil {

    public static Material crossMaterial(
            String mat
    ) {
        return XMaterial.matchXMaterial(mat)
                .orElse(XMaterial.STONE)
                .get();
    }

    public static Material crossMaterial(
            Material mat
    ) {
        return XMaterial.matchXMaterial(mat)
                .get();
    }
}
