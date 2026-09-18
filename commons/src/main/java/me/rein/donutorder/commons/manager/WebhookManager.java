package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.math.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebhookManager {

    @Getter(lazy = true)
    private static final WebhookManager instance = new WebhookManager();

    private boolean enabled;
    private String url;
    private String username = "DonutOrder";

    private boolean logCreate;
    private boolean logDeliver;
    private boolean logCollect;
    private boolean logExpire;
    private boolean logCancel;

    public void load() {
        FileConfiguration config = ConfigSelector.CONFIG.getConfig();
        ConfigurationSection section = config.getConfigurationSection("webhook");

        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", false);
        url = section.getString("url", "");
        username = section.getString("username", "DonutOrder");

        ConfigurationSection events = section.getConfigurationSection("events");
        if (events != null) {
            logCreate = events.getBoolean("order-create", true);
            logDeliver = events.getBoolean("order-deliver", true);
            logCollect = events.getBoolean("order-collect", true);
            logExpire = events.getBoolean("order-expire", true);
            logCancel = events.getBoolean("order-cancel", true);
        } else
            logCreate = logDeliver = logCollect = logExpire = logCancel = true;

        if (enabled && (url == null || url.isEmpty())) {
            Bukkit.getLogger().warning("[DonutOrder] Webhook is enabled but URL is empty.");
            enabled = false;
        }
    }

    public void logOrderCreate(
            Player player,
            Order order
    ) {
        if (!enabled || !logCreate)
            return;

        send(buildEmbed("📦 Order Created", 0x4287F5, player, order, null, null, null, null));
    }

    public void logOrderDeliver(
            Player player,
            Order order,
            long amountDelivered,
            double moneyEarned
    ) {
        if (!enabled || !logDeliver)
            return;

        send(buildEmbed("✅ Order Delivery", 0x42F557, player, order,
                "Delivered", String.valueOf(amountDelivered),
                "Earned", "$" + NumberUtil.formatPrice(moneyEarned)));
    }

    public void logOrderCollect(
            Player player,
            Order order,
            long amountCollected
    ) {
        if (!enabled || !logCollect)
            return;

        send(buildEmbed("📥 Order Collected", 0xF5A742, player, order,
                "Collected", String.valueOf(amountCollected), null, null));
    }

    public void logOrderExpire(
            Order order,
            double refunded
    ) {
        if (!enabled || !logExpire)
            return;

        send(buildEmbed("⏰ Order Expired", 0xF54242, null, order,
                "Refunded", "$" + NumberUtil.formatPrice(refunded), null, null));
    }

    public void logOrderCancel(
            Player player,
            Order order,
            double refunded
    ) {
        if (!enabled || !logCancel) return;

        send(buildEmbed("❌ Order Cancelled", 0x808080, player, order,
                "Refunded", "$" + NumberUtil.formatPrice(refunded), null, null));
    }

    private String buildEmbed(
            String title,
            int color,
            Player actor,
            Order order,
            String extraLabel1, String extraValue1,
            String extraLabel2, String extraValue2
    ) {
        String itemName = order.getItem() != null ? order.getItem().getType().name() : "UNKNOWN";
        String creator = order.getPlayerName() != null ? order.getPlayerName() : order.getCreatorId().toString();
        String actorName = actor != null ? actor.getName() : null;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        // build fields list
        List<String[]> fields = new ArrayList<>();
        fields.add(new String[]{"Creator", creator, "true"});
        if (actorName != null && !actorName.equals(creator))
            fields.add(new String[]{"Player", actorName, "true"});

        fields.add(new String[]{"Item", "`" + itemName + "`", "true"});
        fields.add(new String[]{"Amount", String.valueOf(order.getTotalAmount()), "true"});
        fields.add(new String[]{"Price/Item", "$" + NumberUtil.formatPrice(order.getPricePerItem()), "true"});
        fields.add(new String[]{"Total Cost", "$" + NumberUtil.formatPrice(order.getTotalCost()), "true"});

        if (extraLabel1 != null && extraValue1 != null)
            fields.add(new String[]{extraLabel1, extraValue1, "true"});

        if (extraLabel2 != null && extraValue2 != null)
            fields.add(new String[]{extraLabel2, extraValue2, "true"});

        // build JSON
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"username\":\"").append(escapeJson(username)).append("\"");
        json.append(",\"embeds\":[{");
        json.append("\"title\":\"").append(escapeJson(title)).append("\"");
        json.append(",\"color\":").append(color);
        json.append(",\"timestamp\":\"").append(timestamp).append("\"");

        json.append(",\"fields\":[");
        for (int i = 0; i < fields.size(); i++) {
            String[] f = fields.get(i);
            if (i > 0) json.append(",");
            json.append("{\"name\":\"").append(escapeJson(f[0])).append("\"");
            json.append(",\"value\":\"").append(escapeJson(f[1])).append("\"");
            json.append(",\"inline\":").append(f[2]).append("}");
        }
        json.append("]");

        // footer with order ID
        json.append(",\"footer\":{\"text\":\"Order ID: ").append(escapeJson(order.getOrderId().toString())).append("\"}");

        json.append("}]}");

        return json.toString();
    }

    private void send(
            String json
    ) {
        if (url == null || url.isEmpty())
            return;

        OScheduler.async().run(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "DonutOrder-Webhook");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300)
                    Bukkit.getLogger().warning("[DonutOrder] Webhook returned HTTP " + code);

                conn.disconnect();
            } catch (Exception e) {
                Bukkit.getLogger().warning("[DonutOrder] Webhook send failed: " + e.getMessage());
            }
        });
    }

    private String escapeJson(
            String s
    ) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
