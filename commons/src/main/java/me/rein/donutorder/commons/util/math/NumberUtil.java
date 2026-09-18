package me.rein.donutorder.commons.util.math;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class NumberUtil {

    private static final DecimalFormat df = new DecimalFormat("#.##");

    private static final String[] SUFFIXES = {
            "N", "O", "Sp", "Sx", "Qi", "Q", "T", "B", "M", "K"
    };

    private static final double[] THRESHOLDS = {
            1e30, 1e27, 1e24, 1e21, 1e18, 1e15, 1e12, 1e9, 1e6, 1e3
    };

    private static final String[] PARSE_SUFFIXES = {
            "qi", "sx", "sp", "n", "o", "q", "t", "b", "m", "k"
    };

    private static final double[] PARSE_MULTIPLIERS = {
            1e18, 1e21, 1e24, 1e30, 1e27, 1e15, 1e12, 1e9, 1e6, 1e3
    };

    private static final BigDecimal[] PARSE_BD_MULTIPLIERS = {
            new BigDecimal("1000000000000000000"),      // qi - 1e18
            new BigDecimal("1000000000000000000000"),    // sx - 1e21
            new BigDecimal("1000000000000000000000000"), // sp - 1e24
            new BigDecimal("1000000000000000000000000000000"),    // n - 1e30
            new BigDecimal("1000000000000000000000000000"),       // o - 1e27
            new BigDecimal("1000000000000000"),          // q - 1e15
            new BigDecimal("1000000000000"),             // t - 1e12
            new BigDecimal("1000000000"),                // b - 1e9
            new BigDecimal("1000000"),                   // m - 1e6
            new BigDecimal("1000"),                      // k - 1e3
    };

    private static final BigDecimal BD_LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    public static String formatPrice(
            double price
    ) {
        if (price < 0)
            return "-" + formatPrice(-price);

        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (price >= THRESHOLDS[i]) {
                return formatClean(price / THRESHOLDS[i]) + SUFFIXES[i];
            }
        }

        return formatClean(price);
    }

    public static String formatNumber(
            double number
    ) {
        if (number < 0)
            return "-" + formatNumber(-number);

        if (number == 0)
            return "0";

        if (number < 1000)
            return formatClean(number);

        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (number >= THRESHOLDS[i]) {
                return formatClean(number / THRESHOLDS[i]) + SUFFIXES[i];
            }
        }

        return formatClean(number / 1000.0) + "K";
    }

    public static String formatNumber(
            long number
    ) {
        return formatNumber((double) number);
    }

    private static String formatClean(
            double value
    ) {
        if (value == (long) value)
            return String.format("%d", (long) value);

        return df.format(value);
    }

    public static double parseAmount(
            String input
    ) {
        if (input == null || input.trim().isEmpty())
            return -1;

        String str = input.trim().toLowerCase();

        try {
            str = str.replaceAll("[, ]", "");

            double result;

            String matchedSuffix = null;
            int suffixIndex = -1;
            for (int i = 0; i < PARSE_SUFFIXES.length; i++) {
                if (str.endsWith(PARSE_SUFFIXES[i])) {
                    matchedSuffix = PARSE_SUFFIXES[i];
                    suffixIndex = i;
                    break;
                }
            }

            if (matchedSuffix != null) {
                String numPart = str.substring(0, str.length() - matchedSuffix.length());
                if (numPart.isEmpty())
                    return -1;
                double number = Double.parseDouble(numPart);
                result = number * PARSE_MULTIPLIERS[suffixIndex];
            } else if (str.matches("\\d+")) {
                result = Double.parseDouble(str);
            } else {
                result = Double.parseDouble(str);
            }

            if (Double.isNaN(result) || Double.isInfinite(result))
                return -1;

            return result;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int parseAmountInt(
            String input
    ) {
        double amount = parseAmount(input);
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    public static long parseAmountLong(
            String input
    ) {
        if (input == null || input.trim().isEmpty())
            return -1;

        String str = input.trim().toLowerCase();

        try {
            str = str.replaceAll("[, ]", "");

            for (int i = 0; i < PARSE_SUFFIXES.length; i++) {
                if (str.endsWith(PARSE_SUFFIXES[i])) {
                    String numPart = str.substring(0, str.length() - PARSE_SUFFIXES[i].length());
                    if (numPart.isEmpty())
                        return -1;
                    BigDecimal number = new BigDecimal(numPart);
                    BigDecimal result = number.multiply(PARSE_BD_MULTIPLIERS[i]);
                    if (result.compareTo(BD_LONG_MAX) > 0)
                        return Long.MAX_VALUE;
                    if (result.compareTo(BigDecimal.ZERO) < 0)
                        return -1;
                    return result.longValue();
                }
            }

            BigDecimal result = new BigDecimal(str);
            if (result.compareTo(BD_LONG_MAX) > 0)
                return Long.MAX_VALUE;
            if (result.compareTo(BigDecimal.ZERO) < 0)
                return -1;
            return result.longValue();
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
