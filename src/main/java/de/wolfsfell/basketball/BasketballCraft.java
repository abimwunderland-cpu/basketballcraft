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
    // Event match state. One active arena/match per server (same scope as legacy ball).
    private enum MatchPhase { IDLE, COUNTDOWN, LIVE, FINISHED }
    private MatchPhase matchPhase = MatchPhase.IDLE;
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Map<UUID, Integer> lifetimePoints = new HashMap<>();
    private final Map<UUID, Integer> lifetimeWins = new HashMap<>();
    private final java.io.File historyFile = new java.io.File("plugins/BasketballCraft/match-history.yml");
    private boolean staminaEnabled(Player p) { return matchPhase==MatchPhase.LIVE && !matchPaused && playing(p); }
    private void loadLifetimeStats() {
        org.bukkit.configuration.file.YamlConfiguration data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"leaderboard.yml"));
        for(String k:data.getKeys(false)) try {
            UUID id=UUID.fromString(k); lifetimePoints.put(id,data.getInt(k+".points"));lifetimeWins.put(id,data.getInt(k+".wins"));
        } catch(IllegalArgumentException ignored) {}
    }
    private void saveLifetimeStats() {
        org.bukkit.configuration.file.YamlConfiguration data=new org.bukkit.configuration.file.YamlConfiguration();
        for(UUID id:lifetimePoints.keySet()){data.set(id+".points",lifetimePoints.getOrDefault(id,0));data.set(id+".wins",lifetimeWins.getOrDefault(id,0));}
        try {data.save(new java.io.File(getDataFolder(),"leaderboard.yml"));}catch(java.io.IOException ex){getLogger().severe("Leaderboard speichern fehlgeschlagen: "+ex.getMessage());}
    }
    private void recordMatchHistory() {
        java.io.File file=new java.io.File(getDataFolder(),"match-history.yml");
        org.bukkit.configuration.file.YamlConfiguration data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        String key="matches."+System.currentTimeMillis();
        data.set(key+".date",java.time.ZonedDateTime.now().toString());
        data.set(key+".arena",String.valueOf(selectedArena));
        data.set(key+".red",scores.getOrDefault("red",0));
        data.set(key+".blue",scores.getOrDefault("blue",0));
        int red=scores.getOrDefault("red",0),blue=scores.getOrDefault("blue",0);
        data.set(key+".winner",red==blue?"draw":red>blue?"red":"blue");
        for(var entry:playerTeams.entrySet()) {
            String player=key+".players."+entry.getKey();
            data.set(player+".name",Bukkit.getOfflinePlayer(entry.getKey()).getName());
            data.set(player+".team",entry.getValue());
            data.set(player+".points",playerPoints.getOrDefault(entry.getKey(),0));
            data.set(player+".steals",playerSteals.getOrDefault(entry.getKey(),0));
            data.set(player+".assists",playerAssists.getOrDefault(entry.getKey(),0));
            data.set(player+".blocks",playerBlocks.getOrDefault(entry.getKey(),0));
        }
        try {data.save(file);}catch(java.io.IOException ex){getLogger().severe("Match-History konnte nicht gespeichert werden: "+ex.getMessage());}
    }
    private void showMatchHistory(CommandSender sender,int limit) {
        java.io.File file=new java.io.File(getDataFolder(),"match-history.yml");
        var data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var section=data.getConfigurationSection("matches");
        if(section==null){sender.sendMessage("§7Noch keine gespeicherten Matches.");return;}
        var keys=new java.util.ArrayList<>(section.getKeys(false));
        keys.sort(java.util.Comparator.reverseOrder());
        sender.sendMessage("§d§lBasketball Match-History §7(letzte "+Math.min(limit,keys.size())+")");
        for(String k:keys.stream().limit(limit).toList()){
            String base="matches."+k;
            String winner=data.getString(base+".winner","draw");
            sender.sendMessage("§7"+data.getString(base+".date",k)+" §c"+data.getInt(base+".red")+" §7: §9"+data.getInt(base+".blue")+" §dSieger: §f"+winner);
            var players=data.getConfigurationSection(base+".players");
            if(players!=null)for(String id:players.getKeys(false))sender.sendMessage(" §8- §f"+data.getString(base+".players."+id+".name","?")+" §7("+data.getString(base+".players."+id+".team", "?")+") "+data.getInt(base+".players."+id+".points")+" P");
        }
    }
    private String shortName(UUID id){String n=Bukkit.getOfflinePlayer(id).getName();if(n==null)n="Spieler";return n.length()>11?n.substring(0,11):n;}

    private final Map<UUID, Integer> playerSteals = new HashMap<>();
    private final Map<UUID, Integer> playerBlocks = new HashMap<>();
    private final Map<UUID, Integer> playerAssists = new HashMap<>();
    private final Map<UUID, Integer> defenseCooldown = new HashMap<>();
    private final Map<UUID, Integer> possessionProtection = new HashMap<>();
    private UUID lastPasser;
    private int lastPassTick;
    private int matchEndTick;
    private int countdownEndTick;
    private int matchSeconds = 300;
    private boolean matchPaused;
    private int pauseRemaining;
    private UUID eventAdmin;
    private final Map<UUID, GameMode> observerOriginalModes = new HashMap<>();
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> oldBoards = new HashMap<>();
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> matchBoards = new HashMap<>();
    private final Map<UUID, org.bukkit.scoreboard.Objective> matchObjectives = new HashMap<>();
    private final Map<UUID, Integer> ballPickupProtection = new HashMap<>();
    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Map<String, Location> restartPoints = new HashMap<>();
    private final Map<String, Location[]> courtBounds = new HashMap<>();
    private final Map<UUID, Location> courtFirstCorners = new HashMap<>();
    private String restartExclusiveTeam;
    private int restartDeadlineTick = -1;
    private int lastFinalCountdownSecond = -1;
    private boolean courtLocked = true;
    private final Map<UUID, Integer> boundaryWarnings = new HashMap<>();
    private boolean insideCourt(Location loc) {
        if (activeArena == null || !arenas.containsKey(activeArena)) return false;
        Location[] c = courtBounds.getOrDefault(activeArena,arenas.get(activeArena));
        if (loc.getWorld() != c[0].getWorld()) return false;
        return loc.getX() >= Math.min(c[0].getX(),c[1].getX()) && loc.getX() <= Math.max(c[0].getX(),c[1].getX())+1
            && loc.getZ() >= Math.min(c[0].getZ(),c[1].getZ()) && loc.getZ() <= Math.max(c[0].getZ(),c[1].getZ())+1;
    }
    private boolean courtAccess(Player p) {
        return p.hasPermission("basketball.admin") || playing(p);
    }
    private void dressTeams() {
        for (UUID id : playerTeams.keySet()) {
            Player p=Bukkit.getPlayer(id); if(p==null)continue;
            originalChestplates.putIfAbsent(id, p.getInventory().getChestplate()==null?new ItemStack(Material.AIR):p.getInventory().getChestplate().clone());
            ItemStack vest=new ItemStack(Material.LEATHER_CHESTPLATE);
            org.bukkit.inventory.meta.LeatherArmorMeta meta=(org.bukkit.inventory.meta.LeatherArmorMeta)vest.getItemMeta();
            meta.setColor(playerTeams.get(id).equals("red")?Color.RED:Color.BLUE);
            meta.setDisplayName(playerTeams.get(id).equals("red")?"§cTeam Rot - Eventweste":"§9Team Blau - Eventweste");
            vest.setItemMeta(meta);p.getInventory().setChestplate(vest);
        }
    }
    private void restoreVests() {
        for(var entry:originalChestplates.entrySet()) {
            Player p=Bukkit.getPlayer(entry.getKey());if(p!=null)p.getInventory().setChestplate(entry.getValue());
        }
        originalChestplates.clear();
    }
    private void loadRestartPoints() {
        for(String side:List.of("red","blue")) {
            String path="restart-points."+side;
            World w=Bukkit.getWorld(getConfig().getString(path+".world",""));
            if(w!=null && getConfig().contains(path+".x"))restartPoints.put(side,new Location(w,getConfig().getDouble(path+".x"),getConfig().getDouble(path+".y"),getConfig().getDouble(path+".z")));
        }
        courtLocked=getConfig().getBoolean("court.locked",true);
        for(String arena:arenas.keySet()){
            String path="court.bounds."+arena;String worldName=getConfig().getString(path+".world");
            World w=worldName==null?null:Bukkit.getWorld(worldName);
            if(w==null || !getConfig().contains(path+".x1"))continue;
            courtBounds.put(arena,new Location[]{new Location(w,getConfig().getDouble(path+".x1"),0,getConfig().getDouble(path+".z1")),new Location(w,getConfig().getDouble(path+".x2"),0,getConfig().getDouble(path+".z2"))});
        }
    }
    private void saveRestartPoint(String side,Location l) {
        restartPoints.put(side,l.clone());String path="restart-points."+side;
        getConfig().set(path+".world",l.getWorld().getName());getConfig().set(path+".x",l.getX());getConfig().set(path+".y",l.getY());getConfig().set(path+".z",l.getZ());saveConfig();
    }

    private String opposite(String side) { return side.equals("red") ? "blue" : "red"; }
    private boolean playing(Player p) { return playerTeams.containsKey(p.getUniqueId()); }
    private boolean sameTeam(Player a, Player b) { return Objects.equals(playerTeams.get(a.getUniqueId()), playerTeams.get(b.getUniqueId())) && playing(a); }
    private boolean matchActive() { return matchPhase == MatchPhase.LIVE || matchPhase == MatchPhase.COUNTDOWN; }
    private boolean participantAllowed(Player p) { return !matchActive() || (matchPhase == MatchPhase.LIVE && !matchPaused && playing(p)); }
    private void announce(String message) { Bukkit.broadcastMessage("§d§l[BasketballCraft] §r" + message); }
    private String teamLabel(String side) { return side.equals("red") ? "§cTeam 1" : "§9Team 2"; }
    private void clearMatchBoards() {
        for (UUID id : new HashSet<>(matchBoards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) { var board = oldBoards.get(id); if (board != null) p.setScoreboard(board); }
        }
        matchBoards.clear(); matchObjectives.clear(); oldBoards.clear();
    }
    private void refreshBoards() {
        int seconds = matchPhase == MatchPhase.LIVE ? Math.max(0, (matchPaused ? pauseRemaining : matchEndTick - tick) / 20) :
            matchPhase == MatchPhase.COUNTDOWN ? Math.max(0, (countdownEndTick-tick)/20) : 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id=p.getUniqueId();
            if (!matchBoards.containsKey(id)) {
                oldBoards.put(id,p.getScoreboard());
                var board=Bukkit.getScoreboardManager().getNewScoreboard();
                var obj=board.registerNewObjective("bc_match",org.bukkit.scoreboard.Criteria.DUMMY,"§d§l✦ BASKETBALL ✦");
                obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
                matchBoards.put(id,board);matchObjectives.put(id,obj);p.setScoreboard(board);
            }
            var obj=matchObjectives.get(id);
            for(String entry:new HashSet<>(obj.getScoreboard().getEntries()))obj.getScoreboard().resetScores(entry);
            boolean live=matchActive() && playing(p);
            if(live){
                obj.getScore((tick/20%2==0?"§5":"§d")+"§m----------------").setScore(15);
                obj.getScore("§d§lBASKETBALL EVENT").setScore(14);
                obj.getScore("§7Zeit §f"+String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)).setScore(13);
                obj.getScore("§cROT §7vs §9BLAU").setScore(12);
                obj.getScore("§c"+scores.getOrDefault("red",0)+" §7: §9"+scores.getOrDefault("blue",0)).setScore(11);
                obj.getScore((tick/40%2==0?"§d§l":"§5§l")+"✦ TOP 3 SCORER").setScore(10);
                var top=playerPoints.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).limit(3).toList();
                for(int i=0;i<3;i++)obj.getScore("§d"+(i+1)+". §f"+(i<top.size()?shortName(top.get(i).getKey()):"---")+" §7"+(i<top.size()?top.get(i).getValue():0)+" P").setScore(9-i);
            }else{
                obj.getScore((tick/20%2==0?"§5":"§d")+"§m----------------").setScore(15);
                obj.getScore((tick/40%2==0?"§d§l":"§5§l")+"✦ ALL-TIME TOP 5").setScore(14);
                var top=lifetimePoints.entrySet().stream().sorted((a,b)->{int v=Integer.compare(b.getValue(),a.getValue());return v!=0?v:Integer.compare(lifetimeWins.getOrDefault(b.getKey(),0),lifetimeWins.getOrDefault(a.getKey(),0));}).limit(5).toList();
                for(int i=0;i<5;i++){
                    String line=i<top.size()?"§d"+(i+1)+". §f"+shortName(top.get(i).getKey())+" §7"+top.get(i).getValue()+"P "+lifetimeWins.getOrDefault(top.get(i).getKey(),0)+"W":"§8"+(i+1)+". ---";
                    obj.getScore(line).setScore(13-i);
                }
                obj.getScore("§5§m---------------").setScore(8);
                int rank=1;for(var entry:lifetimePoints.entrySet())if(entry.getValue()>lifetimePoints.getOrDefault(id,0) || (entry.getValue().equals(lifetimePoints.getOrDefault(id,0)) && lifetimeWins.getOrDefault(entry.getKey(),0)>lifetimeWins.getOrDefault(id,0)))rank++;
                obj.getScore("§dDu §7#"+rank+" §f"+lifetimePoints.getOrDefault(id,0)+"P §7"+lifetimeWins.getOrDefault(id,0)+"W").setScore(7);
            }
            obj.getScore((tick/20%2==0?"§5":"§d")+"§m--------------").setScore(4);
            obj.getScore("§d✦ LIVE Twitch WolfsfellTV").setScore(3);
            obj.getScore("§7Daily 17 UHR").setScore(2);
            obj.getScore("§c§lYouTube WolfsfellTVHD").setScore(1);
        }
    }
    private void teleportTeams() {
        for (String side : List.of("red","blue")) {
            List<Location> locations=teamSpawns.getOrDefault(side,List.of());
            int index=0;
            for (var entry : playerTeams.entrySet()) {
                if (!entry.getValue().equals(side)) continue;
                Player p=Bukkit.getPlayer(entry.getKey()); if (p==null) continue;
                Location spawn=locations.get(index++ % locations.size());
                p.teleport(spawn); p.setFallDistance(0); p.setSprinting(false);
                stamina.put(p.getUniqueId(),100.0); sprintUnlock.remove(p.getUniqueId()); restoreSpeed(p);
            }
        }
    }
    private void resetToCenter() {
        Location loc=ballSpawn;
        if (loc==null && activeArena!=null && arenas.containsKey(activeArena)) {
            Location[] a=arenas.get(activeArena);
            loc=a[0].clone().add(a[1]).multiply(0.5).add(0,1.5,0);
        }
        if (loc!=null) spawnBall(loc.clone().add(0,0.7,0));
    }
    private void beginCountdown(Player admin) {
        if (matchActive()) { admin.sendMessage("§cEin Match läuft bereits."); return; }
        if (activeArena==null || !arenas.containsKey(activeArena) || hoops.size()<2 || ballSpawn==null) {
            admin.sendMessage("§cVorher Arena, beide Körbe und Ball-Spawn einrichten."); return;
        }
        for (String side:List.of("red","blue")) {
            if (teamSpawns.getOrDefault(side,List.of()).isEmpty()) { admin.sendMessage("§cEs fehlen Spawnpunkte für "+teamLabel(side));return; }
            if (playerTeams.values().stream().noneMatch(side::equals)) { admin.sendMessage("§cBeide Teams brauchen mindestens einen Spieler.");return; }
        }
        for (UUID id:playerTeams.keySet()) if (Bukkit.getPlayer(id)==null) { admin.sendMessage("§cEin zugewiesener Spieler ist offline. /bc team clear oder remove.");return; }
        eventAdmin=admin.getUniqueId();
        scores.put("red",0);scores.put("blue",0);
        playerPoints.clear();playerSteals.clear();playerBlocks.clear();playerAssists.clear();
        clearBall(); teleportTeams(); dressTeams(); restartExclusiveTeam=null; restartDeadlineTick=-1; lastFinalCountdownSecond=-1; resetToCenter();
        matchPhase=MatchPhase.COUNTDOWN;countdownEndTick=tick+100;matchPaused=false;
        announce("§eMatch beginnt in 5 Sekunden! §7"+matchSeconds/60+" Minuten");refreshBoards();
    }
    private void finishMatch(boolean cancelled) {
        if (matchPhase==MatchPhase.IDLE) return;
        matchPhase=MatchPhase.FINISHED; matchPaused=false;
        clearBall(); restoreVests(); restartExclusiveTeam=null; restartDeadlineTick=-1;
        if (cancelled) announce("§cMatch abgebrochen.");
        else {
            int a=scores.getOrDefault("red",0),b=scores.getOrDefault("blue",0);
            announce("§6§lSPIELENDE! §c"+a+" §7: §9"+b+" §7– "+(a==b?"Unentschieden":teamLabel(a>b?"red":"blue")+" gewinnt!"));
            UUID mvp=playerPoints.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (mvp!=null) announce("§6Topscorer: §f"+Bukkit.getOfflinePlayer(mvp).getName()+" §7("+playerPoints.get(mvp)+" Punkte)");
        }
        if(!cancelled){
            for(var entry:playerPoints.entrySet())lifetimePoints.merge(entry.getKey(),entry.getValue(),Integer::sum);
            int a=scores.getOrDefault("red",0),b=scores.getOrDefault("blue",0);
            if(a!=b){String winner=a>b?"red":"blue";for(var entry:playerTeams.entrySet())if(winner.equals(entry.getValue()))lifetimeWins.merge(entry.getKey(),1,Integer::sum);}
            saveLifetimeStats();
            recordMatchHistory();
        }
        matchPhase=MatchPhase.IDLE;
        for(Player p:Bukkit.getOnlinePlayers()){restoreSpeed(p);stamina.remove(p.getUniqueId());sprintUnlock.remove(p.getUniqueId());}
        refreshBoards();
    }
    private void updateMatch() {
        if (matchPhase==MatchPhase.COUNTDOWN) {
            if (tick>=countdownEndTick) {
                matchPhase=MatchPhase.LIVE; matchEndTick=tick+matchSeconds*20;
                announce("§a§lTIP-OFF! §7Das Spiel läuft!");
            } else if ((countdownEndTick-tick)%20==0) {
                int sec=(countdownEndTick-tick)/20;
                for (UUID id:playerTeams.keySet()) { Player p=Bukkit.getPlayer(id); if(p!=null)p.sendTitle("§e"+sec,"§7Bereit machen",0,18,1); }
            }
        }
        if (matchPhase==MatchPhase.LIVE && matchPaused) matchEndTick++;
        if (matchPhase==MatchPhase.LIVE && !matchPaused) {
            int remaining=Math.max(0,(matchEndTick-tick+19)/20);
            if(remaining>=1 && remaining<=10 && remaining!=lastFinalCountdownSecond){
                lastFinalCountdownSecond=remaining;
                for(Player player:Bukkit.getOnlinePlayers()){
                    if(!playing(player))continue;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.85f, 0.85f+(10-remaining)*0.11f);
                    player.sendTitle(remaining<=3?"§c§l"+remaining:"§d§l"+remaining,"§7Noch "+remaining+" Sekunden",0,18,1);
                }
            }
            if(restartExclusiveTeam!=null && restartDeadlineTick>=0 && tick>=restartDeadlineTick){
                String next=opposite(restartExclusiveTeam);
                Location point=restartPoints.getOrDefault(next,ballSpawn);
                if(point!=null){spawnBall(point.clone().add(0,0.5,0));restartExclusiveTeam=next;restartDeadlineTick=-1;
                    announce("§c10 Sekunden abgelaufen! §7Ballbesitz wechselt zu "+teamLabel(next));}
            }
        }
        if (matchPhase==MatchPhase.LIVE && !matchPaused && tick>=matchEndTick) finishMatch(false);
        if (tick%20==0) refreshBoards();
    }
    private void recordScore(String hoopSide, int points) {
        if (matchPhase!=MatchPhase.LIVE) return;
        String scoringSide=opposite(hoopSide);
        if (lastShooter==null || !scoringSide.equals(playerTeams.get(lastShooter))) return;
        playerPoints.merge(lastShooter,points,Integer::sum);
        if (lastPasser!=null && !lastPasser.equals(lastShooter) && scoringSide.equals(playerTeams.get(lastPasser)) && tick-lastPassTick<120)
            playerAssists.merge(lastPasser,1,Integer::sum);
    }
    private String pendingRestartSide="red";
    private void afterScore() {
        if (matchPhase!=MatchPhase.LIVE) return;
        // Reset after the celebration; protects against the same ball scoring twice.
        // hoop-side is the conceding side; stored explicitly by score() below.
        Bukkit.getScheduler().runTaskLater(this, ()->{
            if(matchPhase!=MatchPhase.LIVE)return;
            String side=pendingRestartSide;
            Location point=restartPoints.getOrDefault(side,ballSpawn);
            if(point!=null){spawnBall(point.clone().add(0,0.5,0));restartExclusiveTeam=side;restartDeadlineTick=tick+200;announce("§eBallbesitz: "+teamLabel(side)+" §7(10 Sekunden)");}
        },35L);
    }
    private void trySteal(Player defender) {
        if(matchPhase!=MatchPhase.LIVE || matchPaused || !playing(defender) || carrier==null || carrier.equals(defender.getUniqueId()) || ball==null)return;
        Player holder=Bukkit.getPlayer(carrier);
        if(holder==null || holder.getWorld()!=defender.getWorld())return;
        UUID id=defender.getUniqueId();if(tick<defenseCooldown.getOrDefault(id,0))return;
        Vector origin=defender.getEyeLocation().toVector();
        // Match the rendered dribbling ball: actual display position may differ from logical ball.
        Location visual=(display!=null && display.isValid())?display.getLocation():ball;
        Vector target=visual.toVector().subtract(origin);
        double range=3.3;
        if(target.lengthSquared()>range*range || target.lengthSquared()<0.001)return;
        Vector look=defender.getEyeLocation().getDirection().normalize();
        // Aim at the visible ball, not the player's torso. 0.42-block hit radius.
        double forward=target.dot(look);
        if(forward<0 || forward>range)return;
        double miss=target.clone().subtract(look.multiply(forward)).lengthSquared();
        if(miss>0.92*0.92)return;
        if(tick<possessionProtection.getOrDefault(holder.getUniqueId(),0))return;
        defenseCooldown.put(id,tick+30);
        if(dunker!=null && dunker.equals(holder.getUniqueId())) {
            if(!defender.isOnGround()){
                dunker=null;dunkSide=null;dropCarrier(holder);
                playerBlocks.merge(id,1,Integer::sum);announce("§b§lDUNK GEBLOCKT! §f"+defender.getName());
            }
            return;
        }
        carrier=null;charging.remove(holder.getUniqueId());chargeTicks.remove(holder.getUniqueId());
        carry(defender);possessionProtection.put(id,tick+30);playerSteals.merge(id,1,Integer::sum);
        defender.sendActionBar("§a§lSTEAL!");holder.sendActionBar("§cBall gestohlen!");
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onDefense(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if(matchActive() && e.getEntity() instanceof Player)e.setCancelled(true);
    }
    @EventHandler public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;
        if(matchPhase==MatchPhase.LIVE && (e.getRightClicked() instanceof Player || e.getRightClicked().equals(display))) {
            e.setCancelled(true);trySteal(e.getPlayer());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEventBreak(BlockBreakEvent e) {
        if(matchActive() && playing(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEventPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if(matchActive() && playing(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEventDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if(matchActive() && playing(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler public void onMatchDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && matchActive() && playing(p))e.setCancelled(true);
    }
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
    private final Map<UUID, Integer> exhaustedMoveAttempt = new HashMap<>();
    private final Map<UUID, Integer> lastHorizontalMove = new HashMap<>();
    private final Map<UUID, Integer> jumpWindow = new HashMap<>();
    private final Map<UUID, Boolean> grounded = new HashMap<>();
    private final Map<UUID, Integer> sprintRecent = new HashMap<>();
    private UUID dunker;
    private String dunkSide;
    private int dunkEnds;
    private int dunkReadyTick;
    private static final int SHOT_CYCLE = 44;
    private static final int GREEN_CENTER = 26;
    private final Map<String, List<Location>> teamSpawns = new HashMap<>();
    private Location ballSpawn = null;
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
            List<Location> spawns = new ArrayList<>();
            for (Map<?, ?> row : getConfig().getMapList("team-spawns." + side)) {
                Location loc = decodeSpawn(row);
                if (loc != null) spawns.add(loc);
            }
            teamSpawns.put(side, spawns);
        }
        if (getConfig().isConfigurationSection("ball-spawn")) {
            ballSpawn = getConfig().getLocation("ball-spawn.location");
        }
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
        loadRestartPoints();
        loadLifetimeStats();
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::step, 1L, 1L);
        refreshBoards();getLogger().info("BasketballCraft 1.3 enabled. /bc help");
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (display != null && display.isValid()) display.remove();
        saveLifetimeStats();clearMatchBoards(); restoreVests();
        for (var entry:observerOriginalModes.entrySet()) {Player p=Bukkit.getPlayer(entry.getKey());if(p!=null)p.setGameMode(entry.getValue());}
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
            entity.setGlowing(true);
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
        if (!participantAllowed(p)) return;
        if(restartExclusiveTeam!=null && !restartExclusiveTeam.equals(playerTeams.get(p.getUniqueId())))return;
        restartExclusiveTeam=null; restartDeadlineTick=-1;
        carrier = p.getUniqueId();
        possessionProtection.put(p.getUniqueId(),tick+12);
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
            if (other.equals(p) || other.isDead() || other.getGameMode() == GameMode.SPECTATOR || (matchActive() && !sameTeam(p,other))) continue;
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
            lastPasser=p.getUniqueId();lastPassTick=tick;
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
        if (matchPhase==MatchPhase.LIVE && carrier!=null && !p.getUniqueId().equals(carrier) && (action==Action.RIGHT_CLICK_AIR || action==Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);trySteal(p); return;
        }
        if(matchActive() && !participantAllowed(p) && wand==null){event.setCancelled(true);return;}
        if (matchPhase==MatchPhase.COUNTDOWN && playing(p)) {event.setCancelled(true);return;}
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
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if(originalChestplates.containsKey(event.getPlayer().getUniqueId())) event.getPlayer().getInventory().setChestplate(originalChestplates.remove(event.getPlayer().getUniqueId()));
        if (matchPhase==MatchPhase.LIVE && playing(event.getPlayer())) {matchPaused=true;pauseRemaining=Math.max(0,matchEndTick-tick);announce("§eSpiel pausiert: "+event.getPlayer().getName()+" hat den Server verlassen.");}
        observerOriginalModes.remove(event.getPlayer().getUniqueId());
        matchBoards.remove(event.getPlayer().getUniqueId());oldBoards.remove(event.getPlayer().getUniqueId());matchObjectives.remove(event.getPlayer().getUniqueId());
 hoopViewers.remove(event.getPlayer().getUniqueId()); restoreSpeed(event.getPlayer()); stamina.remove(event.getPlayer().getUniqueId()); lastHorizontalMove.remove(event.getPlayer().getUniqueId()); exhaustedMoveAttempt.remove(event.getPlayer().getUniqueId()); grounded.remove(event.getPlayer().getUniqueId()); jumpWindow.remove(event.getPlayer().getUniqueId()); charging.remove(event.getPlayer().getUniqueId()); chargeTicks.remove(event.getPlayer().getUniqueId()); dropCarrier(event.getPlayer()); }
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
        updateMatch();
        updateStamina();
        updateDunk();
        if(tick%5==0 && matchPhase==MatchPhase.LIVE){
            for(Player player:Bukkit.getOnlinePlayers()){
                int remaining=defenseCooldown.getOrDefault(player.getUniqueId(),0)-tick;
                if(remaining>0 && playing(player) && !charging.containsKey(player.getUniqueId())){
                    player.sendActionBar("§d✦ §7STEAL §c"+String.format(Locale.ROOT,"%.1f",remaining/20.0)+"s §d✦");
                }
            }
        }
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
                if (participantAllowed(p) && p.getLocation().distanceSquared(ball) <= Math.pow(setting("pickup-radius", 1.4), 2)) { carry(p); break; }
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
            // Ball-only invisible arena walls; no barrier blocks placed in the world.
            if(matchActive() && activeArena!=null && arenas.containsKey(activeArena) && !insideCourt(next)) {
                Location[] bounds=courtBounds.getOrDefault(activeArena,arenas.get(activeArena));
                double minX=Math.min(bounds[0].getX(),bounds[1].getX())+0.3,maxX=Math.max(bounds[0].getX(),bounds[1].getX())+0.7;
                double minZ=Math.min(bounds[0].getZ(),bounds[1].getZ())+0.3,maxZ=Math.max(bounds[0].getZ(),bounds[1].getZ())+0.7;
                if(next.getX()<minX || next.getX()>maxX)velocity.setX(-velocity.getX()*0.78);
                if(next.getZ()<minZ || next.getZ()>maxZ)velocity.setZ(-velocity.getZ()*0.78);
                ball.setX(Math.max(minX,Math.min(maxX,ball.getX())));
                ball.setZ(Math.max(minZ,Math.min(maxZ,ball.getZ())));
                break;
            }
            if (matchPhase==MatchPhase.LIVE && !passFlight && airborne && lastShooter!=null && !scoredThisFlight) {
                for (Player defender:next.getWorld().getPlayers()) {
                    if (!playing(defender) || Objects.equals(playerTeams.get(defender.getUniqueId()),playerTeams.get(lastShooter)))continue;
                    if (defender.isOnGround() || defender.getVelocity().getY()<=0.06)continue;
                    if (defender.getLocation().add(0,1.45,0).distanceSquared(next)>1.4)continue;
                    velocity=defender.getEyeLocation().getDirection().multiply(0.4).add(new Vector(0,0.22,0));
                    lastShooter=null;scoredThisFlight=true;
                    playerBlocks.merge(defender.getUniqueId(),1,Integer::sum);
                    defender.sendActionBar("§b§lBLOCK!");announce("§bBlock von §f"+defender.getName());break;
                }
            }
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
                    if (candidate.isDead() || candidate.getGameMode() == GameMode.SPECTATOR || !participantAllowed(candidate)) continue;
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
                if (participantAllowed(p) && p.getLocation().distanceSquared(ball) < 2.25 && velocity.lengthSquared() < 0.12) { carry(p); break; }
            }
        }
    }
    private void updateStamina() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if(!staminaEnabled(p)) {restoreSpeed(p);stamina.remove(id);sprintUnlock.remove(id);exhaustedMoveAttempt.remove(id);continue;}
            boolean onGround = p.isOnGround();
            boolean wasGrounded = grounded.getOrDefault(id, onGround);
            if (wasGrounded && !onGround && p.getVelocity().getY() > 0.08) jumpWindow.put(id, tick + 12);
            grounded.put(id, onGround);
            if (p.isSprinting()) sprintRecent.put(id, tick);

            double value = energy(p);
            boolean exhausted = sprintUnlock.containsKey(id);
            if (exhausted && value >= 30.0) {
                sprintUnlock.remove(id);
                exhaustedMoveAttempt.remove(id);
                exhausted = false;
            }
            // Apply fatigue in BOTH directions: as stamina falls and as it recovers.
            // A sprinting player otherwise bypasses walk-speed slowdown while draining.
            boolean fatigueSprintBlocked = value <= 30.0 || exhausted;
            boolean sprinting = p.isSprinting() && !fatigueSprintBlocked;
            if (fatigueSprintBlocked && p.isSprinting()) p.setSprinting(false);
            boolean moving = p.getVelocity().clone().setY(0).lengthSquared() > 0.004;
            // PlayerMoveEvent records actual horizontal movement even when sprint is disabled.
            // Velocity alone is unreliable: slowed players can have tiny velocity while walking.
            boolean attemptedMove = tick - exhaustedMoveAttempt.getOrDefault(id, -10000) <= 2;
            boolean recentlyMoved = tick - lastHorizontalMove.getOrDefault(id, -10000) <= 3;
            boolean activelyMoving = moving || attemptedMove || recentlyMoved;
            if (activelyMoving) {
                double drain;
                if (sprinting) {
                    drain = id.equals(carrier)
                        ? getConfig().getDouble("stamina.sprint-with-ball-per-second", 9.0)
                        : getConfig().getDouble("stamina.sprint-per-second", 5.0);
                    if (value < 40.0) drain *= 1.5;
                } else {
                    drain = id.equals(carrier)
                        ? getConfig().getDouble("stamina.walk-with-ball-per-second", 3.5)
                        : getConfig().getDouble("stamina.walk-per-second", 2.5);
                }
                value -= drain / 20.0;
            } else if (p.isOnGround()) {
                // Standing still is the ONLY way to gain stamina.
                double regen = getConfig().getDouble("stamina.idle-regen-per-second", 14.0);
                if (id.equals(carrier)) regen *= getConfig().getDouble("stamina.ball-regen-multiplier", 0.6);
                else regen *= getConfig().getDouble("stamina.no-ball-regen-multiplier", 1.35);
                value += regen / 20.0;
            }
            value = Math.max(0.0, Math.min(100.0, value));
            if (value <= 0.01) {
                value = 0.0;
                if (!exhausted) exhaustedMoveAttempt.put(id, tick);
                sprintUnlock.put(id, 30);
                exhausted = true;
                p.setSprinting(false);
            }
            // Hard freeze only at 0-5; above that the player can walk again.
            // Slowness scales up gradually and is still noticeable at 20 stamina.
            if (value <= 30.0) {
                if (p.isSprinting()) p.setSprinting(false);
                baseWalkSpeed.putIfAbsent(id, p.getWalkSpeed());
                float original = baseWalkSpeed.get(id);
                double multiplier = value <= 5.0 ? 0.0
                    : 0.35 + 0.65 * ((value - 5.0) / 25.0);
                p.setWalkSpeed((float) Math.max(0.0, Math.min(1.0, original * multiplier)));
            } else restoreSpeed(p);
            stamina.put(id, value);
            if (tick % 10 == 0 && !charging.containsKey(id)) {
                int bars = (int)Math.round(value / 10.0);
                StringBuilder bar = new StringBuilder("§d⚡ §7[");
                for (int i = 0; i < 10; i++) bar.append(i < bars ? (value < 25 ? "§c▌" : "§d▌") : "§8▌");
                bar.append("§7] §f").append((int)Math.round(value)).append("/100");
                if (value <= 5.0) bar.append(" §cSTEH BLEIBEN!");
                else if (exhausted) bar.append(" §6SPRINT GESPERRT");
                if (id.equals(carrier)) bar.append(" §7| §d🏀");
                p.sendActionBar(bar.toString());
            }
        }
    }
    private void restoreSpeed(Player p) {
        Float original = baseWalkSpeed.remove(p.getUniqueId());
        if (original != null && p.isOnline()) p.setWalkSpeed(original);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExhaustedMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (e.getTo() == null) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz > 0.000001) lastHorizontalMove.put(id, tick);
        if(courtLocked && matchActive() && !courtAccess(p) && !insideCourt(from) && insideCourt(to)) {
            e.setTo(from);if(tick>=boundaryWarnings.getOrDefault(id,0)){p.sendMessage("§cDas Basketballfeld ist nur für Team Rot, Team Blau und Admins zugänglich.");boundaryWarnings.put(id,tick+60);}return;
        }
        if(courtLocked && matchActive() && !courtAccess(p) && insideCourt(from)) {
            // Unauthorized player was teleported into the court: return to their last outside position.
            Location safe=from.clone();Location[] c=arenas.get(activeArena);
            safe.setX(Math.min(c[0].getX(),c[1].getX())-1.5);e.setTo(safe);return;
        }
        if (!staminaEnabled(p) || energy(p) > 5.0) return;
        // Preserve looking around and vertical motion, but disallow horizontal walking.
        if (dx * dx + dz * dz < 0.000001) return;
        exhaustedMoveAttempt.put(id, tick);
        Location restricted = to.clone();
        restricted.setX(from.getX());
        restricted.setZ(from.getZ());
        e.setTo(restricted);
        Vector v = p.getVelocity();
        p.setVelocity(new Vector(0, v.getY(), 0));
    }
    @EventHandler public void onSprint(PlayerToggleSprintEvent e) {
        if (staminaEnabled(e.getPlayer()) && e.isSprinting() && (energy(e.getPlayer()) <= 30.0 || sprintUnlock.containsKey(e.getPlayer().getUniqueId()))) {
            e.setCancelled(true);
            e.getPlayer().setSprinting(false);
        }
    }
    private void updateDunk() {
        if (dunker == null) return;
        Player p = Bukkit.getPlayer(dunker);
        Location hoop = dunkSide == null ? null : hoops.get(dunkSide);
        if (p == null || !p.isOnline() || hoop == null || !p.getWorld().equals(hoop.getWorld()) ||
                !dunker.equals(carrier)) { dunker = null; dunkSide = null; return; }
        p.setFallDistance(0);
        if (matchPhase==MatchPhase.LIVE) for (Player defender:p.getWorld().getPlayers()) {
            if (!playing(defender) || sameTeam(defender,p) || defender.isOnGround())continue;
            if (defender.getLocation().distanceSquared(p.getLocation())>3.0)continue;
            if (defender.getVelocity().getY()<=0.06)continue;
            dunker=null;dunkSide=null;dropCarrier(p);
            playerBlocks.merge(defender.getUniqueId(),1,Integer::sum);
            announce("§b§lDUNK GEBLOCKT! §f"+defender.getName());return;
        }
        double dx = p.getLocation().getX() - hoop.getX();
        double dz = p.getLocation().getZ() - hoop.getZ();
        double distance = Math.hypot(dx, dz);
        // The player's reach is roughly 2 blocks above their feet.
        if (tick >= dunkReadyTick && distance <= 1.55 &&
                p.getLocation().getY() + 2.15 >= hoop.getY() && p.getLocation().getY() <= hoop.getY() + 0.4) {
            lastShooter = p.getUniqueId(); shotOrigin = p.getLocation().clone();
            // Dunk is worth two, regardless of the starting distance.
            if (matchPhase==MatchPhase.LIVE && !opposite(dunkSide).equals(playerTeams.get(p.getUniqueId()))) {dunker=null;dunkSide=null;return;}
            scores.merge(matchPhase==MatchPhase.LIVE?opposite(dunkSide):dunkSide, 2, Integer::sum);
            recordScore(dunkSide,2);pendingRestartSide=dunkSide;afterScore();
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
    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(this,this::refreshBoards,2L); }
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
        if (matchPhase==MatchPhase.COUNTDOWN || (matchPhase==MatchPhase.LIVE && matchPaused))return;
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
        if (shotOrigin!=null && ballSpawn!=null && hoop!=null && shotOrigin.getWorld().equals(ballSpawn.getWorld())
            && hoop.getWorld().equals(ballSpawn.getWorld())) {
            Vector axis=hoop.toVector().subtract(ballSpawn.toVector());axis.setY(0);
            Vector shot=shotOrigin.toVector().subtract(ballSpawn.toVector());shot.setY(0);
            if(axis.lengthSquared()>1 && shot.dot(axis)<-0.15)points=4;
        }
        if (matchPhase==MatchPhase.LIVE && (lastShooter==null || !opposite(side).equals(playerTeams.get(lastShooter))))return;
        scores.merge(matchPhase==MatchPhase.LIVE?opposite(side):side, points, Integer::sum);
        recordScore(side,points);
        pendingRestartSide=side;
        afterScore();
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
    private Location decodeSpawn(Map<?, ?> row) {
        try {
            World w = Bukkit.getWorld(String.valueOf(row.get("world")));
            if (w == null) return null;
            return new Location(w, ((Number)row.get("x")).doubleValue(), ((Number)row.get("y")).doubleValue(), ((Number)row.get("z")).doubleValue(), ((Number)row.get("yaw")).floatValue(), ((Number)row.get("pitch")).floatValue());
        } catch (Exception ex) { return null; }
    }
    private Map<String,Object> encodeSpawn(Location l) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("world", l.getWorld().getName()); row.put("x", l.getX()); row.put("y", l.getY()); row.put("z", l.getZ());
        row.put("yaw", (double)l.getYaw()); row.put("pitch", (double)l.getPitch()); return row;
    }
    private void persistSpawns() {
        for (String side : List.of("red", "blue")) {
            List<Map<String,Object>> rows = new ArrayList<>();
            for (Location loc : teamSpawns.getOrDefault(side, List.of())) rows.add(encodeSpawn(loc));
            getConfig().set("team-spawns."+side, rows);
        }
        getConfig().set("ball-spawn.location", ballSpawn);
        saveConfig();
    }
    private void showSpawns(Player p) {
        for (String side : List.of("red", "blue")) {
            Particle.DustOptions dust = new Particle.DustOptions(side.equals("red") ? Color.RED : Color.AQUA, 1.6f);
            int index = 0;
            for (Location loc : teamSpawns.getOrDefault(side,List.of())) {
                index++;
                if (!loc.getWorld().equals(p.getWorld())) continue;
                for (int i=0;i<32;i++) {
                    double a=2*Math.PI*i/32;
                    p.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*.65,.15,Math.sin(a)*.65),1,0,0,0,0,dust);
                }
                p.spawnParticle(Particle.END_ROD,loc.clone().add(0,1.5,0),6,.1,.2,.1,0);
            }
        }
        if (ballSpawn != null && ballSpawn.getWorld().equals(p.getWorld()))
            p.spawnParticle(Particle.END_ROD, ballSpawn, 20, .35,.2,.35,0);
    }
    private void showArenaSelection(Player p) {
        Location a=arenaPos1.get(p.getUniqueId()), b=arenaPos2.get(p.getUniqueId());
        if(a!=null && a.getWorld().equals(p.getWorld()))p.spawnParticle(Particle.END_ROD,a.clone().add(.5,.5,.5),25,.25,.25,.25,0);
        if(b!=null && b.getWorld().equals(p.getWorld()))p.spawnParticle(Particle.FLAME,b.clone().add(.5,.5,.5),25,.25,.25,.25,0);
        if(a!=null && b!=null && a.getWorld().equals(b.getWorld()) && a.getWorld().equals(p.getWorld())) {
            double minX=Math.min(a.getBlockX(),b.getBlockX()), maxX=Math.max(a.getBlockX(),b.getBlockX())+1;
            double minY=Math.min(a.getBlockY(),b.getBlockY()), maxY=Math.max(a.getBlockY(),b.getBlockY())+1;
            double minZ=Math.min(a.getBlockZ(),b.getBlockZ()), maxZ=Math.max(a.getBlockZ(),b.getBlockZ())+1;
            for (int i=0;i<=24;i++) {
                double t=i/24.0;
                for(double y:new double[]{minY,maxY})for(double z:new double[]{minZ,maxZ})p.spawnParticle(Particle.END_ROD,minX+(maxX-minX)*t,y,z,1,0,0,0,0);
                for(double y:new double[]{minY,maxY})for(double x:new double[]{minX,maxX})p.spawnParticle(Particle.END_ROD,x,y,minZ+(maxZ-minZ)*t,1,0,0,0,0);
                for(double x:new double[]{minX,maxX})for(double z:new double[]{minZ,maxZ})p.spawnParticle(Particle.END_ROD,x,minY+(maxY-minY)*t,z,1,0,0,0,0);
            }
        }
    }
    private void editBlock(Player p, Block block, boolean undo) {
        String mode=editMode.getOrDefault(p.getUniqueId(),"arena1");
        Location loc=block.getLocation();
        if (mode.equals("arena1") || mode.equals("arena2")) {
            if (undo) { (mode.equals("arena1") ? arenaPos1 : arenaPos2).remove(p.getUniqueId()); p.sendMessage("§eEcke entfernt. Noch nichts gespeichert."); }
            else { (mode.equals("arena1") ? arenaPos1 : arenaPos2).put(p.getUniqueId(),loc);
                p.sendMessage("§d"+(mode.equals("arena1")?"Ecke 1":"Ecke 2")+" gesetzt: §f"+loc.getBlockX()+" "+loc.getBlockY()+" "+loc.getBlockZ()); }
            showArenaSelection(p);
            if (arenaPos1.containsKey(p.getUniqueId()) && arenaPos2.containsKey(p.getUniqueId()))
                p.sendMessage("§aBeide Ecken gesetzt! §7Jetzt erst §f/bc arena save <name> §7ausführen.");
            else p.sendMessage("§eNoch nicht gespeichert! §7Setze auch die andere Ecke über /bc setup.");
            return;
        }
        if (mode.equals("spawn-red") || mode.equals("spawn-blue")) {
            String side=mode.substring(6); List<Location> list=teamSpawns.computeIfAbsent(side,k->new ArrayList<>());
            if (undo) { if(!list.isEmpty())list.remove(list.size()-1); }
            else { Location spot=block.getLocation().add(.5,1,.5); spot.setYaw(p.getLocation().getYaw()); spot.setPitch(0); list.add(spot); }
            persistSpawns(); showSpawns(p);
            p.sendMessage("§aTeam "+(side.equals("red")?"1":"2")+": §f"+list.size()+" Spawnpunkte gespeichert. §7Shift+Rechtsklick entfernt letzten.");
            return;
        }
        if (mode.equals("spawn-ball")) {
            ballSpawn=undo?null:block.getLocation().add(.5,1,.5);
            persistSpawns();showSpawns(p);p.sendMessage(undo?"§eBall-Spawn entfernt.":"§aBall-Spawn gespeichert.");return;
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
        org.bukkit.inventory.Inventory inv=Bukkit.createInventory(null,36,"BasketballCraft | Court Editor");
        String[] names={"§dArena Ecke 1", "§dArena Ecke 2", "§cKorb Team 1", "§9Korb Team 2", "§cDreierlinie Team 1", "§9Dreierlinie Team 2", "§cTeam 1: Spawn hinzufügen", "§9Team 2: Spawn hinzufügen", "§eBall-Spawn"};
        Material[] materials={Material.WOODEN_AXE,Material.WOODEN_AXE,Material.BLAZE_ROD,Material.STICK,Material.RED_DYE,Material.BLUE_DYE,Material.RED_BED,Material.BLUE_BED,Material.SLIME_BALL};
        int[] slots={10,11,12,14,15,16,20,22,24};
        for(int i=0;i<names.length;i++) { ItemStack item=new ItemStack(materials[i]); ItemMeta meta=item.getItemMeta();meta.setDisplayName(names[i]);meta.setLore(List.of("§7Klicken: Modus wählen", "§7Editor-Stab: Rechtsklick setzen", "§7Shift+Rechtsklick: zurück", i<2?"§eArena erst mit /bc arena save <name> speichern":"§aMarkierung wird sofort gespeichert"));item.setItemMeta(meta);inv.setItem(slots[i],item); }
        ItemStack info=new ItemStack(Material.BOOK);ItemMeta m=info.getItemMeta();m.setDisplayName("§e§lAnleitung");m.setLore(List.of("§71. Beide Arena-Ecken einzeln setzen", "§72. /bc arena save <name>","§73. Team-Spawns beliebig oft setzen", "§74. /bc spawn show für Vorschau"));info.setItemMeta(m);inv.setItem(31,info);
        setupInventories.put(p.getUniqueId(),inv); p.openInventory(inv);
    }
    @EventHandler public void onSetupClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory()!=setupInventories.get(p.getUniqueId())) return;
        e.setCancelled(true); if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        String mode=switch(e.getRawSlot()) {case 10->"arena1";case 11->"arena2";case 12->"red";case 14->"blue";case 15->"three-red";case 16->"three-blue";case 20->"spawn-red";case 22->"spawn-blue";case 24->"spawn-ball";default->null;};
        if(mode==null)return;
        editMode.put(p.getUniqueId(),mode);p.closeInventory();
        p.sendMessage("§dCourt-Editor §7Modus: §f"+mode+" §7| Rechtsklick setzen, Shift+Rechtsklick zurück, Linksklick Menü");
        if(mode.startsWith("arena"))p.sendMessage("§eArena-Ecken werden NICHT automatisch gespeichert. §7Erst beide markieren, dann /bc arena save <name>.");
        if(p.getInventory().all(Material.GOLDEN_HOE).values().stream().noneMatch(i->"editor".equals(wandType(i))))p.getInventory().addItem(wand("editor"));
    }
    @EventHandler public void onSetupClose(InventoryCloseEvent e) {
        if(e.getPlayer() instanceof Player p && e.getInventory()==setupInventories.get(p.getUniqueId()))setupInventories.remove(p.getUniqueId());
    }
    private int parsePage(String raw) {try{return Integer.parseInt(raw);}catch(NumberFormatException e){return 1;}}
    private void help(CommandSender sender,int page) {
        String[][] pages={
            {"§d§lBasketballCraft 0.8.3 §7- Hilfe 1/4", "§e/bc help [1-4] §7- Befehlsliste", "§e/bc setup §7- Court-Editor-Menü", "§e/bc spawn add <1|2> §7- Teamspawn an eigener Position", "§e/bc wand §7- Editor-Stab", "§e/bc ball §7- Ball spawnen", "§e/bc reset §7- Punkte und Ball zurücksetzen", "§e/bc score §7- Punktestand"},
            {"§d§lBasketballCraft §7- Hilfe 2/4", "§e/bc arena save <name> §7- Nach beiden Ecken speichern", "§e/bc spawn list|show §7- Spawnpunkte", "§e/bc arena list §7- Hallen auflisten", "§e/bc arena select <name> §7- Halle wählen", "§e/bc arena show §7- Hallengrenzen", "§e/bc arena delete <name> §7- Halle löschen", "§e/bc showhoops §7- Korbringe an/aus"},
            {"§d§lBasketballCraft §7- Hilfe 3/4", "§e/bc three show §7- Dreierlinien anzeigen", "§e/bc spawn remove <1|2> [nummer] §7- Spawn löschen", "§e/bc spawn clear <1|2> §7- Teamspawns löschen", "§e/bc spawn ball §7- Ballspawn hier setzen", "§e/bc three undo <1|2> §7- Letzten Punkt löschen", "§e/bc three clear <1|2> §7- Linie löschen", "§e/bc wand 1|2|arena §7- Alte Markierungsstäbe", "§e/bc sethoop red|blue §7- Ring auf Augenhöhe", "§7Linksklick: Wurf | Rechtsklick: Pass", "§7Sprint+Sprung+Linksklick: Dunk"},
            {"§d§lBasketballCraft 1.0 §7- Hilfe 4/4", "§e/bc team add <spieler> <1|2> §7- Team zuweisen", "§e/bc team remove <spieler> §7- Entfernen", "§e/bc team list|clear §7- Teams anzeigen/leeren", "§e/bc match time <minuten> §7- Matchdauer (Standard 5)", "§e/bc match start|stop §7- Match starten/beenden", "§e/bc match pause|resume §7- Pausieren/Fortsetzen", "§e/bc match status §7- Status", "§e/bc spectate §7- Admin-Zuschauer", "§e/bc stats [spieler] §7- Statistiken", "§7Steal: Genau auf Ball zielen + Rechtsklick"},
            {"§d§lBasketballCraft 1.1 §7- Hilfe 5/5", "§e/bc restart set <1|2> §7- Ball-Resetpunkt setzen", "§e/bc restart show §7- Resetpunkte anzeigen", "§e/bc arena lock|unlock §7- Zuschauer-Feldsperre", "§e/bc arena bounds 1|2 §7- Außenlinien setzen", "§7Ball prallt an Arenagrenzen ab", "§7Rechtsklick auf Ball: Steal (auch Teamkollegen)", "§7Event: kein PvP, Teamwesten Rot/Blau", "§e/bc history [anzahl] §7- Letzte Matches", "§e/bc leaderboard reset CONFIRM §7- Stats löschen"}
        };
        int idx=Math.max(1,Math.min(5,page))-1;
        for(String line:pages[idx])sender.sendMessage(line);
        sender.sendMessage("§8-------------------- §7Seite "+(idx+1)+"/5 §8--------------------");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> options;
        if(args.length==1)options=List.of("help","setup","wand","ball","start","reset","score","arena","three","spawn","showhoops","sethoop","team","match","spectate","stats","restart","history","leaderboard");
        else if(args.length==2) options=switch(args[0].toLowerCase(Locale.ROOT)) {
            case "help"->List.of("1","2","3","4","5");case "team"->List.of("add","remove","list","clear");case "match"->List.of("start","stop","pause","resume","time","status");case "wand"->List.of("1","2","arena","editor");case "arena"->List.of("save","list","select","show","delete","lock","unlock","bounds");case "spawn"->List.of("add","list","show","remove","clear","ball");case "three"->List.of("show","undo","clear");case "sethoop"->List.of("red","blue");case "restart"->List.of("set","show");default->List.of();};
        else if(args.length==3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("bounds"))options=List.of("1","2","show","clear");
        else if(args.length==3 && args[0].equalsIgnoreCase("restart") && args[1].equalsIgnoreCase("set"))options=List.of("1","2");
        else if(args.length==3 && args[0].equalsIgnoreCase("team") && List.of("add","remove").contains(args[1]))options=Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        else if(args.length==4 && args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("add"))options=List.of("1","2");
        else if(args.length==3 && (args[0].equalsIgnoreCase("three") || args[0].equalsIgnoreCase("spawn")))options=List.of("1","2");
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
        if (args[0].equalsIgnoreCase("stats")) {
            UUID id = sender instanceof Player p ? p.getUniqueId() : null;
            if (args.length>1) { Player p=Bukkit.getPlayerExact(args[1]); if(p!=null)id=p.getUniqueId(); }
            if(id==null){sender.sendMessage("§cSpieler nicht gefunden.");return true;}
            sender.sendMessage("§dStats: §f"+Bukkit.getOfflinePlayer(id).getName()+" §7Punkte "+playerPoints.getOrDefault(id,0)+" | Assists "+playerAssists.getOrDefault(id,0)+" | Steals "+playerSteals.getOrDefault(id,0)+" | Blocks "+playerBlocks.getOrDefault(id,0));return true;
        }
        if (!sender.hasPermission("basketball.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
        if (args[0].equalsIgnoreCase("history")) {
            int count=5;
            if(args.length>1)try{count=Math.max(1,Math.min(20,Integer.parseInt(args[1])));}catch(NumberFormatException ignored){}
            showMatchHistory(sender,count);return true;
        }
        if(args[0].equalsIgnoreCase("leaderboard") && args.length>=2 && args[1].equalsIgnoreCase("reset")) {
            if(matchActive()){sender.sendMessage("§cErst Match beenden.");return true;}
            if(args.length<3 || !args[2].equalsIgnoreCase("CONFIRM")){
                sender.sendMessage("§cACHTUNG: Löscht alle Gesamtpunkte und Siege! §e/bc leaderboard reset CONFIRM");return true;
            }
            lifetimePoints.clear();lifetimeWins.clear();saveLifetimeStats();refreshBoards();
            sender.sendMessage("§aLeaderboard zurückgesetzt. Match-History bleibt gespeichert.");return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can configure the court."); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "restart" -> {
                if(args.length>=3 && args[1].equalsIgnoreCase("set") && List.of("1","2").contains(args[2])) {
                    String side=args[2].equals("1")?"red":"blue";saveRestartPoint(side,p.getLocation());p.sendMessage("§aRestart-Punkt "+teamLabel(side)+" gesetzt.");
                } else if(args.length>=2 && args[1].equalsIgnoreCase("show")) {
                    for(var entry:restartPoints.entrySet())p.sendMessage(teamLabel(entry.getKey())+" §7Restart: §f"+entry.getValue().toVector());
                } else p.sendMessage("§e/bc restart set <1|2> | show");
            }
            case "team" -> {
                if(args.length<2){p.sendMessage("§e/bc team add <player> <1|2> | remove <player> | list | clear");break;}
                String sub=args[1].toLowerCase(Locale.ROOT);
                if(sub.equals("list")) {
                    for(String side:List.of("red","blue")) {
                        String names=playerTeams.entrySet().stream().filter(x->x.getValue().equals(side)).map(x->Optional.ofNullable(Bukkit.getOfflinePlayer(x.getKey()).getName()).orElse("? ")).reduce((a,b)->a+", "+b).orElse("keine");
                        p.sendMessage(teamLabel(side)+" §7: §f"+names);
                    }
                } else if(sub.equals("clear") && !matchActive()) {playerTeams.clear();p.sendMessage("§aTeamzuweisungen gelöscht.");}
                else if(sub.equals("add") && args.length>=4 && !matchActive()) {
                    Player target=Bukkit.getPlayerExact(args[2]);
                    if(target==null || !List.of("1","2").contains(args[3])){p.sendMessage("§cSpieler online und Team 1|2 angeben.");break;}
                    playerTeams.put(target.getUniqueId(),args[3].equals("1")?"red":"blue");
                    announce("§f"+target.getName()+" §7→ "+teamLabel(playerTeams.get(target.getUniqueId())));
                } else if(sub.equals("remove") && args.length>=3 && !matchActive()) {
                    Player target=Bukkit.getPlayerExact(args[2]);if(target!=null)playerTeams.remove(target.getUniqueId());p.sendMessage("§aTeamzuweisung entfernt.");
                } else p.sendMessage("§cTeam kann während eines Matches nicht geändert werden. /bc team add <spieler> <1|2>");
            }
            case "match" -> {
                if(args.length<2){p.sendMessage("§e/bc match start|stop|pause|resume|time <minuten>|status");break;}
                switch(args[1].toLowerCase(Locale.ROOT)) {
                    case "start" -> beginCountdown(p);
                    case "stop" -> finishMatch(true);
                    case "pause" -> {if(matchPhase==MatchPhase.LIVE){matchPaused=true;pauseRemaining=Math.max(0,matchEndTick-tick);announce("§eMatch pausiert.");}}
                    case "resume" -> {if(matchPhase==MatchPhase.LIVE && matchPaused){matchPaused=false;matchEndTick=tick+pauseRemaining;announce("§aMatch fortgesetzt.");}}
                    case "status" -> p.sendMessage("§dStatus: §f"+matchPhase+" §7| Teams: "+playerTeams.size()+" §7| "+scores.get("red")+":"+scores.get("blue"));
                    case "time" -> {if(args.length<3 || matchActive()){p.sendMessage("§c/bc match time <1-30> (nur vor Spielstart)");break;}try{int min=Integer.parseInt(args[2]);if(min<1||min>30)throw new NumberFormatException();matchSeconds=min*60;p.sendMessage("§aMatchdauer: "+min+" Minuten");}catch(NumberFormatException ex){p.sendMessage("§c1 bis 30 Minuten eingeben.");}}
                    default -> p.sendMessage("§c/bc match start|stop|pause|resume|time|status");
                }
            }
            case "spectate" -> {
                if(!observerOriginalModes.containsKey(p.getUniqueId())) {observerOriginalModes.put(p.getUniqueId(),p.getGameMode());p.setGameMode(GameMode.SPECTATOR);p.sendMessage("§aZuschauermodus aktiv. /bc spectate zum Beenden.");}
                else {p.setGameMode(observerOriginalModes.remove(p.getUniqueId()));p.sendMessage("§aZuschauermodus beendet.");}
            }
            case "setup" -> { openSetup(p); }
            case "spawn" -> {
                if(args.length<2){p.sendMessage("§e/bc spawn add <1|2> | list | show | remove <1|2> [nummer] | clear <1|2> | ball");break;}
                String sub=args[1].toLowerCase(Locale.ROOT);
                if(sub.equals("show")){showSpawns(p);p.sendMessage("§aSpawnpunkte als Partikel angezeigt.");break;}
                if(sub.equals("list")){
                    for(String side:List.of("red","blue")){List<Location> points=teamSpawns.getOrDefault(side,List.of());p.sendMessage("§eTeam "+(side.equals("red")?"1":"2")+": §f"+points.size()+" Spawns");int i=0;for(Location l:points){i++;p.sendMessage("§7  "+i+". "+l.getWorld().getName()+" "+l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ());}}
                    p.sendMessage("§eBall-Spawn: §f"+(ballSpawn==null?"nicht gesetzt":ballSpawn.getBlockX()+" "+ballSpawn.getBlockY()+" "+ballSpawn.getBlockZ()));break;
                }
                if(sub.equals("ball")){ballSpawn=p.getLocation().clone();persistSpawns();showSpawns(p);p.sendMessage("§aBall-Spawn auf deiner Position gespeichert.");break;}
                if(args.length<3 || !List.of("1","2").contains(args[2])){p.sendMessage("§cBitte Team 1 oder 2 angeben.");break;}
                String side=args[2].equals("1")?"red":"blue";
                List<Location> points=teamSpawns.computeIfAbsent(side,k->new ArrayList<>());
                if(sub.equals("add")){points.add(p.getLocation().clone());p.sendMessage("§aSpawn "+points.size()+" für Team "+args[2]+" gespeichert (inklusive Blickrichtung).");}
                else if(sub.equals("clear")){points.clear();p.sendMessage("§eTeam-Spawns gelöscht.");}
                else if(sub.equals("remove")){
                    int index=points.size()-1;
                    if(args.length>=4)try{index=Integer.parseInt(args[3])-1;}catch(NumberFormatException ignored){index=-1;}
                    if(index<0||index>=points.size()){p.sendMessage("§cSpawn-Nummer ungültig. /bc spawn list");break;}
                    points.remove(index);p.sendMessage("§aSpawn entfernt.");
                } else {p.sendMessage("§cUnbekannter Spawn-Befehl.");break;}
                persistSpawns();showSpawns(p);
            }

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
                if (args.length < 2) { p.sendMessage("§e/bc arena save <name> | show | list | select <name> | delete <name> | lock | unlock"); return true; }
                String sub = args[1].toLowerCase(Locale.ROOT);
                if(sub.equals("bounds")) {
                    if(args.length<3){p.sendMessage("§e/bc arena bounds 1|2|show|clear");break;}
                    String action=args[2].toLowerCase(Locale.ROOT);
                    if(action.equals("1")){courtFirstCorners.put(p.getUniqueId(),p.getLocation().clone());p.sendMessage("§aAußenlinie Ecke 1 gesetzt. Stelle dich zur gegenüberliegenden Ecke: /bc arena bounds 2");}
                    else if(action.equals("2")){
                        Location first=courtFirstCorners.get(p.getUniqueId());if(first==null || first.getWorld()!=p.getWorld()){p.sendMessage("§cZuerst /bc arena bounds 1");break;}
                        if(activeArena==null){p.sendMessage("§cZuerst Arena auswählen.");break;}
                        Location second=p.getLocation().clone();courtBounds.put(activeArena,new Location[]{first,second});
                        String path="court.bounds."+activeArena;getConfig().set(path+".world",first.getWorld().getName());
                        getConfig().set(path+".x1",first.getX());getConfig().set(path+".z1",first.getZ());getConfig().set(path+".x2",second.getX());getConfig().set(path+".z2",second.getZ());saveConfig();
                        p.sendMessage("§aAußenlinien gespeichert. Ball prallt daran ab.");
                    }else if(action.equals("clear")){courtBounds.remove(activeArena);getConfig().set("court.bounds."+activeArena,null);saveConfig();p.sendMessage("§eAußenlinien entfernt; Arenaecken gelten wieder.");}
                    else if(action.equals("show")){Location[] c=courtBounds.get(activeArena);p.sendMessage(c==null?"§eKeine separaten Außenlinien.":"§aAußenlinien: §f"+c[0].toVector()+" bis "+c[1].toVector());}
                    break;
                }
                if(sub.equals("lock") || sub.equals("unlock")) {courtLocked=sub.equals("lock");getConfig().set("court.locked",courtLocked);saveConfig();p.sendMessage(courtLocked?"§aArena gesperrt.":"§eArena freigegeben.");break;}
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
            case "ball", "start" -> { if(matchActive()){p.sendMessage("§cWährend eines Matches: /bc match stop");break;} spawnBall(p.getLocation().add(0, 1.0, 0)); p.sendMessage("§aBasketball gespawnt!"); }
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
            case "reset" -> { if(matchActive()){p.sendMessage("§cMatch läuft: /bc match stop");break;} scores.put("red", 0); scores.put("blue", 0); spawnBall(p.getLocation().add(0, 1, 0)); p.sendMessage("§aSpielstand zurückgesetzt."); }
            default -> p.sendMessage("§cUnbekannter Befehl. /bc help");
        }
        return true;
    }
}
