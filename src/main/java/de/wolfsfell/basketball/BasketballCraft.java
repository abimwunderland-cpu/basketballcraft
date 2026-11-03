package de.wolfsfell.basketball;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.*;

public final class BasketballCraft extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey key;
    private ItemDisplay display;
    private Location ball;
    private Vector velocity = new Vector();
    private UUID carrier;
    private UUID lastShooter;
    private Location shotOrigin;
    private boolean scoredThisFlight;
    private boolean airborne;
    private final Map<String, Location> hoops = new HashMap<>();
    private final Map<String, Integer> scores = new HashMap<>();
    private BukkitTask ticker;
    private int tick;
    private final Set<UUID> hoopViewers = new HashSet<>();
    private final double hoopRadius = 0.92;
    private final Map<UUID, Long> charging = new HashMap<>();
    private final Map<UUID, Integer> chargeTicks = new HashMap<>();
    private UUID pickupLockedPlayer;
    private int pickupLockedUntil;
    private UUID intendedReceiver;
    private int intendedReceiverUntil;
    private boolean passFlight;
    private final Map<UUID, Integer> passCooldown = new HashMap<>();
    private final Map<UUID, Double> stamina = new HashMap<>();
    private final Map<UUID, Integer> sprintUnlock = new HashMap<>();
    private final Map<UUID, Integer> jumpWindow = new HashMap<>();
    private final Map<UUID, Boolean> grounded = new HashMap<>();
    private final Map<UUID, Integer> sprintRecent = new HashMap<>();
    private UUID dunker;
    private String dunkSide;
    private int dunkEnds;
    private int dunkReadyTick;
    private static final int SHOT_CYCLE = 44;
    private static final int GREEN_CENTER = 26;
    private final Map<UUID, Location> arenaPos1 = new HashMap<>();
    private final Map<UUID, Location> arenaPos2 = new HashMap<>();
    private final Map<String, Location[]> arenas = new HashMap<>();
    private String activeArena = null;
    private final Map<String, List<Location>> threeLines = new HashMap<>();
    private final Map<UUID, String> editMode = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.Inventory> setupInventories = new HashMap<>();
    private final Map<UUID, Float> baseWalkSpeed = new HashMap<>();
    private final NamespacedKey wandKey = new NamespacedKey(this, "wand");
    private int greenWidth(Player p) {
        double e = energy(p);
        return e >= 70 ? 6 : e >= 40 ? 5 : e >= 20 ? 4 : 3;
    }
    private int greenStart(Player p) { return GREEN_CENTER - (greenWidth(p) - 1) / 2; }
    private int greenEnd(Player p) { return greenStart(p) + greenWidth(p) - 1; }
    private boolean green(Player p, int phase) { return phase >= greenStart(p) && phase <= greenEnd(p); }
    private ItemStack wand(String type) {
        ItemStack item = new ItemStack(type.equals("arena") ? Material.WOODEN_AXE : type.equals("red") ? Material.BLAZE_ROD : type.equals("blue") ? Material.STICK : Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.equals("arena") ? "§d§lHallen-Wand" : type.equals("red") ? "§c§lKorb Team 1" : type.equals("blue") ? "§9§lKorb Team 2" : "§e§lCourt-Editor");
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta); return item;
    }
    private String wandType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.STRING);
    }
    private void saveHoop(String side, Location loc) {
        hoops.put(side, loc);
        String base = "hoops." + side;
        getConfig().set(base + ".world", loc.getWorld().getName());
        getConfig().set(base + ".x", loc.getX()); getConfig().set(base + ".y", loc.getY()); getConfig().set(base + ".z", loc.getZ());
        saveConfig();
    }
    private void saveArena(String name, Location a, Location b) {
        String base = "arenas." + name;
        getConfig().set(base + ".world", a.getWorld().getName());
        for (int i = 0; i < 2; i++) {
            Location loc = i == 0 ? a : b;
            getConfig().set(base + ".pos" + (i+1), List.of(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }
        getConfig().set("active-arena", name); saveConfig();
        arenas.put(name, new Location[]{a.clone(), b.clone()}); activeArena = name;
    }
    private boolean insideArena(Location loc) {
        if (activeArena == null || !arenas.containsKey(activeArena)) return true;
        Location[] corners = arenas.get(activeArena);
        if (!loc.getWorld().equals(corners[0].getWorld())) return false;
        return loc.getX() >= Math.min(corners[0].getBlockX(), corners[1].getBlockX()) && loc.getX() < Math.max(corners[0].getBlockX(), corners[1].getBlockX()) + 1
            && loc.getY() >= Math.min(corners[0].getBlockY(), corners[1].getBlockY()) && loc.getY() < Math.max(corners[0].getBlockY(), corners[1].getBlockY()) + 1
            && loc.getZ() >= Math.min(corners[0].getBlockZ(), corners[1].getBlockZ()) && loc.getZ() < Math.max(corners[0].getBlockZ(), corners[1].getBlockZ()) + 1;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "basketball");
        Objects.requireNonNull(getCommand("basketball")).setExecutor(this);
        Objects.requireNonNull(getCommand("basketball")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String side : List.of("red", "blue")) {
            String base = "hoops." + side;
            if (getConfig().contains(base + ".world")) {
                World world = Bukkit.getWorld(getConfig().getString(base + ".world", ""));
                if (world != null) hoops.put(side, new Location(world, getConfig().getDouble(base + ".x"), getConfig().getDouble(base + ".y"), getConfig().getDouble(base + ".z")));
            }
            scores.put(side, 0);
        }
        ConfigurationSection arenaSection = getConfig().getConfigurationSection("arenas");
        if (arenaSection != null) for (String name : arenaSection.getKeys(false)) {
            String base = "arenas." + name;
            World w = Bukkit.getWorld(getConfig().getString(base + ".world", ""));
            List<Integer> a = getConfig().getIntegerList(base + ".pos1"), b = getConfig().getIntegerList(base + ".pos2");
            if (w != null && a.size() == 3 && b.size() == 3)
                arenas.put(name, new Location[]{new Location(w,a.get(0),a.get(1),a.get(2)),new Location(w,b.get(0),b.get(1),b.get(2))});
        }
        activeArena = getConfig().getString("active-arena");
        for (String side : List.of("red", "blue")) {
            List<Location> points = new ArrayList<>();
            for (Map<?, ?> row : getConfig().getMapList("three-lines." + side)) {
                World w = Bukkit.getWorld(String.valueOf(row.get("world")));
                if (w == null) continue;
                try { points.add(new Location(w, ((Number)row.get("x")).doubleValue(), ((Number)row.get("y")).doubleValue(), ((Number)row.get("z")).doubleValue())); }
                catch (Exception ignored) { }
            }
            threeLines.put(side, points);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::step, 1L, 1L);
        getLogger().info("BasketballCraft 0.8.2 enabled. /bc help");
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (display != null && display.isValid()) display.remove();
        charging.clear(); chargeTicks.clear(); stamina.clear();
        for (Player p : Bukkit.getOnlinePlayers()) restoreSpeed(p);
    }
    private double setting(String name, double fallback) { return getConfig().getDouble("physics." + name, fallback); }
    private ItemStack ballItem() {
        ItemStack item = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5§lBasketballCraft §7[Visual]");
        meta.setItemModel(new NamespacedKey("basketballcraft", "basketball"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isBall(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    private void spawnBall(Location location) {
        clearBall();
        ball = location.clone();
        ball.getWorld().getChunkAt(ball).load();
        display = ball.getWorld().spawn(ball, ItemDisplay.class, entity -> {
            entity.setItemStack(ballItem());
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(-0.265f, -0.265f, -0.265f), new AxisAngle4f(), new Vector3f(0.53f, 0.53f, 0.53f), new AxisAngle4f()));
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(1);
            entity.setPersistent(false);
        });
        velocity.zero(); airborne = false; scoredThisFlight = false; lastShooter = null; shotOrigin = null; charging.clear(); chargeTicks.clear(); pickupLockedPlayer = null; intendedReceiver = null; passFlight = false;
    }
    private void clearBall() {
        dunker = null; dunkSide = null;

        carrier = null; charging.clear(); chargeTicks.clear();
        if (display != null && display.isValid()) display.remove();
        display = null; ball = null; airborne = false;
    }
    private void carry(Player p) {
        if (carrier != null || ball == null || !p.getWorld().equals(ball.getWorld())) return;
        if (p.getUniqueId().equals(pickupLockedPlayer) && tick < pickupLockedUntil) return;
        carrier = p.getUniqueId();
        airborne = false; velocity.zero(); passFlight = false; intendedReceiver = null;
        charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
        p.sendActionBar("§d🏀 Ballbesitz §7| §fLinksklick: Wurf §7| §fRechtsklick: Smart-Pass §7| §fShift+Rechtsklick: Ablegen §7| §dSprint+Sprung+Linksklick: Dunk");
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
    }
    private void startCharge(Player p) {
        if (!p.getUniqueId().equals(carrier)) return;
        charging.put(p.getUniqueId(), (long) tick);
        chargeTicks.put(p.getUniqueId(), 0);
        p.sendMessage("§eWurf geladen! §7Nochmals §fLinksklick §7zum Release. §aGRÜN §7= perfektes Timing.");
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.7f);
    }
    private Player smartTarget(Player p) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        Player best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double range = getConfig().getDouble("passing.assist-range", 16.0);
        double maxAngle = Math.toRadians(getConfig().getDouble("passing.assist-angle-degrees", 12.0));
        for (Player other : p.getWorld().getPlayers()) {
            if (other.equals(p) || other.isDead() || other.getGameMode() == GameMode.SPECTATOR) continue;
            Vector to = other.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());
            double distance = to.length();
            if (distance < 1.5 || distance > range) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, look.dot(to.normalize()))));
            if (angle > maxAngle) continue;
            // No team system yet: a player inside the aim cone is the intended receiver.
            double score = angle * 9 + distance * 0.018;
            if (score < bestScore) { bestScore = score; best = other; }
        }
        return best;
    }
    private double energy(Player p) { return stamina.getOrDefault(p.getUniqueId(), 100.0); }
    private boolean spend(Player p, double cost) {
        if (energy(p) < cost) { p.sendActionBar("§cZu wenig Ausdauer!"); return false; }
        stamina.put(p.getUniqueId(), energy(p) - cost); return true;
    }
    private boolean attemptDunk(Player p) {
        if (!p.getUniqueId().equals(carrier) || dunker != null) return false;
        if (tick > jumpWindow.getOrDefault(p.getUniqueId(), -100) ||
                tick > sprintRecent.getOrDefault(p.getUniqueId(), -100) + 12 || energy(p) < 70) return false;
        Location chosen = null; String side = null;
        Vector forward = p.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.01) return false;
        forward.normalize();
        double best = Double.MAX_VALUE;
        for (var entry : hoops.entrySet()) {
            Location hoop = entry.getValue();
            if (!p.getWorld().equals(hoop.getWorld())) continue;
            Vector delta = hoop.toVector().subtract(p.getLocation().toVector()).setY(0);
            double distance = delta.length();
            if (distance < 0.6 || distance > getConfig().getDouble("dunk.max-distance", 3.5)) continue;
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward.dot(delta.clone().normalize())))));
            if (angle > getConfig().getDouble("dunk.max-angle-degrees", 38)) continue;
            if (distance < best) { best = distance; chosen = hoop; side = entry.getKey(); }
        }
        if (chosen == null) return false;
        if (!spend(p, getConfig().getDouble("dunk.cost", 35))) return false;
        dunker = p.getUniqueId(); dunkSide = side;
        dunkEnds = tick + 28; dunkReadyTick = tick + 5;
        charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
        // Accelerate toward the hoop, and lift enough to reach the ring.
        Vector toward = chosen.toVector().subtract(p.getLocation().toVector()).setY(0);
        double horizontal = Math.min(0.39, Math.max(0.12, toward.length() / 13.0));
        toward.normalize().multiply(horizontal);
        double height = chosen.getY() - p.getLocation().getY();
        double vertical = Math.max(0.66, Math.min(0.96, 0.53 + height * 0.072));
        p.setVelocity(new Vector(toward.getX(), vertical, toward.getZ()));
        p.setFallDistance(0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.75f);
        p.sendTitle("§d§lDUNK!", "§7Zum Ring!", 0, 10, 4);
        return true;
    }
    private void release(Player p, boolean pass) {
        if (!p.getUniqueId().equals(carrier)) return;
        if (dunker != null) return;
        if (pass && tick < passCooldown.getOrDefault(p.getUniqueId(), 0)) return;
        if (!spend(p, pass ? getConfig().getDouble("stamina.pass-cost", 2) : getConfig().getDouble("stamina.shot-cost", 5))) return;
        int held = chargeTicks.getOrDefault(p.getUniqueId(), 0);
        charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
        carrier = null;
        ball = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.7));
        Vector direction = p.getEyeLocation().getDirection().normalize();
        pickupLockedPlayer = p.getUniqueId();
        pickupLockedUntil = tick + getConfig().getInt("passing.pickup-lock-ticks", 12);
        passFlight = pass;
        intendedReceiver = null;
        if (pass) {
            Player receiver = smartTarget(p);
            if (receiver != null) {
                Vector toward = receiver.getLocation().add(0, 1.15, 0).toVector().subtract(ball.toVector());
                direction = toward.normalize();
                intendedReceiver = receiver.getUniqueId();
                intendedReceiverUntil = tick + 36;
                p.sendActionBar("§dSMART PASS §7→ §f" + receiver.getName());
            } else p.sendActionBar("§7Freier Pass");
            velocity = direction.multiply(getConfig().getDouble("passing.speed", 1.10));
            passCooldown.put(p.getUniqueId(), tick + getConfig().getInt("passing.cooldown-ticks", 8));
        } else {
            int phase = held % SHOT_CYCLE;
            boolean green = green(p, phase);
            // Charging sets shot strength; releasing in the green window improves consistency.
            // The player's camera direction remains decisive: no auto-aim or guaranteed basket.
            double charge = Math.min(1.0, held / 30.0);
            double strength = setting("shoot-speed", 1.16) * (0.68 + 0.36 * charge);
            if (!green) {
                double miss = Math.min(0.15, Math.abs(phase - 26.5) / 150.0);
                strength *= (phase < greenStart(p) ? 1.0 - miss : 1.0 + miss);
            }
            velocity = direction.multiply(strength);
            velocity.setY(velocity.getY() + 0.13 + 0.07 * charge);
            // Require camera to point at the actual rim, vertically AND horizontally.
            // No assist if aiming at the floor or several blocks away.
            Location target = null;
            double bestAngle = Math.min(7.0, getConfig().getDouble("shooting.assist-max-angle-degrees", 7.0));
            for (Location hoop : hoops.values()) {
                if (!hoop.getWorld().equals(p.getWorld())) continue;
                Vector toRim = hoop.toVector().subtract(p.getEyeLocation().toVector());
                double d = toRim.length();
                if (d < 1.6 || d > getConfig().getDouble("shooting.max-distance", 22.0)) continue;
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, direction.dot(toRim.normalize())))));
                if (angle < bestAngle) { bestAngle = angle; target = hoop; }
            }
            if (target != null) {
                // Green release improves timing, but camera alignment still matters.
                // Offset the target sideways in proportion to the player's horizontal aim error.
                Vector toHoop = target.toVector().subtract(ball.toVector()).setY(0);
                Vector horizontalAim = direction.clone().setY(0).normalize();
                double signed = toHoop.clone().normalize().getX() * horizontalAim.getZ()
                        - toHoop.clone().normalize().getZ() * horizontalAim.getX();
                double distanceToHoop = toHoop.length();
                Vector lateral = new Vector(-toHoop.getZ(), 0, toHoop.getX()).normalize();
                target = target.clone().add(lateral.multiply(Math.max(-2.0, Math.min(2.0, signed * distanceToHoop * 0.65))));
                // Simulate the same discrete gravity and drag as step(), solving
                // for a DESCENDING crossing of the hoop plane. Never require the
                // player to stand above the hoop.
                Vector delta = target.toVector().subtract(ball.toVector());
                double distance = Math.hypot(delta.getX(), delta.getZ());
                int duration = (int)Math.max(16, Math.min(39, 17 + distance * 0.95));
                double drag = setting("drag", 0.992);
                double gravity = setting("gravity", 0.045);
                double horizontalSum = 0, verticalGravity = 0, vyFactor = 0;
                for (int i = 0; i < duration; i++) {
                    horizontalSum += Math.pow(drag, i + 1);
                    vyFactor += Math.pow(drag, i + 1);
                    verticalGravity += gravity * (1 - Math.pow(drag, i + 1)) / (1 - drag + 1e-12);
                }
                // Exact recurrence: v_n = drag*v_(n-1) - gravity
                // y(T) = vy0*sum(drag^i) - gravity*sum((1-drag^i)/(1-drag))
                double gSum = 0;
                for (int i = 1; i <= duration; i++) gSum += (1 - Math.pow(drag, i)) / Math.max(1e-8, 1 - drag);
                double vx = delta.getX() / horizontalSum;
                double vz = delta.getZ() / horizontalSum;
                double vy = (delta.getY() + gravity * gSum) / vyFactor;
                // Early/late release creates meaningful misses; green is accurate.
                double error = green ? 0.004 : Math.min(0.15, 0.025 + Math.abs(phase - 26) * 0.005);
                velocity = new Vector(vx + (Math.random() - 0.5) * error,
                        vy + (green ? 0 : (phase < greenStart(p) ? -0.022 : 0.025)),
                        vz + (Math.random() - 0.5) * error);
                p.sendActionBar("§d§lKORB ANVISIERT §7• §f" + String.format(Locale.ROOT, "%.1f", distance) + "m");
            } else {
                p.sendActionBar("§7Kein Korb im Blickfeld - freier Wurf");
            }
            p.sendActionBar(green ? "§aPerfektes Timing" : "§6Release");
            p.playSound(p, green ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_SNOWBALL_THROW, 0.9f, green ? 1.5f : 0.8f);
        }
        lastShooter = p.getUniqueId();
        shotOrigin = p.getLocation().clone();
        airborne = true;
        scoredThisFlight = false;
        if (display != null && display.isValid()) display.teleport(ball);
        if (pass) p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 1f, 1.25f);
    }
    private void placeBall(Player p) {
        if (!p.getUniqueId().equals(carrier)) return;
        carrier = null;
        charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
        ball = p.getLocation().add(0, 0.5, 0);
        velocity.zero(); airborne = true; passFlight = false; intendedReceiver = null;
        pickupLockedPlayer = p.getUniqueId(); pickupLockedUntil = tick + 12;
        p.sendActionBar("§7Ball abgelegt");
    }
    @EventHandler(priority = EventPriority.HIGH) public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Action action = event.getAction();
        String wand = wandType(event.getItem());
        if (wand != null && p.hasPermission("basketball.admin")) {
            event.setCancelled(true);
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            Block block = event.getClickedBlock();
            if (block == null) return;
            if (wand.equals("editor")) {
                if (action == Action.RIGHT_CLICK_BLOCK) editBlock(p, block, p.isSneaking());
                else if (action == Action.LEFT_CLICK_BLOCK) openSetup(p);
            } else if (wand.equals("arena")) {
                if (action == Action.LEFT_CLICK_BLOCK) {
                    arenaPos1.put(p.getUniqueId(), block.getLocation());
                    p.sendMessage("§dHalle: Position 1 gesetzt: §f" + block.getX()+" "+block.getY()+" "+block.getZ());
                } else if (action == Action.RIGHT_CLICK_BLOCK) {
                    arenaPos2.put(p.getUniqueId(), block.getLocation());
                    p.sendMessage("§dHalle: Position 2 gesetzt. §7Jetzt /bc arena save <name>");
                }
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                if (p.isSneaking()) {
                    hoops.remove(wand); getConfig().set("hoops." + wand, null); saveConfig();
                    p.sendMessage("§eKorb " + wand + " entfernt.");
                } else {
                    // Marked block CENTER is the rim center, not the block corner.
                    Location center = block.getLocation().add(0.5, 0.5, 0.5);
                    saveHoop(wand, center); hoopViewers.add(p.getUniqueId());
                    p.sendMessage("§aKorb " + wand + " gesetzt. §7Ringvorschau eingeschaltet!");
                    drawHoops();
                }
            }
            return;
        }
        if (p.getUniqueId().equals(carrier)) {
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                if (dunker != null && dunker.equals(p.getUniqueId())) return;
                if (attemptDunk(p)) return;
                if (charging.containsKey(p.getUniqueId())) release(p, false);
                else startCharge(p);
                return;
            }
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                if (dunker != null && dunker.equals(p.getUniqueId())) return;
                if (p.isSneaking()) placeBall(p);
                else release(p, true);
                return;
            }
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { hoopViewers.remove(event.getPlayer().getUniqueId()); restoreSpeed(event.getPlayer()); stamina.remove(event.getPlayer().getUniqueId()); grounded.remove(event.getPlayer().getUniqueId()); jumpWindow.remove(event.getPlayer().getUniqueId()); charging.remove(event.getPlayer().getUniqueId()); chargeTicks.remove(event.getPlayer().getUniqueId()); dropCarrier(event.getPlayer()); }
    @EventHandler public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getEntity().getUniqueId().equals(carrier)) {
            dropCarrier(event.getEntity());
        }
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer().getUniqueId().equals(carrier) && event.getTo() != null &&
                !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            Location old = ball == null ? event.getFrom() : ball.clone();
            dropCarrier(event.getPlayer());
            ball = old;
        }
    }
    private void dropCarrier(Player p) {
        if (p.getUniqueId().equals(dunker)) { dunker = null; dunkSide = null; }
        if (!p.getUniqueId().equals(carrier)) return;
        carrier = null; charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
        if (ball != null) {
            ball = p.getLocation().clone().add(0, 0.6, 0);
            velocity.zero(); airborne = true; pickupLockedPlayer = p.getUniqueId(); pickupLockedUntil = tick + 12; passFlight = false;
        }
    }
    private void step() {
        tick++;
        updateStamina();
        updateDunk();
        for (UUID id : new ArrayList<>(charging.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !id.equals(carrier)) { charging.remove(id); chargeTicks.remove(id); continue; }
            int elapsed = chargeTicks.merge(id, 1, Integer::sum);
            int phase = elapsed % SHOT_CYCLE;
            int greenFrom = greenStart(player), greenTo = greenEnd(player);
            int filled = (int) Math.round(20.0 * phase / SHOT_CYCLE);
            StringBuilder meter = new StringBuilder("§6[");
            for (int i = 0; i < 20; i++) meter.append(i >= (int)(20.0 * greenFrom / SHOT_CYCLE) && i <= (int)(20.0 * greenTo / SHOT_CYCLE) ? "§a▌" : i < filled ? "§e▌" : "§8▌");
            meter.append("§6] §7Klick: Release");
            player.sendActionBar(meter.toString());
            // No sound cue: players must time the visual meter themselves.
        }
        if (tick % 5 == 0) drawHoops();
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
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            boolean moving = p.getVelocity().clone().setY(0).lengthSquared() > 0.004;
            double rhythm = p.isSprinting() ? 0.85 : moving ? 0.60 : 0.33;
            double height = 0.32 + Math.abs(Math.sin(tick * rhythm)) * 0.58;
            // Keep the ball visible in first person: in front and slightly to the right.
            if (p.getUniqueId().equals(dunker)) {
                ball = p.getLocation().add(forward.multiply(0.48)).add(0, 1.25, 0);
            } else {
                ball = p.getLocation().add(right.multiply(0.32)).add(forward.multiply(0.72)).add(0, height, 0);
            }
            if (!p.getUniqueId().equals(dunker) && tick % (p.isSprinting() ? 7 : moving ? 10 : 17) == 0)
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOOD_HIT, 0.22f, 0.6f);
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
            if (velocity.getY() < 0 && lastShooter != null && !scoredThisFlight) {
                for (var hoopEntry : hoops.entrySet()) {
                    Location hoop = hoopEntry.getValue();
                    if (!hoop.getWorld().equals(ball.getWorld())) continue;
                    if (ball.getY() >= hoop.getY() && next.getY() < hoop.getY()) {
                        double frac = (ball.getY() - hoop.getY()) / (ball.getY() - next.getY());
                        double ix = ball.getX() + (next.getX() - ball.getX()) * frac - hoop.getX();
                        double iz = ball.getZ() + (next.getZ() - ball.getZ()) * frac - hoop.getZ();
                        if (ix * ix + iz * iz <= hoopRadius * hoopRadius) {
                            scoredThisFlight = true;
                            score(hoopEntry.getKey());
                            // Continue simulating the same ball: it must fall through the hoop.
                            break;
                        }
                    }
                }
            }
            // A real pass can be intercepted mid-flight; intended receiver gets no immunity.
            if (passFlight && tick % 1 == 0) {
                double catchRadius = getConfig().getDouble("passing.catch-radius", 1.05);
                Player caught = null;
                double bestDist = catchRadius * catchRadius;
                for (Player candidate : next.getWorld().getPlayers()) {
                    if (candidate.isDead() || candidate.getGameMode() == GameMode.SPECTATOR) continue;
                    if (candidate.getUniqueId().equals(pickupLockedPlayer) && tick < pickupLockedUntil) continue;
                    double d = candidate.getLocation().add(0, 1.0, 0).distanceSquared(next);
                    if (d < bestDist) { bestDist = d; caught = candidate; }
                }
                if (caught != null) { ball = next; carry(caught); return; }
            }
            if (next.getBlock().getType().isSolid() || (movement.getY() <= 0 && next.clone().add(0, -0.25, 0).getBlock().getType().isSolid())) {
                boolean floor = movement.getY() <= 0 && ball.clone().add(0, -0.25, 0).getBlock().getType().isSolid();
                if (floor || (velocity.getY() < 0 && !ball.clone().add(0, -0.25, 0).getBlock().getType().isSolid())) {
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
        if (ball.getY() < ball.getWorld().getMinHeight() + 2) { spawnBall(ball.getWorld().getSpawnLocation().add(0, 1, 0)); return; }
        if (velocity.lengthSquared() < 0.0009 && ball.clone().add(0, -0.25, 0).getBlock().getType().isSolid()) {
            velocity.zero(); airborne = false;
        }
        display.teleport(ball);
        if (tick % 3 == 0) {
            for (Player p : ball.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(ball) < 2.25 && velocity.lengthSquared() < 0.12) { carry(p); break; }
            }
        }
    }
    private void updateStamina() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean onGround = p.isOnGround();
            boolean wasGrounded = grounded.getOrDefault(id, onGround);
            if (wasGrounded && !onGround && p.getVelocity().getY() > 0.08) jumpWindow.put(id, tick + 12);
            grounded.put(id, onGround);
            if (p.isSprinting()) sprintRecent.put(id, tick);
            double value = energy(p);
            boolean sprinting = p.isSprinting();
            if (sprinting && value <= 0.1) {
                p.setSprinting(false);
                sprintUnlock.put(id, 25);
                sprinting = false;
            }
            if (sprintUnlock.containsKey(id) && value < sprintUnlock.get(id)) {
                if (p.isSprinting()) p.setSprinting(false);
                sprinting = false;
            } else sprintUnlock.remove(id);
            if (sprinting) value -= (id.equals(carrier) ? getConfig().getDouble("stamina.sprint-with-ball-per-second", 9)
                    : getConfig().getDouble("stamina.sprint-per-second", 5)) / 20.0;
            else {
                boolean moving = p.getVelocity().clone().setY(0).lengthSquared() > 0.004;
                value += (moving ? getConfig().getDouble("stamina.walk-regen-per-second", 3)
                    : getConfig().getDouble("stamina.idle-regen-per-second", 6)) / 20.0;
            }
            value = Math.max(0, Math.min(100, value));
            if (value <= 0.01) { sprintUnlock.put(id, 25); p.setSprinting(false); }
            if (sprintUnlock.containsKey(id) && value < 25) {
                p.setSprinting(false);
                baseWalkSpeed.putIfAbsent(id, p.getWalkSpeed());
                p.setWalkSpeed(Math.min(baseWalkSpeed.get(id), 0.13f));
            } else restoreSpeed(p);
            stamina.put(id, value);
            if (tick % 10 == 0 && !charging.containsKey(id)) {
                int bars = (int)Math.round(value / 10.0);
                StringBuilder bar = new StringBuilder("§d⚡ §7[");
                for (int i = 0; i < 10; i++) bar.append(i < bars ? (value < 25 ? "§c▌" : "§d▌") : "§8▌");
                bar.append("§7] §f").append((int)Math.round(value)).append("/100");
                if (id.equals(carrier)) bar.append(" §7| §d🏀");
                p.sendActionBar(bar.toString());
            }
        }
    }
    private void restoreSpeed(Player p) {
        Float original = baseWalkSpeed.remove(p.getUniqueId());
        if (original != null && p.isOnline()) p.setWalkSpeed(original);
    }
    @EventHandler public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting() && sprintUnlock.containsKey(e.getPlayer().getUniqueId()) && energy(e.getPlayer()) < 25) {
            e.setCancelled(true); e.getPlayer().setSprinting(false);
        }
    }
    private void updateDunk() {
        if (dunker == null) return;
        Player p = Bukkit.getPlayer(dunker);
        Location hoop = dunkSide == null ? null : hoops.get(dunkSide);
        if (p == null || !p.isOnline() || hoop == null || !p.getWorld().equals(hoop.getWorld()) ||
                !dunker.equals(carrier)) { dunker = null; dunkSide = null; return; }
        p.setFallDistance(0);
        double dx = p.getLocation().getX() - hoop.getX();
        double dz = p.getLocation().getZ() - hoop.getZ();
        double distance = Math.hypot(dx, dz);
        // The player's reach is roughly 2 blocks above their feet.
        if (tick >= dunkReadyTick && distance <= 1.55 &&
                p.getLocation().getY() + 2.15 >= hoop.getY() && p.getLocation().getY() <= hoop.getY() + 0.4) {
            lastShooter = p.getUniqueId(); shotOrigin = p.getLocation().clone();
            // Dunk is worth two, regardless of the starting distance.
            scores.merge(dunkSide, 2, Integer::sum);
            Bukkit.broadcastMessage("§d§l🏀 DUNK! §f" + p.getName() + " §7(+2) §cROT " + scores.get("red") + " §7- §9" + scores.get("blue") + " BLAU");
            hoop.getWorld().playSound(hoop, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 0.7f);
            hoop.getWorld().spawnParticle(Particle.CRIT, hoop, 24, 0.5, 0.2, 0.5, 0.1);
            // Detach the visual ball and animate a genuine downward drop through the rim.
            carrier = null; charging.remove(p.getUniqueId()); chargeTicks.remove(p.getUniqueId());
            ball = hoop.clone().add(0, 0.38, 0);
            velocity = new Vector(0, -0.17, 0);
            airborne = true; scoredThisFlight = true; passFlight = false;
            pickupLockedPlayer = p.getUniqueId(); pickupLockedUntil = tick + 16;
            if (display != null && display.isValid()) display.teleport(ball);
            p.sendTitle("§d§lDUNK!", "§a+2 PUNKTE", 0, 22, 8);
            dunker = null; dunkSide = null;
        } else if (tick > dunkEnds || (p.isOnGround() && tick > dunkReadyTick + 3)) {
            p.sendActionBar("§cDunk verfehlt!");
            dunker = null; dunkSide = null;
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { stamina.put(event.getPlayer().getUniqueId(), 100.0); }
    private void drawHoops() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!hoopViewers.contains(viewer.getUniqueId())) continue;
            for (var entry : hoops.entrySet()) {
                Location center = entry.getValue();
                if (!center.getWorld().equals(viewer.getWorld()) || center.distanceSquared(viewer.getLocation()) > 48*48) continue;
                org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                    entry.getKey().equals("red") ? Color.fromRGB(255, 50, 65) : Color.fromRGB(45, 125, 255), 1.15f);
                for (int i = 0; i < 32; i++) {
                    double angle = Math.PI * 2 * i / 32.0;
                    viewer.spawnParticle(Particle.DUST, center.getX() + Math.cos(angle)*hoopRadius,
                        center.getY(), center.getZ() + Math.sin(angle)*hoopRadius, 1, 0, 0, 0, 0, dust);
                }
                viewer.spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
            }
        }
    }
    private void showHoopOnce(Player p, String side) {
        Location loc = hoops.get(side);
        if (loc == null) return;
        p.sendMessage("§e" + side.toUpperCase(Locale.ROOT) + " §7Trefferzone: §f" +
            String.format(Locale.ROOT, "%.2f / %.2f / %.2f", loc.getX(), loc.getY(), loc.getZ()) +
            " §7(Radius " + hoopRadius + " Blöcke)");
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
    }
    private void score(String side) {
        int points = 2;
        Location hoop = hoops.get(side);
        if (shotOrigin != null && hoop != null && shotOrigin.getWorld().equals(hoop.getWorld())) {
            List<Location> line = threeLines.getOrDefault(side, List.of());
            if (line.size() >= 3) {
                double dx = shotOrigin.getX() - hoop.getX(), dz = shotOrigin.getZ() - hoop.getZ();
                double playerDist = Math.hypot(dx, dz);
                if (playerDist > 0.01) {
                    double ux = dx / playerDist, uz = dz / playerDist;
                    double boundary = Double.POSITIVE_INFINITY;
                    for (int i = 0; i < line.size() - 1; i++) {
                        Location a = line.get(i), b = line.get(i+1);
                        if (!a.getWorld().equals(hoop.getWorld()) || !b.getWorld().equals(hoop.getWorld())) continue;
                        double ax=a.getX()-hoop.getX(), az=a.getZ()-hoop.getZ();
                        double sx=b.getX()-a.getX(), sz=b.getZ()-a.getZ();
                        double det=ux*sz-uz*sx;
                        if (Math.abs(det)<1e-8) continue;
                        double t=(ax*sz-az*sx)/det, u=(ax*uz-az*ux)/det;
                        if (t>0 && u>=0 && u<=1) boundary=Math.min(boundary,t);
                    }
                    if (Double.isFinite(boundary) && playerDist >= boundary - 0.01) points = 3;
                }
            } else if (shotOrigin.distanceSquared(hoop) >= Math.pow(getConfig().getDouble("court.three-point-distance", 8.0), 2)) points = 3;
        }
        scores.merge(side, points, Integer::sum);
        String scorer = lastShooter == null ? "Unbekannt" : Optional.ofNullable(Bukkit.getOfflinePlayer(lastShooter).getName()).orElse("Spieler");
        Bukkit.broadcastMessage("§6§l🏀 KORB! §f" + scorer + " §7(" + points + " Punkte) §cROT " + scores.get("red") + " §7- §9" + scores.get("blue") + " BLAU");
        hoop.getWorld().playSound(hoop, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        // Do not respawn or freeze the ball. Its downward velocity continues.
    }
    private void saveThree(String side) {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Location l : threeLines.getOrDefault(side, List.of())) {
            Map<String,Object> row = new HashMap<>();
            row.put("world",l.getWorld().getName()); row.put("x",l.getX()); row.put("y",l.getY()); row.put("z",l.getZ()); rows.add(row);
        }
        getConfig().set("three-lines." + side, rows); saveConfig();
    }
    private void drawThree(Player p) {
        for (String side : List.of("red","blue")) {
            List<Location> points = threeLines.getOrDefault(side,List.of());
            Particle.DustOptions dust = new Particle.DustOptions(side.equals("red") ? Color.RED : Color.AQUA, 1.1f);
            for (int i=0;i<points.size();i++) {
                Location a=points.get(i); if (!a.getWorld().equals(p.getWorld())) continue;
                p.spawnParticle(Particle.DUST,a,10,.08,.08,.08,0,dust);
                if (i+1>=points.size()) continue;
                Location b=points.get(i+1); if (!b.getWorld().equals(a.getWorld())) continue;
                Vector delta=b.toVector().subtract(a.toVector()); int n=Math.max(1,(int)(delta.length()*3));
                for(int j=0;j<=n;j++) p.spawnParticle(Particle.DUST,a.clone().add(delta.clone().multiply(j/(double)n)),1,0,0,0,0,dust);
            }
        }
    }
    private void editBlock(Player p, Block block, boolean undo) {
        String mode=editMode.getOrDefault(p.getUniqueId(),"arena1");
        Location loc=block.getLocation();
        if (mode.equals("arena1") || mode.equals("arena2")) {
            (mode.equals("arena1") ? arenaPos1 : arenaPos2).put(p.getUniqueId(),loc);
            p.sendMessage("§d"+mode+" §7gesetzt. §f/bc arena save <name>"); return;
        }
        if (mode.equals("red") || mode.equals("blue")) {
            if (undo) { hoops.remove(mode); getConfig().set("hoops."+mode,null); saveConfig(); p.sendMessage("§eKorb entfernt."); }
            else { saveHoop(mode,loc.add(.5,.5,.5)); hoopViewers.add(p.getUniqueId()); showHoopOnce(p,mode); drawHoops(); p.sendMessage("§aRing markiert! §7Partikel zeigen die Trefferzone."); }
            return;
        }
        if (mode.equals("three-red") || mode.equals("three-blue")) {
            String side=mode.substring(6); List<Location> line=threeLines.computeIfAbsent(side,k->new ArrayList<>());
            if (undo) { if(!line.isEmpty())line.remove(line.size()-1); }
            else { if (!line.isEmpty() && !line.get(0).getWorld().equals(loc.getWorld())) {p.sendMessage("§cAlle Linienpunkte müssen in derselben Welt sein.");return;} line.add(loc.add(.5,1.03,.5)); }
            saveThree(side); drawThree(p); p.sendMessage("§aDreierlinie "+side+": §f"+line.size()+" Punkte §7(Shift+Rechtsklick: letzter Punkt zurück)");
            return;
        }
        p.sendMessage("§cWähle erst einen Editor-Modus: /bc setup");
    }
    private void openSetup(Player p) {
        org.bukkit.inventory.Inventory inv=Bukkit.createInventory(null,27,"BasketballCraft | Court Editor");
        String[] modes={"arena1","arena2","red","blue","three-red","three-blue"};
        String[] names={"§dHalle: Ecke 1","§dHalle: Ecke 2","§cKorb Team 1","§9Korb Team 2","§cDreierlinie Team 1","§9Dreierlinie Team 2"};
        Material[] materials={Material.WOODEN_AXE,Material.WOODEN_AXE,Material.BLAZE_ROD,Material.STICK,Material.RED_DYE,Material.BLUE_DYE};
        int[] slots={10,11,12,14,15,16};
        for(int i=0;i<modes.length;i++) { ItemStack item=new ItemStack(materials[i]); ItemMeta meta=item.getItemMeta();meta.setDisplayName(names[i]);meta.setLore(List.of("§7Klicken zum Auswählen", "§7Danach Editor-Stab benutzen"));item.setItemMeta(meta);inv.setItem(slots[i],item); }
        setupInventories.put(p.getUniqueId(),inv); p.openInventory(inv);
    }
    @EventHandler public void onSetupClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory()!=setupInventories.get(p.getUniqueId())) return;
        e.setCancelled(true); if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        String mode=switch(e.getRawSlot()) {case 10->"arena1";case 11->"arena2";case 12->"red";case 14->"blue";case 15->"three-red";case 16->"three-blue";default->null;};
        if(mode==null)return;
        editMode.put(p.getUniqueId(),mode);p.closeInventory();
        p.sendMessage("§dCourt-Editor §7Modus: §f"+mode+" §7| Rechtsklick setzen, Shift+Rechtsklick entfernen, Linksklick Menü");
        if(p.getInventory().all(Material.GOLDEN_HOE).values().stream().noneMatch(i->"editor".equals(wandType(i))))p.getInventory().addItem(wand("editor"));
    }
    @EventHandler public void onSetupClose(InventoryCloseEvent e) {
        if(e.getPlayer() instanceof Player p && e.getInventory()==setupInventories.get(p.getUniqueId()))setupInventories.remove(p.getUniqueId());
    }
    private int parsePage(String raw) {try{return Integer.parseInt(raw);}catch(NumberFormatException e){return 1;}}
    private void help(CommandSender sender,int page) {
        String[][] pages={
            {"§d§lBasketballCraft 0.8.2 §7- Hilfe 1/3", "§e/bc help [1-3] §7- Befehlsliste", "§e/bc setup §7- Court-Editor-Menü", "§e/bc wand §7- Editor-Stab", "§e/bc ball §7- Ball spawnen", "§e/bc reset §7- Punkte und Ball zurücksetzen", "§e/bc score §7- Punktestand"},
            {"§d§lBasketballCraft §7- Hilfe 2/3", "§e/bc arena save <name> §7- Halle speichern", "§e/bc arena list §7- Hallen auflisten", "§e/bc arena select <name> §7- Halle wählen", "§e/bc arena show §7- Hallengrenzen", "§e/bc arena delete <name> §7- Halle löschen", "§e/bc showhoops §7- Korbringe an/aus"},
            {"§d§lBasketballCraft §7- Hilfe 3/3", "§e/bc three show §7- Dreierlinien anzeigen", "§e/bc three undo <1|2> §7- Letzten Punkt löschen", "§e/bc three clear <1|2> §7- Linie löschen", "§e/bc wand 1|2|arena §7- Alte Markierungsstäbe", "§e/bc sethoop red|blue §7- Ring auf Augenhöhe", "§7Linksklick: Wurf | Rechtsklick: Pass", "§7Sprint+Sprung+Linksklick: Dunk"}
        };
        int idx=Math.max(1,Math.min(3,page))-1;
        for(String line:pages[idx])sender.sendMessage(line);
        sender.sendMessage("§8-------------------- §7Seite "+(idx+1)+"/3 §8--------------------");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> options;
        if(args.length==1)options=List.of("help","setup","wand","ball","start","reset","score","arena","three","showhoops","sethoop");
        else if(args.length==2) options=switch(args[0].toLowerCase(Locale.ROOT)) {
            case "help"->List.of("1","2","3");case "wand"->List.of("1","2","arena","editor");case "arena"->List.of("save","list","select","show","delete");case "three"->List.of("show","undo","clear");case "sethoop"->List.of("red","blue");default->List.of();};
        else if(args.length==3 && args[0].equalsIgnoreCase("three"))options=List.of("1","2");
        else if(args.length==3 && args[0].equalsIgnoreCase("arena") && List.of("select","delete").contains(args[1]))options=new ArrayList<>(arenas.keySet());
        else options=List.of();
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender,args.length>1 ? parsePage(args[1]) : 1);
            return true;
        }
        if (args[0].equalsIgnoreCase("score")) {
            sender.sendMessage("§cROT §f" + scores.get("red") + " §7- §f" + scores.get("blue") + " §9BLAU");
            return true;
        }
        if (!sender.hasPermission("basketball.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can configure the court."); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setup" -> { openSetup(p); }
            case "three" -> {
                if(args.length<2) {p.sendMessage("§e/bc three show|undo <1|2>|clear <1|2>");break;}
                String sub=args[1].toLowerCase(Locale.ROOT);
                if(sub.equals("show")){drawThree(p);p.sendMessage("§aDreierlinien angezeigt.");break;}
                if(args.length<3 || !List.of("1","2").contains(args[2])){p.sendMessage("§cBitte Team 1 oder 2 angeben.");break;}
                String side=args[2].equals("1")?"red":"blue";
                List<Location> points=threeLines.computeIfAbsent(side,k->new ArrayList<>());
                if(sub.equals("clear"))points.clear();else if(sub.equals("undo")&&!points.isEmpty())points.remove(points.size()-1);
                else if(!List.of("clear","undo").contains(sub)){p.sendMessage("§cUnbekannte Aktion.");break;}
                saveThree(side);drawThree(p);p.sendMessage("§aLinie "+side+": "+points.size()+" Punkte.");
            }
            case "wand" -> {
                if (args.length < 2) { p.getInventory().addItem(wand("editor")); p.sendMessage("§aCourt-Editor erhalten. §7/bc setup"); return true; }
                String type = args[1].equals("1") ? "red" : args[1].equals("2") ? "blue" : args[1].toLowerCase(Locale.ROOT);
                if (!List.of("arena", "red", "blue", "editor").contains(type)) { p.sendMessage("§c/bc wand arena|1|2"); return true; }
                p.getInventory().addItem(wand(type)); p.sendMessage("§aMarkierungsstab erhalten: §f" + type);
            }
            case "arena" -> {
                if (args.length < 2) { p.sendMessage("§e/bc arena save <name> | show | list | select <name> | delete <name>"); return true; }
                String sub = args[1].toLowerCase(Locale.ROOT);
                if (sub.equals("save") && args.length >= 3) {
                    Location a = arenaPos1.get(p.getUniqueId()), b = arenaPos2.get(p.getUniqueId());
                    if (a == null || b == null || !a.getWorld().equals(b.getWorld())) { p.sendMessage("§cErst beide Hallenecken in derselben Welt markieren."); return true; }
                    saveArena(args[2].toLowerCase(Locale.ROOT), a, b);
                    p.sendMessage("§aHalle gespeichert und aktiviert: §f" + activeArena);
                } else if (sub.equals("list")) p.sendMessage("§dHallen: §f" + arenas.keySet() + " §7Aktiv: " + activeArena);
                else if (sub.equals("select") && args.length >= 3) {
                    String name = args[2].toLowerCase(Locale.ROOT);
                    if (!arenas.containsKey(name)) { p.sendMessage("§cHalle nicht gefunden."); return true; }
                    activeArena = name; getConfig().set("active-arena", name); saveConfig(); p.sendMessage("§aHalle aktiviert: " + name);
                } else if (sub.equals("delete") && args.length >= 3) {
                    String name = args[2].toLowerCase(Locale.ROOT);
                    arenas.remove(name); getConfig().set("arenas."+name, null);
                    if (name.equals(activeArena)) { activeArena = null; getConfig().set("active-arena", null); }
                    saveConfig(); p.sendMessage("§eHalle gelöscht: " + name);
                } else if (sub.equals("show")) {
                    if (activeArena == null || !arenas.containsKey(activeArena)) { p.sendMessage("§cKeine aktive Halle."); return true; }
                    Location[] corners = arenas.get(activeArena);
                    Location a = corners[0], b = corners[1];
                    p.sendMessage("§dHalle " + activeArena + " §7Ecken: " + a.getBlockX()+","+a.getBlockY()+","+a.getBlockZ()+" bis "+b.getBlockX()+","+b.getBlockY()+","+b.getBlockZ());
                    if (p.getWorld().equals(a.getWorld())) {
                        for (int i=0;i<=32;i++) {
                            double t=i/32.0;
                            for (int x=0;x<2;x++) for (int y=0;y<2;y++) {
                                double xx=x==0?a.getBlockX():b.getBlockX()+1, yy=y==0?a.getBlockY():b.getBlockY()+1;
                                p.spawnParticle(Particle.END_ROD,xx,yy,a.getBlockZ()+(b.getBlockZ()+1-a.getBlockZ())*t,1,0,0,0,0);
                            }
                            for (int z=0;z<2;z++) for (int y=0;y<2;y++) {
                                double zz=z==0?a.getBlockZ():b.getBlockZ()+1, yy=y==0?a.getBlockY():b.getBlockY()+1;
                                p.spawnParticle(Particle.END_ROD,a.getBlockX()+(b.getBlockX()+1-a.getBlockX())*t,yy,zz,1,0,0,0,0);
                            }
                        }
                    }
                } else p.sendMessage("§c/bc arena save <name> | show | list | select <name> | delete <name>");
            }
            case "showhoops" -> {
                if (hoopViewers.remove(p.getUniqueId())) p.sendMessage("§7Korbmarkierungen ausgeschaltet.");
                else { hoopViewers.add(p.getUniqueId()); p.sendMessage("§aKorbmarkierungen eingeschaltet. §7Nur du siehst sie.");
                    for (String side : List.of("red", "blue")) showHoopOnce(p, side);
                    drawHoops();
                }
            }
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
                p.sendMessage("§aKorbmitte gespeichert (Augenhöhe). §7Mit /bc showhoops prüfen.");
                showHoopOnce(p, side);
                hoopViewers.add(p.getUniqueId());
            }
            case "reset" -> { scores.put("red", 0); scores.put("blue", 0); spawnBall(p.getLocation().add(0, 1, 0)); p.sendMessage("§aSpielstand zurückgesetzt."); }
            default -> p.sendMessage("§cUnbekannter Befehl. /bc help");
        }
        return true;
    }
}
