package me.rein.donutorder.commons.util.misc;

import dev.demeng.sentinel.wrapper.SentinelClient;
import dev.demeng.sentinel.wrapper.exception.*;
import me.rein.donutorder.commons.Commons;
import me.rein.donutorder.commons.manager.ValuesManager;
import org.bukkit.Bukkit;

import java.io.IOException;

public class ATest {

    public static Object test() {
//        return true;
        SentinelClient client = new SentinelClient(
                "https://auth.spoof.dev/api/v1",
                "9rdhjgmqsngeeiv4bvp4e1vm1k",
                "V/kWPI3HfcY+njag01SXj5bIQJ9yDoHhUtLxIJCAktY=");

        boolean authenticated = false;

        try {
            client.getLicenseController().auth(
                    ValuesManager.getInstance().getLicenseKey(),
                    "Order",
                    null,
                    null,
                    SentinelClient.getCurrentHwid(),
                    SentinelClient.getCurrentIp());
            authenticated = true;
        } catch (
                InvalidLicenseException e) {
            Bukkit.getLogger().severe("Invalid license key.");
        } catch (
                ExpiredLicenseException e) {
            Bukkit.getLogger().severe("Expired.");
        } catch (
                BlacklistedLicenseException e) {
            Bukkit.getLogger().severe("Blacklisted.");
        } catch (
                ConnectionMismatchException e) {
            Bukkit.getLogger().severe("Provided connection does not match.");
        } catch (
                ExcessiveServersException e) {
            Bukkit.getLogger().severe("Too many servers. (Max: " + e.getMaxServers() + ")");
        } catch (ExcessiveIpsException e) {
            Bukkit.getLogger().severe("Too many IPs. (Max: " + e.getMaxIps() + ")");
        } catch (InvalidProductException e) {
            Bukkit.getLogger().severe("License is for different product.");
        } catch (InvalidPlatformException e) {
            Bukkit.getLogger().severe("Provided connection platform is invalid.");
        } catch (
                IOException e) {

            Bukkit.getLogger().severe("[DonutOrder] An unexpected error occurred: "+e.getMessage());
            if (e.getMessage().toLowerCase().contains("connection timed out")) {
                Bukkit.getLogger().severe("[DonutOrder] Host unreachable - Emergency failsafe protocol enabled");
                return true;
            }

            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (errorMsg.contains("connection timed out")
                    || errorMsg.contains("http response code: 5")
                    || errorMsg.contains("server returned http")
                    || errorMsg.contains("unreachable")
                    || errorMsg.contains("refused")
                    || errorMsg.contains("reset")
                    || errorMsg.contains("closed")
                    || errorMsg.contains("timeout")
                    || errorMsg.contains("socket")
                    || errorMsg.contains("connect")
                    || errorMsg.isEmpty()
                    || e instanceof java.net.SocketTimeoutException
                    || e instanceof java.net.ConnectException) {

                Bukkit.getLogger().severe("[VirtualSpawner] License server unreachable - Emergency failsafe protocol enabled");
                return true;
            }
        }

        if(authenticated) {
            Bukkit.getLogger().info("Your Order license key is valid!");
            return true;
        }

        Bukkit.getLogger().severe("Your Order license key is invalid!\nCreate a ticket in our discord server to get one.");
        Bukkit.getPluginManager().disablePlugin(Commons.getPlugin());

        return null;
    }
}
