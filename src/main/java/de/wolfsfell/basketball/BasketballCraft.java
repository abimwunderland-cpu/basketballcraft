package de.wolfsfell.basketball;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.*;

public final class BasketballCraft extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey key;
    private ItemDisplay display;
    private Location ball;
    private Vector velocity = new Vector();
    private UUID carrier;
    private UUID lastShooter;
    private Location shotOrigin;
    private boolean airborne;
    private final Map<String, Location> hoops = new HashMap<>();
    private final Map<String, Integer> scores = new HashMap<>();
    private BukkitTask ticker;
    private int tick;

    @Override public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "basketball");
        Objects.requireNonNull(getCommand("basketball")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String side : List.of("red", "blue")) {
            String base = "hoops." + side;
            if (getConfig().contains(base + ".world")) {
                World world = Bukkit.getWorld(getConfig().getString(base + ".world", ""));
                if (world != null) hoops.put(side, new Location(world, getConfig().getDouble(base + ".x"), getConfig().getDouble(base + ".y"), getConfig().getDouble(base + ".z")));
            }
            scores.put(side, 0);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::step, 1L, 1L);
        getLogger().info("BasketballCraft 0.1 enabled. /bc help");
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (display != null && display.isValid()) display.remove();
    }
    private double setting(String name, double fallback) { return getConfig().getDouble("physics." + name, fallback); }
    private ItemStack ballItem() {
        ItemStack item = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lBasketball §7[Linksklick: Wurf | Shift+Linksklick: Pass]");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isBall(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    private void removeBallItems(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (isBall(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
        }
    }
    private void spawnBall(Location location) {
        clearBall();
        ball = location.clone();
        ball.getWorld().getChunkAt(ball).load();
        display = ball.getWorld().spawn(ball, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.ORANGE_CONCRETE));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(-0.23f, -0.23f, -0.23f), new AxisAngle4f(), new Vector3f(0.46f, 0.46f, 0.46f), new AxisAngle4f()));
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(1);
            entity.setPersistent(false);
        });
        velocity.zero(); airborne = false; lastShooter = null; shotOrigin = null;
    }
    private void clearBall() {
        if (carrier != null) {
            Player p = Bukkit.getPlayer(carrier);
            if (p != null) removeBallItems(p);
        }
        carrier = null;
        if (display != null && display.isValid()) display.remove();
        display = null; ball = null; airborne = false;
    }
    private void carry(Player p) {
        if (carrier != null || ball == null || !p.getWorld().equals(ball.getWorld())) return;
        carrier = p.getUniqueId();
        p.getInventory().addItem(ballItem()).forEach((slot, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.sendActionBar("§6🏀 Ballbesitz §7| §fLinksklick: Wurf §7| §fShift+Linksklick: Pass");
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
    }
    private void release(Player p, boolean pass) {
        if (!p.getUniqueId().equals(carrier)) return;
        removeBallItems(p);
        carrier = null;
        ball = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.65));
        Vector direction = p.getEyeLocation().getDirection().normalize();
        double speed = pass ? setting("pass-speed", 0.87) : setting("shoot-speed", 1.16);
        velocity = direction.multiply(speed);
        if (!pass) velocity.setY(velocity.getY() + 0.13);
        lastShooter = p.getUniqueId();
        shotOrigin = p.getLocation().clone();
        airborne = true;
        if (display != null && display.isValid()) display.teleport(ball);
        p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 1.0f, pass ? 1.25f : 0.8f);
    }
    @EventHandler(priority = EventPriority.HIGH) public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Action action = event.getAction();
        if (p.getUniqueId().equals(carrier) && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            release(p, p.isSneaking());
            return;
        }
        if (p.getUniqueId().equals(carrier) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            return;
        }
        if (carrier == null && ball != null && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && p.getWorld().equals(ball.getWorld()) && p.getLocation().distanceSquared(ball) < 6.25) {
            event.setCancelled(true);
            carry(p);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { dropCarrier(event.getPlayer()); }
    @EventHandler public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getEntity().getUniqueId().equals(carrier)) {
            event.getDrops().removeIf(this::isBall);
            dropCarrier(event.getEntity());
        }
    }
    @EventHandler public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (isBall(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            if (event.getPlayer().getUniqueId().equals(carrier)) release(event.getPlayer(), true);
        }
    }
    private void dropCarrier(Player p) {
        if (!p.getUniqueId().equals(carrier)) return;
        removeBallItems(p);
        carrier = null;
        if (ball != null) {
            ball = p.getLocation().clone().add(0, 0.6, 0);
            velocity.zero(); airborne = true;
        }
    }
    private void step() {
        tick++;
        if (ball == null || display == null || !display.isValid()) return;
        if (carrier != null) {
            Player p = Bukkit.getPlayer(carrier);
            if (p == null || !p.isOnline() || !p.getWorld().equals(ball.getWorld())) {
                if (p != null) dropCarrier(p); else carrier = null;
                return;
            }
            Vector forward = p.getLocation().getDirection().setY(0);
            if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
            forward.normalize();
            ball = p.getLocation().add(forward.multiply(0.7)).add(0, 0.28 + Math.abs(Math.sin(tick * 0.48)) * 0.48, 0);
            if (tick % 10 == 0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOOD_HIT, 0.25f, 0.55f);
            display.teleport(ball);
            return;
        }
        if (!airborne) {
            for (Player p : ball.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(ball) <= Math.pow(setting("pickup-radius", 1.4), 2)) { carry(p); break; }
            }
            return;
        }
        Location previous = ball.clone();
        velocity.multiply(setting("drag", 0.992));
        velocity.setY(velocity.getY() - setting("gravity", 0.045));
        Vector movement = velocity.clone();
        int parts = Math.max(1, (int)Math.ceil(movement.length() / 0.22));
        for (int i = 0; i < parts; i++) {
            Location next = ball.clone().add(movement.clone().multiply(1.0 / parts));
            if (next.getBlock().getType().isSolid()) {
                boolean floor = movement.getY() <= 0 && ball.clone().add(0, -0.12, 0).getBlock().getType().isSolid();
                if (floor || (velocity.getY() < 0 && !ball.clone().add(0, -0.12, 0).getBlock().getType().isSolid())) {
                    velocity.setY(Math.abs(velocity.getY()) * setting("bounce", 0.67));
                    velocity.setX(velocity.getX() * setting("floor-friction", 0.78));
                    velocity.setZ(velocity.getZ() * setting("floor-friction", 0.78));
                } else {
                    velocity.setX(-velocity.getX() * 0.6);
                    velocity.setZ(-velocity.getZ() * 0.6);
                }
                break;
            }
            ball = next;
        }
        for (var entry : hoops.entrySet()) {
            Location hoop = entry.getValue();
            if (!hoop.getWorld().equals(ball.getWorld())) continue;
            // Hoop marker is the CENTER of the horizontal scoring plane, not the backboard.
            if (previous.getY() > hoop.getY() && ball.getY() <= hoop.getY() && velocity.getY() < 0) {
                double x = ball.getX() - hoop.getX();
                double z = ball.getZ() - hoop.getZ();
                if (x*x + z*z <= 0.55*0.55) { score(entry.getKey()); return; }
            }
        }
        if (ball.getY() < ball.getWorld().getMinHeight() + 2) { spawnBall(ball.getWorld().getSpawnLocation().add(0, 1, 0)); return; }
        if (velocity.lengthSquared() < 0.0009 && ball.clone().add(0, -0.16, 0).getBlock().getType().isSolid()) {
            velocity.zero(); airborne = false;
        }
        display.teleport(ball);
        if (tick % 3 == 0) {
            for (Player p : ball.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(ball) < 2.25 && velocity.lengthSquared() < 0.12) { carry(p); break; }
            }
        }
    }
    private void score(String side) {
        int points = 2;
        Location hoop = hoops.get(side);
        if (shotOrigin != null && shotOrigin.getWorld().equals(hoop.getWorld()) && shotOrigin.distanceSquared(hoop) >= Math.pow(getConfig().getDouble("court.three-point-distance", 8.0), 2)) points = 3;
        scores.merge(side, points, Integer::sum);
        String scorer = lastShooter == null ? "Unbekannt" : Optional.ofNullable(Bukkit.getOfflinePlayer(lastShooter).getName()).orElse("Spieler");
        Bukkit.broadcastMessage("§6§l🏀 KORB! §f" + scorer + " §7(" + points + " Punkte) §cROT " + scores.get("red") + " §7- §9" + scores.get("blue") + " BLAU");
        hoop.getWorld().playSound(hoop, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        spawnBall(hoop.clone().add(0, -1.8, 0));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6§lBasketballCraft v0.1 §7| /bc ball, /bc sethoop red|blue, /bc start, /bc reset, /bc score");
            sender.sendMessage("§7Ball aufnehmen: Nähe oder Rechtsklick. §fLinksklick: Wurf §7| §fShift+Linksklick: Pass");
            return true;
        }
        if (args[0].equalsIgnoreCase("score")) {
            sender.sendMessage("§cROT §f" + scores.get("red") + " §7- §f" + scores.get("blue") + " §9BLAU");
            return true;
        }
        if (!sender.hasPermission("basketball.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can configure the court."); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "ball", "start" -> { spawnBall(p.getLocation().add(0, 1.0, 0)); p.sendMessage("§aBasketball gespawnt!"); }
            case "sethoop" -> {
                if (args.length < 2 || !List.of("red", "blue").contains(args[1].toLowerCase(Locale.ROOT))) { p.sendMessage("§c/bc sethoop red|blue"); return true; }
                String side = args[1].toLowerCase(Locale.ROOT);
                // Set at the player's eye position: stand/fly at the CENTER of the hoop.
                Location loc = p.getEyeLocation();
                hoops.put(side, loc);
                String base = "hoops." + side;
                getConfig().set(base + ".world", loc.getWorld().getName());
                getConfig().set(base + ".x", loc.getX());
                getConfig().set(base + ".y", loc.getY());
                getConfig().set(base + ".z", loc.getZ());
                saveConfig();
                p.sendMessage("§a" + side + " hoop center set at your EYE position (" + String.format(Locale.ROOT, "%.2f %.2f %.2f", loc.getX(), loc.getY(), loc.getZ()) + ").");
            }
            case "reset" -> { scores.put("red", 0); scores.put("blue", 0); spawnBall(p.getLocation().add(0, 1, 0)); p.sendMessage("§aSpielstand zurückgesetzt."); }
            default -> p.sendMessage("§cUnbekannter Befehl. /bc help");
        }
        return true;
    }
}
