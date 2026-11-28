package com.upgradekey;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class UpgradeKeyPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> DEMORA_WEAPONS = List.of(
            "sculk_crossbow", "midas_sword", "mjolnir", "shadow_blade", "reaper_scythe",
            "villager_staff", "dragon_katana", "excalibur", "void_staff", "magma_club",
            "shrink_ray", "chainsaw_sword", "wither_sickles", "awakened_wither_sickles",
            "cloud_sword", "hypnosis_staff", "emerald_blade", "golem_hammer", "toxic_crossbow",
            "lich_staff", "artemis_bow", "poseidon_trident", "reinforced_elytra", "ravager_horn",
            "freezing_chakram", "sculkweavers_lantern", "war_pick", "beehive_blaster", "fugu_cannon",
            "harpoon_launcher", "awakened_dragon_katana", "rework_elytra", "elemental_staff", "admon_stick"
    );

    private final Map<UUID, Integer> tokens = new HashMap<>();
    private final Map<UUID, String> assignments = new HashMap<>();
    private final Set<UUID> pendingReward = new HashSet<>();
    private File playersFile;
    private YamlConfiguration playersData;
    private NamespacedKey rewardItemKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        rewardItemKey = new NamespacedKey(this, "demora_reward_item");
        loadPlayerData();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("upgradekey")).setExecutor(this);
        Objects.requireNonNull(getCommand("upgradekey")).setTabCompleter(this);
        getLogger().info("UpgradeKey enabled. Demora integration uses /demorahop give <weapon> <player>.");
    }

    @Override public void onDisable() { savePlayerData(); }

    private void loadPlayerData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        playersFile = new File(getDataFolder(), "players.yml");
        playersData = YamlConfiguration.loadConfiguration(playersFile);
        if (playersData.getConfigurationSection("players") != null) {
            for (String raw : Objects.requireNonNull(playersData.getConfigurationSection("players")).getKeys(false)) {
                try {
                    UUID id = UUID.fromString(raw);
                    tokens.put(id, Math.max(0, Math.min(maxTokens(), playersData.getInt("players." + raw + ".tokens", 0))));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (getConfig().getConfigurationSection("assigned-weapons") != null) {
            for (String raw : Objects.requireNonNull(getConfig().getConfigurationSection("assigned-weapons")).getKeys(false)) {
                try { assignments.put(UUID.fromString(raw), getConfig().getString("assigned-weapons." + raw, defaultWeapon())); }
                catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void savePlayerData() {
        if (playersData == null) return;
        for (Map.Entry<UUID, Integer> entry : tokens.entrySet())
            playersData.set("players." + entry.getKey() + ".tokens", entry.getValue());
        try { playersData.save(playersFile); } catch (IOException e) { getLogger().severe("Could not save players.yml: " + e.getMessage()); }
        for (Map.Entry<UUID, String> entry : assignments.entrySet())
            getConfig().set("assigned-weapons." + entry.getKey(), entry.getValue());
        saveConfig();
    }

    private int maxTokens() { return Math.max(1, getConfig().getInt("max-tokens", 5)); }
    private int getTokens(UUID id) { return tokens.getOrDefault(id, 0); }
    private String defaultWeapon() { return getConfig().getString("default-weapon", "mjolnir").toLowerCase(Locale.ROOT); }
    private String assignedWeapon(UUID id) { return assignments.getOrDefault(id, defaultWeapon()); }
    private String msg(String key) { return color(getConfig().getString("messages." + key, "")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void tell(CommandSender sender, String text) { sender.sendMessage(color(getConfig().getString("messages.prefix", "&8[&6UpgradeKey&8] &r")) + text); }

    @EventHandler public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        // A Demora reward is never left on the ground, regardless of how the owner died.
        event.getDrops().removeIf(this::isRewardItem);
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            int old = getTokens(victim.getUniqueId());
            int reduced = Math.max(0, old - 1);
            tokens.put(victim.getUniqueId(), reduced);
            tell(victim, msg("lost").replace("%killer%", killer.getName()).replace("%tokens%", String.valueOf(reduced)).replace("%max%", String.valueOf(maxTokens())));
            addToken(killer);
            savePlayerData();
        }
    }

    private void addToken(Player player) {
        UUID id = player.getUniqueId();
        int next = Math.min(maxTokens(), getTokens(id) + 1);
        tokens.put(id, next);
        tell(player, msg("kill").replace("%tokens%", String.valueOf(next)).replace("%max%", String.valueOf(maxTokens())));
        if (next >= maxTokens()) {
            tell(player, msg("max").replace("%max%", String.valueOf(maxTokens())));
            giveReward(player);
        }
        savePlayerData();
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            removeRewardItems(player);
            // Tokens are only reduced by player kills. If the player died environmentally
            // while at the cap, they qualify for their reward again after respawning.
            if (getTokens(player.getUniqueId()) >= maxTokens()) giveReward(player);
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (getTokens(player.getUniqueId()) >= maxTokens() && !hasRewardItem(player)) giveReward(player);
        }, 10L);
    }

    private boolean isRewardItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(rewardItemKey, PersistentDataType.STRING);
    }
    private boolean isRewardItem(ItemStack item, UUID owner) {
        if (!isRewardItem(item)) return false;
        return owner.toString().equals(item.getItemMeta().getPersistentDataContainer().get(rewardItemKey, PersistentDataType.STRING));
    }
    private boolean hasRewardItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isRewardItem(item, player.getUniqueId())) return true;
        return false;
    }
    private void removeRewardItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isRewardItem(item, player.getUniqueId())) player.getInventory().setItem(slot, null);
        }
    }

    private void giveReward(Player player) {
        if (!getConfig().getBoolean("demora.enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("DemoraLegendaryWeapons")) {
            pendingReward.add(player.getUniqueId());
            player.sendMessage(msg("weapon-pending"));
            return;
        }
        if (hasRewardItem(player)) return;
        boolean hasSpace = false;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot) == null || player.getInventory().getItem(slot).getType().isAir()) { hasSpace = true; break; }
        if (!hasSpace) {
            pendingReward.add(player.getUniqueId());
            player.sendMessage(msg("weapon-pending"));
            return;
        }

        String weapon = assignedWeapon(player.getUniqueId()).toLowerCase(Locale.ROOT);
        if (!DEMORA_WEAPONS.contains(weapon)) {
            getLogger().warning("Invalid Demora weapon id '" + weapon + "' assigned to " + player.getName());
            return;
        }
        ItemStack[] before = cloneContents(player.getInventory().getContents());
        String command = getConfig().getString("demora.command", "demorahop give %weapon% %player%")
                .replace("%weapon%", weapon).replace("%player%", player.getName());
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            getLogger().warning("Could not dispatch Demora command: " + command);
            pendingReward.add(player.getUniqueId());
            return;
        }
        ItemStack[] after = player.getInventory().getContents();
        boolean tagged = false;
        for (int slot = 0; slot < Math.min(36, after.length); slot++) {
            if (after[slot] != null && (before[slot] == null || !after[slot].isSimilar(before[slot]))) {
                ItemStack reward = after[slot].clone();
                ItemMeta meta = reward.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(rewardItemKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    reward.setItemMeta(meta);
                    player.getInventory().setItem(slot, reward);
                    tagged = true;
                    break;
                }
            }
        }
        if (tagged) pendingReward.remove(player.getUniqueId());
        else {
            pendingReward.add(player.getUniqueId());
            player.sendMessage(msg("weapon-pending"));
            getLogger().warning("Demora command ran, but UpgradeKey could not identify the newly added weapon for " + player.getName());
        }
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (isRewardItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isRewardItem(event.getMainHandItem()) || isRewardItem(event.getOffHandItem())) event.setCancelled(true);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            if (holder.type.equals("players")) {
                String targetId = clicked.getItemMeta() == null ? null : clicked.getItemMeta().getPersistentDataContainer().get(holderTargetKey(), PersistentDataType.STRING);
                if (targetId == null) return;
                Player target = Bukkit.getPlayer(UUID.fromString(targetId));
                if (target != null) openWeaponMenu(player, target);
            } else if (holder.type.equals("weapons")) {
                String weapon = clicked.getItemMeta() == null ? null : clicked.getItemMeta().getPersistentDataContainer().get(holderWeaponKey(), PersistentDataType.STRING);
                if (weapon != null) {
                    assignments.put(holder.target, weapon);
                    getConfig().set("assigned-weapons." + holder.target, weapon);
                    saveConfig();
                    Player target = Bukkit.getPlayer(holder.target);
                    tell(player, "&aAssigned &e" + weapon + " &ato " + (target == null ? holder.target : target.getName()) + ".");
                    if (target != null && getTokens(target.getUniqueId()) >= maxTokens()) {
                        removeRewardItems(target);
                        giveReward(target);
                    }
                    player.closeInventory();
                }
            }
            return;
        }
        boolean hotbarReward = event.getHotbarButton() >= 0 && isRewardItem(player.getInventory().getItem(event.getHotbarButton()));
        if (isRewardItem(event.getCurrentItem()) || isRewardItem(event.getCursor()) || hotbarReward) event.setCancelled(true);
        if (pendingReward.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (getTokens(player.getUniqueId()) >= maxTokens() && !hasRewardItem(player)) giveReward(player);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }
    @EventHandler public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isRewardItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (isRewardItem(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) { event.setCancelled(true); return; }
        if (isRewardItem(event.getOldCursor())) event.setCancelled(true);
    }

    private NamespacedKey holderTargetKey() { return new NamespacedKey(this, "menu_target"); }
    private NamespacedKey holderWeaponKey() { return new NamespacedKey(this, "menu_weapon"); }

    private void openPlayerMenu(Player admin) {
        MenuHolder holder = new MenuHolder("players", null);
        Inventory inv = Bukkit.createInventory(holder, 54, color("&8UpgradeKey: Select Player"));
        holder.inventory = inv;
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            meta.setDisplayName(color("&e" + target.getName()));
            meta.setLore(List.of(color("&7Tokens: &6+" + getTokens(target.getUniqueId()) + "/" + maxTokens()), color("&aClick to assign a weapon")));
            meta.getPersistentDataContainer().set(holderTargetKey(), PersistentDataType.STRING, target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        admin.openInventory(inv);
    }

    private void openWeaponMenu(Player admin, Player target) {
        MenuHolder holder = new MenuHolder("weapons", target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 54, color("&8Weapon for " + target.getName()));
        holder.inventory = inv;
        int slot = 0;
        for (String weapon : DEMORA_WEAPONS) {
            if (slot >= 45) break;
            Material icon = weapon.contains("bow") || weapon.contains("crossbow") ? Material.CROSSBOW
                    : weapon.contains("trident") ? Material.TRIDENT
                    : weapon.contains("staff") ? Material.BLAZE_ROD
                    : weapon.contains("elytra") ? Material.ELYTRA
                    : weapon.contains("scythe") || weapon.contains("sickle") ? Material.IRON_HOE
                    : Material.NETHERITE_SWORD;
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color("&6" + pretty(weapon)));
            meta.setLore(List.of(color("&7ID: &f" + weapon), color(weapon.equals(assignedWeapon(target.getUniqueId())) ? "&aCurrently assigned" : "&eClick to assign")));
            meta.getPersistentDataContainer().set(holderWeaponKey(), PersistentDataType.STRING, weapon);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        admin.openInventory(inv);
    }

    private String pretty(String id) { String[] parts = id.split("_"); StringJoiner join = new StringJoiner(" "); for (String p : parts) join.add(Character.toUpperCase(p.charAt(0)) + p.substring(1)); return join.toString(); }

    private void openTokensMenu(Player player) {
        MenuHolder holder = new MenuHolder("tokens", player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 9, color("&8Your Upgrade Tokens"));
        holder.inventory = inv;
        int count = getTokens(player.getUniqueId());
        for (int i = 0; i < maxTokens(); i++) {
            ItemStack key = new ItemStack(Material.TRIAL_KEY);
            ItemMeta meta = key.getItemMeta();
            meta.setDisplayName(color((i < count ? "&a" : "&7") + "Upgrade Token " + (i + 1)));
            meta.setLore(List.of(color(i < count ? "&aEquipped" : "&8Not earned yet")));
            key.setItemMeta(meta);
            inv.setItem(i + 2, key);
        }
        player.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player admin)) { tell(sender, "&cPlayers only."); return true; }
            if (!admin.hasPermission("upgradekey.admin")) { tell(sender, "&cYou do not have permission."); return true; }
            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "&cThat player must be online."); return true; }
                openWeaponMenu(admin, target);
            } else openPlayerMenu(admin);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "tokens" -> { if (sender instanceof Player player) openTokensMenu(player); else tell(sender, "&cPlayers only."); return true; }
            case "give", "set" -> {
                if (!sender.hasPermission("upgradekey.admin")) { tell(sender, "&cYou do not have permission."); return true; }
                if (args.length != 3) { tell(sender, "&cUsage: /upgradekey " + args[0] + " <player> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "&cPlayer must be online."); return true; }
                try {
                    int amount = Math.max(0, Math.min(maxTokens(), Integer.parseInt(args[2])));
                    int value = args[0].equalsIgnoreCase("give") ? Math.min(maxTokens(), getTokens(target.getUniqueId()) + amount) : amount;
                    tokens.put(target.getUniqueId(), value);
                    savePlayerData();
                    tell(sender, "&aSet " + target.getName() + " to +" + value + " tokens.");
                    if (value >= maxTokens() && !hasRewardItem(target)) giveReward(target);
                } catch (NumberFormatException e) { tell(sender, "&cAmount must be a number."); }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("upgradekey.admin")) { tell(sender, "&cYou do not have permission."); return true; }
                reloadConfig(); tell(sender, "&aConfiguration reloaded."); return true;
            }
            default -> { tell(sender, "&eCommands: /upgradekey menu, /upgradekey tokens, /upgradekey give <player> <amount>, /upgradekey set <player> <amount>, /upgradekey reload"); return true; }
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("menu", "tokens", "give", "set", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("menu")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return Collections.emptyList();
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String type; private final UUID target; private Inventory inventory;
        private MenuHolder(String type, UUID target) { this.type = type; this.target = target; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
