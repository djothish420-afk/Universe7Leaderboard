package com.universe7.leaderboard;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Universe7Leaderboard extends JavaPlugin {
    private Economy economy;
    private final Map<String, UUID> displays = new HashMap<>();
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.##");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();
        loadDisplays();
        Objects.requireNonNull(getCommand("u7lb")).setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshDisplays, 20L * 30, 20L * 30);
        getLogger().info("Universe7Leaderboard enabled.");
    }

    @Override
    public void onDisable() {
        saveDisplays();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
    }

    private void loadDisplays() {
        ConfigurationSection section = getConfig().getConfigurationSection("displays");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String raw = section.getString(key + ".entity");
            if (raw == null) continue;
            try { displays.put(key, UUID.fromString(raw)); } catch (IllegalArgumentException ignored) {}
        }
        Bukkit.getScheduler().runTask(this, this::refreshDisplays);
    }

    private void saveDisplays() {
        for (String key : new ArrayList<>(displays.keySet())) {
            Entity entity = findEntity(displays.get(key));
            if (entity != null) getConfig().set("displays." + key + ".location",
                    serializeLocation(entity.getLocation()));
        }
        saveConfig();
    }

    private Entity findEntity(UUID id) {
        if (id == null) return null;
        for (World world : Bukkit.getWorlds()) {
            Entity e = world.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private String serializeLocation(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    private Location parseLocation(String value) {
        try {
            String[] p = value.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception ex) { return null; }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private String prettyType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "kills" -> "Player Kills";
            case "deaths" -> "Deaths";
            case "playtime" -> "Playtime";
            case "money" -> "Richest Players";
            default -> type;
        };
    }

    private List<OfflinePlayer> knownPlayers() {
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null)
                .collect(Collectors.toList());
    }

    private long stat(OfflinePlayer player, String type) {
        try {
            if (type.equalsIgnoreCase("kills")) return player.getStatistic(Statistic.PLAYER_KILLS);
            if (type.equalsIgnoreCase("deaths")) return player.getStatistic(Statistic.DEATHS);
            if (type.equalsIgnoreCase("playtime")) {
                // Support APIs exposing PLAY_TIME (ticks) or legacy PLAY_ONE_MINUTE (ticks).
                try {
                    Object value = Statistic.class.getField("PLAY_TIME").get(null);
                    return (long) player.getStatistic((Statistic) value);
                } catch (ReflectiveOperationException ignored) {
                    try {
                        Object value = Statistic.class.getField("PLAY_ONE_MINUTE").get(null);
                        return (long) player.getStatistic((Statistic) value);
                    } catch (ReflectiveOperationException ignoredAgain) { return 0L; }
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private double score(OfflinePlayer p, String type) {
        if (type.equalsIgnoreCase("money")) {
            if (economy == null) return 0;
            try { return economy.getBalance(p); } catch (Exception ignored) { return 0; }
        }
        return stat(p, type);
    }

    private String formatValue(OfflinePlayer p, String type, double value) {
        if (type.equalsIgnoreCase("money")) return "$" + moneyFormat.format(value);
        if (type.equalsIgnoreCase("playtime")) {
            long seconds = Math.max(0, (long) value / 20);
            long days = seconds / 86400; seconds %= 86400;
            long hours = seconds / 3600; seconds %= 3600;
            long minutes = seconds / 60;
            if (days > 0) return days + "d " + hours + "h";
            if (hours > 0) return hours + "h " + minutes + "m";
            return minutes + "m";
        }
        return String.valueOf((long) value);
    }

    private List<String> lines(String type) {
        List<OfflinePlayer> players = knownPlayers();
        players.sort((a, b) -> Double.compare(score(b, type), score(a, type)));
        List<String> out = new ArrayList<>();
        out.add("&6&lTOP " + (type.equalsIgnoreCase("kills") ? "KILLS" : prettyType(type).toUpperCase(Locale.ROOT)));
        int rank = 0;
        for (OfflinePlayer p : players) {
            double value = score(p, type);
            if (value <= 0) continue;
            rank++;
            out.add("&e" + rank + ". &f" + p.getName() + " &8- &b" + formatValue(p, type, value));
            if (rank >= 10) break;
        }
        if (rank == 0) out.add("&7No data found yet.");
        out.add("&8----------------------");
        return out;
    }

    private void refreshDisplays() {
        for (String key : new ArrayList<>(displays.keySet())) {
            String type = getConfig().getString("displays." + key + ".type", "kills");
            UUID id = displays.get(key);
            Entity entity = findEntity(id);
            if (entity instanceof TextDisplay td) {
                td.setText(lines(type).stream().map(this::color).collect(Collectors.joining("\n")));
            } else {
                String locText = getConfig().getString("displays." + key + ".location");
                Location loc = parseLocation(locText == null ? "" : locText);
                if (loc == null) { displays.remove(key); continue; }
                TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                    display.setPersistent(true);
                    display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                    display.setShadowed(true);
                    display.setSeeThrough(false);
                    display.setLineWidth(300);
                    display.setText(lines(type).stream().map(this::color).collect(Collectors.joining("\n")));
                });
                displays.put(key, td.getUniqueId());
                getConfig().set("displays." + key + ".entity", td.getUniqueId().toString());
                saveConfig();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(color("&a&lUniverse7Leaderboard &7commands:"));
            sender.sendMessage(color("&e/u7lb top <kills|deaths|playtime|money>"));
            sender.sendMessage(color("&e/u7lb create <kills|deaths|playtime|money>"));
            sender.sendMessage(color("&e/u7lb refresh"));
            sender.sendMessage(color("&e/u7lb remove <id>"));
            return true;
        }
        if (args[0].equalsIgnoreCase("top") && args.length >= 2) {
            String type = args[1].toLowerCase(Locale.ROOT);
            if (!validType(type)) { sender.sendMessage(color("&cUse kills, deaths, playtime, or money.")); return true; }
            lines(type).forEach(line -> sender.sendMessage(color(line)));
            return true;
        }
        if (args[0].equalsIgnoreCase("create") && args.length >= 2) {
            if (!(sender instanceof org.bukkit.entity.Player player)) { sender.sendMessage("Run this command in-game."); return true; }
            String type = args[1].toLowerCase(Locale.ROOT);
            if (!validType(type)) { sender.sendMessage(color("&cUse kills, deaths, playtime, or money.")); return true; }
            if (type.equals("money") && economy == null) {
                sender.sendMessage(color("&cVault economy provider not found. Check your economy plugin."));
                return true;
            }
            String id = type;
            if (displays.containsKey(id)) {
                Entity old = findEntity(displays.get(id));
                if (old != null) old.remove();
            }
            Location loc = player.getLocation().clone().add(0, 2.2, 0);
            TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                display.setPersistent(true);
                display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                display.setShadowed(true);
                display.setLineWidth(300);
                display.setText(lines(type).stream().map(this::color).collect(Collectors.joining("\n")));
            });
            displays.put(id, td.getUniqueId());
            getConfig().set("displays." + id + ".type", type);
            getConfig().set("displays." + id + ".entity", td.getUniqueId().toString());
            getConfig().set("displays." + id + ".location", serializeLocation(loc));
            saveConfig();
            sender.sendMessage(color("&aCreated the " + prettyType(type) + " hologram here."));
            return true;
        }
        if (args[0].equalsIgnoreCase("refresh")) {
            refreshDisplays();
            sender.sendMessage(color("&aLeaderboards refreshed."));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove") && args.length >= 2) {
            Entity e = findEntity(displays.remove(args[1].toLowerCase(Locale.ROOT)));
            if (e != null) e.remove();
            getConfig().set("displays." + args[1].toLowerCase(Locale.ROOT), null);
            saveConfig();
            sender.sendMessage(color("&aLeaderboard removed."));
            return true;
        }
        sender.sendMessage(color("&cUnknown command. Use /u7lb help"));
        return true;
    }

    private boolean validType(String type) {
        return Set.of("kills", "deaths", "playtime", "money").contains(type);
    }
}
