package org.mashe.nohappyghast;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class NoHappyGhastPlugin extends JavaPlugin {
    private volatile Settings settings;
    private boolean folia;

    @Override
    public void onEnable() {
        this.folia = isFoliaServer();
        saveDefaultConfig();
        reloadPluginSettings();
        getServer().getPluginManager().registerEvents(new PreventionListener(this), this);
        if (folia) {
            getLogger().info("Folia detected: skipping startup entity purge; chunk-based purge remains active.");
        } else {
            purgeLoadedEntities();
        }
    }

    void reloadPluginSettings() {
        reloadConfig();
        this.settings = Settings.from(getConfig());
    }

    Settings settings() {
        return settings;
    }

    boolean isFolia() {
        return folia;
    }

    String colorize(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    void purgeLoadedEntities() {
        Settings local = settings;
        if (local == null || !local.purgeExistingEntities()) {
            return;
        }

        for (org.bukkit.World world : getServer().getWorlds()) {
            if (!local.isWorldEnabled(world)) {
                continue;
            }
            for (Entity entity : world.getEntities()) {
                String type = entity.getType().name().toLowerCase(Locale.ROOT);
                for (String keyword : local.blockedEntityKeywords()) {
                    if (type.contains(keyword)) {
                        entity.remove();
                        break;
                    }
                }
            }
        }
    }

    record Settings(
            boolean blockEntitySpawns,
            boolean blockItemUse,
            boolean blockPlacements,
            boolean blockBreaks,
            boolean netherOnlyBreakProtection,
            boolean blockPistonInteractions,
            boolean blockExplosions,
            boolean blockEnvironmentDamage,
            boolean blockCommands,
            boolean blockEntityInteractions,
            boolean purgeExistingEntities,
            boolean notifyPlayerOnBlock,
            long notifyCooldownMillis,
            boolean debugSpawnLog,
            String bypassPermission,
            Set<String> enabledWorlds,
            Set<String> blockedEntityKeywords,
            Set<String> blockedItemKeywords,
            String prefix,
            String blockedItemUseMessage,
            String blockedPlacementMessage,
            String blockedBreakMessage,
            String blockedCommandMessage,
            String blockedInteractionMessage,
            String blockedSpawnLogMessage
    ) {
        static Settings from(FileConfiguration cfg) {
            Set<String> enabledWorlds = normalize(cfg.getStringList("settings.enabled-worlds"));
            if (enabledWorlds.isEmpty()) {
                enabledWorlds = new LinkedHashSet<>();
                enabledWorlds.add("all");
            }

            return new Settings(
                    cfg.getBoolean("settings.block-entity-spawns", true),
                    cfg.getBoolean("settings.block-item-use", true),
                    cfg.getBoolean("settings.block-placements", true),
                    cfg.getBoolean("settings.block-breaks", true),
                    cfg.getBoolean("settings.nether-only-break-protection", true),
                    cfg.getBoolean("settings.block-piston-interactions", true),
                    cfg.getBoolean("settings.block-explosions", true),
                    cfg.getBoolean("settings.block-environment-damage", true),
                    cfg.getBoolean("settings.block-commands", true),
                    cfg.getBoolean("settings.block-entity-interactions", true),
                    cfg.getBoolean("settings.purge-existing-entities", true),
                    cfg.getBoolean("settings.notify-player-on-block", true),
                    Math.max(0L, cfg.getLong("settings.notify-cooldown-millis", 1200L)),
                    cfg.getBoolean("settings.debug-spawn-log", false),
                    cfg.getString("settings.bypass-permission", "nohappyghast.bypass"),
                    enabledWorlds,
                    normalize(cfg.getStringList("settings.blocked-entity-keywords")),
                    normalize(cfg.getStringList("settings.blocked-item-keywords")),
                    cfg.getString("messages.prefix", "&c[NoHappyGhast] &7"),
                    cfg.getString("messages.blocked-item-use", "You cannot use that item here."),
                    cfg.getString("messages.blocked-placement", "You cannot place that item/block here."),
                    cfg.getString("messages.blocked-break", "You cannot break that block."),
                    cfg.getString("messages.blocked-command", "That command is blocked by server settings."),
                    cfg.getString("messages.blocked-interaction", "You cannot interact with that entity."),
                    cfg.getString("messages.blocked-spawn-log", "Blocked spawn of {target}.")
            );
        }

        boolean isWorldEnabled(World world) {
            if (world == null || enabledWorlds.isEmpty()) {
                return false;
            }
            if (enabledWorlds.contains("all")) {
                return true;
            }
            return enabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
        }

        boolean allWorldsEnabled() {
            return enabledWorlds.contains("all");
        }

        private static Set<String> normalize(List<String> raw) {
            return raw.stream()
                    .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
