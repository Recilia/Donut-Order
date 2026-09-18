package me.rein.donutorder.commons.util.sound;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Getter
@AllArgsConstructor
public class SoundWrapper {

    private Sound sound;
    private float volume;
    private float pitch;

    public void play(
            Player player
    ) {
        player.playSound(
                player.getLocation(),
                sound,
                volume,
                pitch
        );
    }

    public void play(
            Location location
    ) {
        location.getWorld().playSound(
                location,
                sound,
                volume,
                pitch
        );
    }
}

