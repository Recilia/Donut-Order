package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.util.sound.SoundWrapper;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SoundManager {

    @Getter(lazy = true)
    private static final SoundManager instance = new SoundManager();

    private Map<String, SoundWrapper> sounds;

    public SoundWrapper getSound(String soundName) {
        return sounds.getOrDefault(soundName, null);
    }

    public void loadSounds() {
        sounds = new HashMap<>();

        FileConfiguration config = ConfigSelector.SOUNDS.getConfig();

        for (String soundName : config.getKeys(false)) {
            sounds.put(soundName, parse(soundName, config));
        }
    }

    private SoundWrapper parse(String soundName, FileConfiguration config) {
        String sound = config.getString(soundName + ".sound");
        float volume = (float) config.getDouble(soundName + ".volume");
        float pitch = (float) config.getDouble(soundName + ".pitch");

        return new SoundWrapper(
                Sound.valueOf(sound),
                volume,
                pitch
        );
    }
}
