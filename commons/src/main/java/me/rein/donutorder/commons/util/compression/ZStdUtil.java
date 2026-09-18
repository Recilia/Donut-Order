package me.rein.donutorder.commons.util.compression;

import com.github.luben.zstd.Zstd;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class ZStdUtil {

    private static final int MAX_DECOMPRESSED_SIZE = 1024 * 1024;

    public static byte[] serializeItemStack(
            ItemStack itemStack
    ) {
        if (itemStack == null)
            return null;

        if (itemStack instanceof ItemBuilder) {
            itemStack = ((ItemBuilder) itemStack).toItemStack();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(itemStack);
            dataOutput.flush();

            return Zstd.compress(outputStream.toByteArray());
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to serialize ItemStack: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack deserializeItemStack(
            byte[] data
    ) {
        if (data == null || data.length == 0)
            return null;

        ItemStack item = ZStdUtil.deserializeZStdCompressed(data);
        if (item != null)
            return item;

        item = ZStdUtil.deserializeUncompressed(data);
        if (item == null) {
            Bukkit.getLogger().warning("Failed to deserialize ItemStack - all methods exhausted");
            return null;
        }

        return item;
    }

    private static ItemStack deserializeZStdCompressed(
            byte[] data
    ) {
        try {
            byte[] decompressedData = Zstd.decompress(data, MAX_DECOMPRESSED_SIZE);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decompressedData);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack deserializeUncompressed(
            byte[] data
    ) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}