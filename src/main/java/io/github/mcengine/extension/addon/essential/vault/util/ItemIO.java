package io.github.mcengine.extension.addon.essential.vault.util;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Item (de)serialization helpers for storing full Bukkit {@link ItemStack}
 * (including metadata/NBT) as a compact binary blob suitable for DB persistence.
 *
 * <p>Uses {@link BukkitObjectOutputStream} / {@link BukkitObjectInputStream}.</p>
 */
public final class ItemIO {

    /**
     * Prevent instantiation of utility class.
     */
    private ItemIO() {}

    /**
     * Serializes an {@link ItemStack} into a byte array. Returns {@code null} on failure.
     *
     * @param item the item to serialize
     * @return binary representation or {@code null} if an error occurred
     */
    public static byte[] toBytes(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Deserializes a byte array back into an {@link ItemStack}. Returns {@code null} on failure.
     *
     * @param bytes binary payload
     * @return reconstructed {@link ItemStack} or {@code null} if an error occurred
     */
    public static ItemStack fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            Object obj = ois.readObject();
            return (obj instanceof ItemStack is) ? is : null;
        } catch (IOException | ClassNotFoundException ex) {
            return null;
        }
    }
}
