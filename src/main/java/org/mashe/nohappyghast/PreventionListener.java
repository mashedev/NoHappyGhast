package org.mashe.nohappyghast;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PreventionListener implements Listener {
    private final NoHappyGhastPlugin plugin;
    private final Map<UUID, Long> lastMessageAtMillis = new ConcurrentHashMap<>();

    PreventionListener(NoHappyGhastPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEntitySpawns() || !settings.isWorldEnabled(event.getLocation().getWorld())) {
            return;
        }

        String entityType = event.getEntityType().name().toLowerCase(Locale.ROOT);
        if (findMatch(entityType, settings.blockedEntityKeywords()).isEmpty()) {
            return;
        }

        event.setCancelled(true);
        if (settings.debugSpawnLog()) {
            String line = settings.blockedSpawnLogMessage().replace("{target}", entityType);
            plugin.getLogger().info(stripColor(plugin.colorize(line)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockItemUse() || event.getHand() != EquipmentSlot.HAND || !settings.isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }

        Optional<String> matched = matchItem(event.getItem(), settings.blockedItemKeywords(), settings.blockedEntityKeywords());
        if (matched.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        sendPlayerMessage(event.getPlayer(), settings.blockedItemUseMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        blockEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        blockEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        blockEntityInteraction(event.getPlayer(), event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEntityInteractions() || !settings.isWorldEnabled(event.getMount().getWorld())) {
            return;
        }

        if (event.getEntity() instanceof Player player && hasBypass(player)) {
            return;
        }

        boolean mountBlocked = isBlockedEntity(event.getMount(), settings.blockedEntityKeywords());
        boolean riderBlocked = isBlockedEntity(event.getEntity(), settings.blockedEntityKeywords());
        if (!mountBlocked && !riderBlocked) {
            return;
        }

        if (event.getEntity() instanceof Player player && !hasBypass(player)) {
            sendPlayerMessage(player, settings.blockedInteractionMessage());
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEntityInteractions() || !settings.isWorldEnabled(event.getEntity().getWorld())) {
            return;
        }

        if (event.getDamager() instanceof Player player && hasBypass(player)) {
            return;
        }

        boolean damagerBlocked = isBlockedEntity(event.getDamager(), settings.blockedEntityKeywords());
        boolean victimBlocked = isBlockedEntity(event.getEntity(), settings.blockedEntityKeywords());
        if (!damagerBlocked && !victimBlocked) {
            return;
        }

        if (event.getDamager() instanceof Player player && !hasBypass(player)) {
            sendPlayerMessage(player, settings.blockedInteractionMessage());
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.purgeExistingEntities() || !settings.isWorldEnabled(event.getWorld())) {
            return;
        }

        for (Entity entity : event.getChunk().getEntities()) {
            if (isBlockedEntity(entity, settings.blockedEntityKeywords())) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockPlacements() || !settings.isWorldEnabled(event.getBlockPlaced().getWorld())) {
            return;
        }

        String placedType = event.getBlockPlaced().getType().name().toLowerCase(Locale.ROOT);
        Optional<String> matched = findMatch(placedType, settings.blockedItemKeywords());
        if (matched.isEmpty()) {
            matched = matchItem(event.getItemInHand(), settings.blockedItemKeywords(), settings.blockedEntityKeywords());
        }
        if (matched.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        sendPlayerMessage(event.getPlayer(), settings.blockedPlacementMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockBreaks() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        if (settings.netherOnlyBreakProtection() && event.getBlock().getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) {
            return;
        }

        if (hasBypass(event.getPlayer())) {
            return;
        }

        if (!isProtectedBlock(event.getBlock(), settings)) {
            return;
        }

        event.setCancelled(true);
        sendPlayerMessage(event.getPlayer(), settings.blockedBreakMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockPistonInteractions() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (isProtectedBlock(block, settings)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockPistonInteractions() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (isProtectedBlock(block, settings)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockExplosions() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        event.blockList().removeIf(block -> isProtectedBlock(block, settings));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockExplosions() || !settings.isWorldEnabled(event.getLocation().getWorld())) {
            return;
        }

        event.blockList().removeIf(block -> isProtectedBlock(block, settings));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEnvironmentDamage() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        if (isProtectedBlock(event.getBlock(), settings)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEnvironmentDamage() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        if (isProtectedBlock(event.getBlock(), settings)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEnvironmentDamage() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        if (isProtectedBlock(event.getBlock(), settings)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockItemUse() || !settings.isWorldEnabled(event.getBlock().getWorld())) {
            return;
        }

        Optional<String> matched = matchItem(event.getItem(), settings.blockedItemKeywords(), settings.blockedEntityKeywords());
        if (matched.isEmpty()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockCommands() || hasBypass(event.getPlayer()) || !settings.isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }

        if (!isBlockedSummon(event.getMessage(), settings.blockedEntityKeywords())) {
            return;
        }

        event.setCancelled(true);
        sendPlayerMessage(event.getPlayer(), settings.blockedCommandMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockCommands() || !settings.allWorldsEnabled()) {
            return;
        }

        CommandSender sender = event.getSender();
        if (hasBypass(sender)) {
            return;
        }

        if (isBlockedSummon(event.getCommand(), settings.blockedEntityKeywords())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastMessageAtMillis.remove(event.getPlayer().getUniqueId());
    }

    private void blockEntityInteraction(Player player, Entity entity, Cancellable event) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.blockEntityInteractions() || hasBypass(player) || !settings.isWorldEnabled(entity.getWorld())) {
            return;
        }

        if (!isBlockedEntity(entity, settings.blockedEntityKeywords())) {
            return;
        }

        event.setCancelled(true);
        sendPlayerMessage(player, settings.blockedInteractionMessage());
    }

    private void sendPlayerMessage(Player player, String body) {
        NoHappyGhastPlugin.Settings settings = plugin.settings();
        if (!settings.notifyPlayerOnBlock() || body == null || body.isBlank()) {
            return;
        }

        long cooldownMillis = settings.notifyCooldownMillis();
        if (cooldownMillis > 0L) {
            long now = System.currentTimeMillis();
            UUID uuid = player.getUniqueId();
            long last = lastMessageAtMillis.getOrDefault(uuid, 0L);
            if ((now - last) < cooldownMillis) {
                return;
            }
            lastMessageAtMillis.put(uuid, now);
        }

        String full = plugin.colorize(settings.prefix() + body);
        player.sendMessage(full);
    }

    private boolean hasBypass(CommandSender sender) {
        String node = plugin.settings().bypassPermission();
        return node != null && !node.isBlank() && sender.hasPermission(node);
    }

    private static Optional<String> matchItem(ItemStack item, Set<String> primaryKeywords, Set<String> fallbackKeywords) {
        if (item == null) {
            return Optional.empty();
        }

        String material = item.getType().name().toLowerCase(Locale.ROOT);
        Optional<String> matched = findMatch(material, primaryKeywords);
        return matched.isPresent() ? matched : findMatch(material, fallbackKeywords);
    }

    private static Optional<String> findMatch(String value, Set<String> keywords) {
        if (value == null || keywords.isEmpty()) {
            return Optional.empty();
        }

        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return Optional.of(keyword);
            }
        }
        return Optional.empty();
    }

    private static boolean isBlockedEntity(Entity entity, Set<String> blockedEntityKeywords) {
        if (entity == null) {
            return false;
        }

        String type = entity.getType().name().toLowerCase(Locale.ROOT);
        return findMatch(type, blockedEntityKeywords).isPresent();
    }

    private static boolean isProtectedBlock(Block block, NoHappyGhastPlugin.Settings settings) {
        String blockType = block.getType().name().toLowerCase(Locale.ROOT);
        return findMatch(blockType, settings.blockedItemKeywords()).isPresent();
    }

    private static boolean isBlockedSummon(String command, Set<String> blockedEntityKeywords) {
        if (command == null || command.isBlank()) {
            return false;
        }

        String normalized = command.startsWith("/") ? command.substring(1).trim() : command.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("\\s+");
        if (parts.length >= 2 && stripNamespace(parts[0]).equals("summon")) {
            String entity = stripNamespace(parts[1]);
            return findMatch(entity, blockedEntityKeywords).isPresent();
        }

        if (!lower.contains("summon")) {
            return false;
        }

        for (String keyword : blockedEntityKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespace(String value) {
        int idx = value.lastIndexOf(':');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static String stripColor(String value) {
        return ChatColor.stripColor(value);
    }
}
