package me.rein.donutorder.commons.util.time;

import me.rein.donutorder.commons.manager.ValuesManager;

import java.util.concurrent.TimeUnit;

public class TimeUtil {

    public static String formatTimeConcise(
            long milliseconds
    ) {
        ValuesManager values = ValuesManager.getInstance();

        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60;

        boolean showSeconds = true;

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days);
            result.append(days == 1 ? values.getDayFormat() : values.getDaysFormat());
            result.append(" ");

            showSeconds = false;
        }

        if (hours > 0) {
            result.append(hours);
            result.append(hours == 1 ? values.getHourFormat() : values.getHoursFormat());
            result.append(" ");

            showSeconds = false;
        }

        if (minutes > 0) {
            result.append(minutes);
            result.append(minutes == 1 ? values.getMinuteFormat() : values.getMinutesFormat());
            result.append(" ");

            showSeconds = false;
        }

        if (showSeconds) {
            result.append(seconds);
            result.append(seconds == 1 ? values.getSecondFormat() : values.getSecondsFormat());
            result.append(" ");
        }

        return result.toString().trim();
    }
}
