package net.minecraft.server;

import lombok.Getter;
import me.joeleoli.ragespigot.RageSpigot;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import java.io.File;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.chunkio.ChunkIOExecutor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.TravelAgent;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public abstract class PlayerList {

    public static final File a = new File("banned-players.json");
    public static final File b = new File("banned-ips.json");
    public static final File c = new File("ops.json");
    public static final File d = new File("whitelist.json");
    private static final Logger f = LogManager.getLogger();
    private static final SimpleDateFormat g = new SimpleDateFormat("yyyy-MM-dd \'at\' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<EntityPlayer> players = new java.util.concurrent.CopyOnWriteArrayList();
    private final Map<UUID, EntityPlayer> j = Maps.newHashMap();
    private final GameProfileBanList k;
    private final IpBanList l;
    private final OpList operators;
    private final WhiteList whitelist;
    private final Map<UUID, ServerStatisticManager> o;
    public IPlayerFileData playerFileData;
    private boolean hasWhitelist;
    protected int maxPlayers;
    private int r;
    private WorldSettings.EnumGamemode s;
    private boolean t;
    private int u;

    @Getter
    private CraftServer cserver;
    private final Map<String,EntityPlayer> playersByName = new org.spigotmc.CaseInsensitiveMap<>();

    public PlayerList(MinecraftServer minecraftserver) {
        this.cserver = minecraftserver.server = new CraftServer(minecraftserver, this);
        minecraftserver.console = org.bukkit.craftbukkit.command.ColouredConsoleSender.getInstance();
        minecraftserver.reader.addCompleter(new org.bukkit.craftbukkit.command.ConsoleCommandCompleter(minecraftserver.server));
        
        this.k = new GameProfileBanList(PlayerList.a);
        this.l = new IpBanList(PlayerList.b);
        this.operators = new OpList(PlayerList.c);
        this.whitelist = new WhiteList(PlayerList.d);
        this.o = Maps.newHashMap();
        this.server = minecraftserver;
        this.k.a(false);
        this.l.a(false);
        this.maxPlayers = 8;
    }

    public void a(NetworkManager networkmanager, EntityPlayer entityPlayer) {
        GameProfile gameprofile = entityPlayer.getProfile();
        UserCache usercache = this.server.getUserCache();
        GameProfile gameprofile1 = usercache.a(gameprofile.getId());
        String s = gameprofile1 == null ? gameprofile.getName() : gameprofile1.getName();

        usercache.a(gameprofile);
        NBTTagCompound nbttagcompound = this.a(entityPlayer);

        if (nbttagcompound != null && nbttagcompound.hasKey("bukkit")) {
            NBTTagCompound bukkit = nbttagcompound.getCompound("bukkit");
            s = bukkit.hasKeyOfType("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
        }

        Location originalLoc = new Location(entityPlayer.world.getWorld(), entityPlayer.locX, entityPlayer.locY, entityPlayer.locZ, entityPlayer.yaw, entityPlayer.pitch);
        org.bukkit.event.player.PlayerInitialSpawnEvent event = new org.bukkit.event.player.PlayerInitialSpawnEvent(entityPlayer.getBukkitEntity(), originalLoc);
        this.server.server.getPluginManager().callEvent(event);

        Location newLoc = event.getSpawnLocation();
        entityPlayer.world = ((CraftWorld) newLoc.getWorld()).getHandle();
        entityPlayer.locX = newLoc.getX();
        entityPlayer.locY = newLoc.getY();
        entityPlayer.locZ = newLoc.getZ();
        entityPlayer.yaw = newLoc.getYaw();
        entityPlayer.pitch = newLoc.getPitch();
        entityPlayer.dimension = ((CraftWorld) newLoc.getWorld()).getHandle().dimension;
        entityPlayer.spawnWorld = entityPlayer.world.worldData.getName();

        entityPlayer.spawnIn(this.server.getWorldServer(entityPlayer.dimension));
        entityPlayer.playerInteractManager.a((WorldServer) entityPlayer.world);
        String s1 = "local";

        if (networkmanager.getSocketAddress() != null) {
            s1 = networkmanager.getSocketAddress().toString();
        }

        Player bukkitPlayer = entityPlayer.getBukkitEntity();
        PlayerSpawnLocationEvent ev = new PlayerSpawnLocationEvent(bukkitPlayer, bukkitPlayer.getLocation());
        Bukkit.getPluginManager().callEvent(ev);

        Location loc = ev.getSpawnLocation();
        WorldServer world = ((CraftWorld) loc.getWorld()).getHandle();

        entityPlayer.spawnIn(world);
        entityPlayer.setPosition(loc.getX(), loc.getY(), loc.getZ());
        entityPlayer.setYawPitch(loc.getYaw(), loc.getPitch());

        WorldServer worldserver = this.server.getWorldServer(entityPlayer.dimension);
        WorldData worlddata = worldserver.getWorldData();
        BlockPosition blockposition = worldserver.getSpawn();

        this.a(entityPlayer, null, worldserver);

        PlayerConnection playerconnection = new PlayerConnection(this.server, networkmanager, entityPlayer);

        playerconnection.sendPacket(new PacketPlayOutLogin(entityPlayer.getId(), entityPlayer.playerInteractManager.getGameMode(), worlddata.isHardcore(), worldserver.worldProvider.getDimension(), worldserver.getDifficulty(), Math.min(this.getMaxPlayers(), 60), worlddata.getType(), worldserver.getGameRules().getBoolean("reducedDebugInfo"))); // CraftBukkit - cap player list to 60
        entityPlayer.getBukkitEntity().sendSupportedChannels();
        playerconnection.sendPacket(new PacketPlayOutCustomPayload("MC|Brand", (new PacketDataSerializer(Unpooled.buffer())).a(this.getServer().getServerModName())));
        playerconnection.sendPacket(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        playerconnection.sendPacket(new PacketPlayOutSpawnPosition(blockposition));
        playerconnection.sendPacket(new PacketPlayOutAbilities(entityPlayer.abilities));
        playerconnection.sendPacket(new PacketPlayOutHeldItemSlot(entityPlayer.inventory.itemInHandIndex));
        entityPlayer.getStatisticManager().d();
        entityPlayer.getStatisticManager().updateStatistics(entityPlayer);
        this.sendScoreboard((ScoreboardServer) worldserver.getScoreboard(), entityPlayer);
        this.server.aH();

        String joinMessage;

        if (!entityPlayer.getName().equalsIgnoreCase(s)) {
            joinMessage = "\u00A7e" + LocaleI18n.a("multiplayer.player.joined.renamed", entityPlayer.getName(), s);
        } else {
            joinMessage = "\u00A7e" + LocaleI18n.a("multiplayer.player.joined", entityPlayer.getName());
        }

        this.onPlayerJoin(entityPlayer, joinMessage);

        worldserver = server.getWorldServer(entityPlayer.dimension);

        playerconnection.a(entityPlayer.locX, entityPlayer.locY, entityPlayer.locZ, entityPlayer.yaw, entityPlayer.pitch);
        this.b(entityPlayer, worldserver);

        if (this.server.getResourcePack().length() > 0) {
            entityPlayer.setResourcePack(this.server.getResourcePack(), this.server.getResourcePackHash());
        }

        Iterator iterator = entityPlayer.getEffects().iterator();

        while (iterator.hasNext()) {
            MobEffect mobeffect = (MobEffect) iterator.next();

            playerconnection.sendPacket(new PacketPlayOutEntityEffect(entityPlayer.getId(), mobeffect));
        }

        entityPlayer.syncInventory();

        if (nbttagcompound != null && nbttagcompound.hasKeyOfType("Riding", 10)) {
            Entity entity = EntityTypes.a(nbttagcompound.getCompound("Riding"), worldserver);

            if (entity != null) {
                entity.attachedToPlayer = true;
                worldserver.addEntity(entity);
                entityPlayer.mount(entity);
                entity.attachedToPlayer = false;
            }
        }

        PlayerList.f.info(entityPlayer.getName() + " joined the server: (" + s1 + ") (" + entityPlayer.world.worldData.getName() + ", " + entityPlayer.locX + ", " + entityPlayer.locY + ", " + entityPlayer.locZ + ")");
    }

    public void sendScoreboard(ScoreboardServer scoreboardserver, EntityPlayer entityPlayer) {
        HashSet hashset = Sets.newHashSet();
        Iterator iterator = scoreboardserver.getTeams().iterator();

        while (iterator.hasNext()) {
            ScoreboardTeam scoreboardteam = (ScoreboardTeam) iterator.next();

            entityPlayer.playerConnection.sendPacket(new PacketPlayOutScoreboardTeam(scoreboardteam, 0));
        }

        for (int i = 0; i < 19; ++i) {
            ScoreboardObjective scoreboardobjective = scoreboardserver.getObjectiveForSlot(i);

            if (scoreboardobjective != null && !hashset.contains(scoreboardobjective)) {
                List list = scoreboardserver.getScoreboardScorePacketsForObjective(scoreboardobjective);
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    Packet packet = (Packet) iterator1.next();

                    entityPlayer.playerConnection.sendPacket(packet);
                }

                hashset.add(scoreboardobjective);
            }
        }

    }

    public void setPlayerFileData(WorldServer[] aworldserver) {
        if (playerFileData != null) {
            return;
        }

        this.playerFileData = aworldserver[0].getDataManager().getPlayerFileData();

        aworldserver[0].getWorldBorder().a(new IWorldBorderListener() {
            public void a(WorldBorder worldborder, double d0) {
                PlayerList.this.sendAll(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.SET_SIZE));
            }

            public void a(WorldBorder worldborder, double d0, double d1, long i) {
                PlayerList.this.sendAll(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.LERP_SIZE));
            }

            public void a(WorldBorder worldborder, double d0, double d1) {
                PlayerList.this.sendAll(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.SET_CENTER));
            }

            public void a(WorldBorder worldborder, int i) {
                PlayerList.this.sendAll(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.SET_WARNING_TIME));
            }

            public void b(WorldBorder worldborder, int i) {
                PlayerList.this.sendAll(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.SET_WARNING_BLOCKS));
            }

            public void b(WorldBorder worldborder, double d0) {}

            public void c(WorldBorder worldborder, double d0) {}
        });
    }

    public void a(EntityPlayer entityplayer, WorldServer newWorld) {
        WorldServer currentWorld = entityplayer.u();

        if (newWorld != null) {
            newWorld.getPlayerChunkMap().removePlayer(entityplayer);
        }

        currentWorld.getPlayerChunkMap().addPlayer(entityplayer);
        currentWorld.chunkProviderServer.getChunkAt((int) entityplayer.locX >> 4, (int) entityplayer.locZ >> 4);
    }

    public int d() {
        return PlayerChunkMap.getFurthestViewableBlock(this.s());
    }

    public NBTTagCompound a(EntityPlayer entityplayer) {
        NBTTagCompound nbttagcompound = this.server.worlds.get(0).getWorldData().i();
        NBTTagCompound nbttagcompound1;

        if (entityplayer.getName().equals(this.server.S()) && nbttagcompound != null) {
            entityplayer.f(nbttagcompound);
            nbttagcompound1 = nbttagcompound;
            PlayerList.f.debug("loading single player");
        } else {
            nbttagcompound1 = this.playerFileData.load(entityplayer);
        }

        return nbttagcompound1;
    }

    protected void savePlayerFile(EntityPlayer entityplayer) {
        this.playerFileData.save(entityplayer);

        ServerStatisticManager serverstatisticmanager = this.o.get(entityplayer.getUniqueID());

        if (serverstatisticmanager != null) {
            serverstatisticmanager.b();
        }
    }

    public void onPlayerJoin(EntityPlayer entityplayer, String joinMessage) {
        this.players.add(entityplayer);
        this.playersByName.put(entityplayer.getName(), entityplayer);
        this.j.put(entityplayer.getUniqueID(), entityplayer);

        WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(cserver.getPlayer(entityplayer), joinMessage);
        cserver.getPluginManager().callEvent(playerJoinEvent);

        joinMessage = playerJoinEvent.getJoinMessage();

        if (joinMessage != null && joinMessage.length() > 0) {
            for (IChatBaseComponent line : org.bukkit.craftbukkit.util.CraftChatMessage.fromString(joinMessage)) {
                server.getPlayerList().sendAll(new PacketPlayOutChat(line));
            }
        }

        ChunkIOExecutor.adjustPoolSize(getPlayerCount());

        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entityplayer);

        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer1 = this.players.get(i);

            if (!RageSpigot.INSTANCE.getConfig().isHidePlayersFromTab() || entityplayer1.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer1.playerConnection.sendPacket(packet);
            }

            if (!RageSpigot.INSTANCE.getConfig().isHidePlayersFromTab() || entityplayer.getBukkitEntity().canSee(entityplayer1.getBukkitEntity())) {
                entityplayer.playerConnection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entityplayer1));
            }
        }

        if (entityplayer.world == worldserver && !worldserver.players.contains(entityplayer)) {
            worldserver.addEntity(entityplayer);
            this.a(entityplayer, (WorldServer) null);
        }
    }

    public void d(EntityPlayer entityplayer) {
        entityplayer.u().getPlayerChunkMap().movePlayer(entityplayer);
    }

    public String disconnect(EntityPlayer entityplayer) {
        entityplayer.b(StatisticList.f);

        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(entityplayer);

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(cserver.getPlayer(entityplayer), "\u00A7e" + entityplayer.getName() + " left the game.");
        cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        this.savePlayerFile(entityplayer);

        WorldServer worldserver = entityplayer.u();

        if (entityplayer.vehicle != null && !(entityplayer.vehicle instanceof EntityPlayer)) {
            worldserver.removeEntity(entityplayer.vehicle);
            PlayerList.f.debug("removing player mount");
        }

        worldserver.kill(entityplayer);
        worldserver.getPlayerChunkMap().removePlayer(entityplayer);

        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getName());

        UUID uuid = entityplayer.getUniqueID();
        EntityPlayer entityplayer1 = this.j.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.j.remove(uuid);
            this.o.remove(uuid);
        }

        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, entityplayer);

        for (int i = 0; i < players.size(); i++) {
            EntityPlayer entityplayer2 = this.players.get(i);

            entityplayer2.playerConnection.sendPacket(packet);
        }

        cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());

        ChunkIOExecutor.adjustPoolSize(this.getPlayerCount());

        return playerQuitEvent.getQuitMessage();
    }

    public EntityPlayer attemptLogin(LoginListener loginlistener, GameProfile gameprofile, String hostname) {
        UUID uuid = EntityHuman.a(gameprofile);
        ArrayList arraylist = Lists.newArrayList();

        EntityPlayer entityplayer;

        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = this.players.get(i);

            if (entityplayer.getUniqueID().equals(uuid)) {
                arraylist.add(entityplayer);
            }
        }

        Iterator iterator = arraylist.iterator();

        while (iterator.hasNext()) {
            entityplayer = (EntityPlayer) iterator.next();
            savePlayerFile(entityplayer);
            entityplayer.playerConnection.disconnect("You logged in from another location");
        }

        SocketAddress socketaddress = loginlistener.networkManager.getSocketAddress();

        EntityPlayer entity = new EntityPlayer(server, server.getWorldServer(0), gameprofile, new PlayerInteractManager(server.getWorldServer(0)));
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, hostname, ((java.net.InetSocketAddress) socketaddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.networkManager.getRawAddress()).getAddress());
        String s;

        if (getProfileBans().isBanned(gameprofile) && !getProfileBans().get(gameprofile).hasExpired()) {
            GameProfileBanEntry banEntry = this.k.get(gameprofile);
            s = "You are banned from this server!\nReason: " + banEntry.getReason();

            if (banEntry.getExpires() != null) {
                s = s + "\nYour ban will be removed on " + PlayerList.g.format(banEntry.getExpires());
            }

            if (!banEntry.hasExpired()) {
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, s);
            }
        } else if (!this.isWhitelisted(gameprofile)) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, org.spigotmc.SpigotConfig.whitelistMessage);
        } else if (getIPBans().isBanned(socketaddress) && !getIPBans().get(socketaddress).hasExpired()) {
            IpBanEntry banEntry = this.l.get(socketaddress);
            s = "Your IP address is banned from this server!\nReason: " + banEntry.getReason();

            if (banEntry.getExpires() != null) {
                s = s + "\nYour ban will be removed on " + PlayerList.g.format(banEntry.getExpires());
            }

            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, s);
        } else {
            if (this.players.size() >= this.maxPlayers && !this.f(gameprofile)) {
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, org.spigotmc.SpigotConfig.serverFullMessage); // Spigot
            }
        }

        cserver.getPluginManager().callEvent(event);

        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(event.getKickMessage());
            return null;
        }

        return entity;
    }

    // This has been moved to the attemptLogin method above but must stay
    // implemented because spigot is shit
    public EntityPlayer processLogin(GameProfile gameprofile, EntityPlayer player) {
        return player;
    }

    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i, boolean flag) {
        return this.moveToWorld(entityplayer, i, flag, null, true, false);
    }

    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i, boolean flag, Location location, boolean avoidSuffocation, boolean callEvent) {
        entityplayer.u().getTracker().untrackPlayer(entityplayer);
        entityplayer.u().getPlayerChunkMap().removePlayer(entityplayer);
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getName());
        this.server.getWorldServer(entityplayer.dimension).removeEntity(entityplayer);
        BlockPosition blockposition = entityplayer.getBed();
        boolean flag1 = entityplayer.isRespawnForced();

        EntityPlayer entityplayer1 = entityplayer;
        org.bukkit.World fromWorld = entityplayer.getBukkitEntity().getWorld();

        entityplayer.viewingCredits = false;
        entityplayer1.playerConnection = entityplayer.playerConnection;

        entityplayer1.copyTo(entityplayer, flag);
        entityplayer1.d(entityplayer.getId());
        entityplayer1.o(entityplayer);

        BlockPosition blockposition1;

        if (location == null) {
            boolean isBedSpawn = false;
            CraftWorld cworld = (CraftWorld) this.server.server.getWorld(entityplayer.spawnWorld);

            if (cworld != null && blockposition != null) {
                blockposition1 = EntityHuman.getBed(cworld.getHandle(), blockposition, flag1);

                if (blockposition1 != null) {
                    isBedSpawn = true;
                    location = new Location(cworld, blockposition1.getX() + 0.5, blockposition1.getY(), blockposition1.getZ() + 0.5);
                } else {
                    entityplayer1.setRespawnPosition(null, true);
                    entityplayer1.playerConnection.sendPacket(new PacketPlayOutGameStateChange(0, 0.0F));
                }
            }

            if (location == null) {
                cworld = (CraftWorld) this.server.server.getWorlds().get(0);
                blockposition = cworld.getHandle().getSpawn();
                location = new Location(cworld, blockposition.getX() + 0.5, blockposition.getY(), blockposition.getZ() + 0.5);
            }

            Player respawnPlayer = cserver.getPlayer(entityplayer1);

            if (callEvent) {
                PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn);

                cserver.getPluginManager().callEvent(respawnEvent);

                if (entityplayer.playerConnection.isDisconnected()) {
                    return entityplayer;
                }

                location = respawnEvent.getRespawnLocation();
            }

            entityplayer.reset();
        } else {
            location.setWorld(server.getWorldServer(i).getWorld());
        }

        WorldServer worldserver = ((CraftWorld) location.getWorld()).getHandle();
        entityplayer1.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());

        worldserver.chunkProviderServer.getChunkAt((int) entityplayer1.locX >> 4, (int) entityplayer1.locZ >> 4);

        while (avoidSuffocation && !worldserver.getCubes(entityplayer1, entityplayer1.getBoundingBox()).isEmpty() && entityplayer1.locY < 256.0D) {
            entityplayer1.setPosition(entityplayer1.locX, entityplayer1.locY + 1.0D, entityplayer1.locZ);
        }

        byte actualDimension = (byte) (worldserver.getWorld().getEnvironment().getId());

        if (fromWorld.getEnvironment() == worldserver.getWorld().getEnvironment()) {
            entityplayer1.playerConnection.sendPacket(new PacketPlayOutRespawn((byte) (actualDimension >= 0 ? -1 : 0), worldserver.getDifficulty(), worldserver.getWorldData().getType(), entityplayer.playerInteractManager.getGameMode()));
        }

        entityplayer1.playerConnection.sendPacket(new PacketPlayOutRespawn(actualDimension, worldserver.getDifficulty(), worldserver.getWorldData().getType(), entityplayer1.playerInteractManager.getGameMode()));
        entityplayer1.spawnIn(worldserver);
        entityplayer1.dead = false;
        entityplayer1.playerConnection.teleport(new Location(worldserver.getWorld(), entityplayer1.locX, entityplayer1.locY, entityplayer1.locZ, entityplayer1.yaw, entityplayer1.pitch));
        entityplayer1.setSneaking(false);

        blockposition1 = worldserver.getSpawn();

        entityplayer1.playerConnection.sendPacket(new PacketPlayOutSpawnPosition(blockposition1));
        entityplayer1.playerConnection.sendPacket(new PacketPlayOutExperience(entityplayer1.exp, entityplayer1.expTotal, entityplayer1.expLevel));

        this.b(entityplayer1, worldserver);

        if (!entityplayer.playerConnection.isDisconnected()) {
            worldserver.getPlayerChunkMap().addPlayer(entityplayer1);
            worldserver.addEntity(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByName.put(entityplayer1.getName(), entityplayer1);
            this.j.put(entityplayer1.getUniqueID(), entityplayer1);
        }

        updateClient(entityplayer);
        entityplayer.updateAbilities();

        for (Object o1 : entityplayer.getEffects()) {
            MobEffect mobEffect = (MobEffect) o1;
            entityplayer.playerConnection.sendPacket(new PacketPlayOutEntityEffect(entityplayer.getId(), mobEffect));
        }

        entityplayer1.setHealth(entityplayer1.getHealth());

        if (fromWorld != location.getWorld()) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld);
            server.server.getPluginManager().callEvent(event);
        }

        if (entityplayer.playerConnection.isDisconnected()) {
            this.savePlayerFile(entityplayer);
        }

        return entityplayer1;
    }

    public void changeDimension(EntityPlayer entityplayer, int i, TeleportCause cause) {
        WorldServer exitWorld = null;

        if (entityplayer.dimension < CraftWorld.CUSTOM_DIMENSION_OFFSET) {
            for (WorldServer world : this.server.worlds) {
                if (world.dimension == i) {
                    exitWorld = world;
                }
            }
        }

        Location enter = entityplayer.getBukkitEntity().getLocation();
        Location exit = null;
        boolean useTravelAgent = false;

        if (exitWorld != null) {
            if ((cause == TeleportCause.END_PORTAL) && (i == 0)) {
                exit = (entityplayer.getBukkitEntity()).getBedSpawnLocation();

                if (exit == null || ((CraftWorld) exit.getWorld()).getHandle().dimension != 0) {
                    exit = exitWorld.getWorld().getSpawnLocation();
                }
            } else {
                exit = this.calculateTarget(enter, exitWorld);
                useTravelAgent = true;
            }
        }

        TravelAgent agent = exit != null ? (TravelAgent) ((CraftWorld) exit.getWorld()).getHandle().getTravelAgent() : org.bukkit.craftbukkit.CraftTravelAgent.DEFAULT;
        agent.setCanCreatePortal(cause != TeleportCause.END_PORTAL);

        PlayerPortalEvent event = new PlayerPortalEvent(entityplayer.getBukkitEntity(), enter, exit, agent, cause);
        event.useTravelAgent(useTravelAgent);

        Bukkit.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled() || event.getTo() == null) {
            return;
        }

        exit = cause != TeleportCause.END_PORTAL && event.useTravelAgent() ? event.getPortalTravelAgent().findOrCreate(event.getTo()) : event.getTo();

        if (exit == null) {
            return;
        }

        exitWorld = ((CraftWorld) exit.getWorld()).getHandle();

        org.bukkit.event.player.PlayerTeleportEvent tpEvent = new org.bukkit.event.player.PlayerTeleportEvent(entityplayer.getBukkitEntity(), enter, exit, cause);

        Bukkit.getServer().getPluginManager().callEvent(tpEvent);

        if (tpEvent.isCancelled() || tpEvent.getTo() == null) {
            return;
        }

        Vector velocity = entityplayer.getBukkitEntity().getVelocity();
        boolean before = exitWorld.chunkProviderServer.forceChunkLoad;
        exitWorld.chunkProviderServer.forceChunkLoad = true;
        exitWorld.getTravelAgent().adjustExit(entityplayer, exit, velocity);
        exitWorld.chunkProviderServer.forceChunkLoad = before;

        this.moveToWorld(entityplayer, exitWorld.dimension, true, exit, false, false);

        if (entityplayer.motX != velocity.getX() || entityplayer.motY != velocity.getY() || entityplayer.motZ != velocity.getZ()) {
            entityplayer.getBukkitEntity().setVelocity(velocity);
        }
    }

    public void changeWorld(Entity entity, int i, WorldServer worldserver, WorldServer worldserver1) {
        Location exit = calculateTarget(entity.getBukkitEntity().getLocation(), worldserver1);
        repositionEntity(entity, exit, true);
    }

    public Location calculateTarget(Location enter, World target) {
        WorldServer worldserver = ((CraftWorld) enter.getWorld()).getHandle();
        WorldServer worldserver1 = (target.getWorld()).getHandle();
        int i = worldserver.dimension;

        double y = enter.getY();
        float yaw = enter.getYaw();
        float pitch = enter.getPitch();
        double d0 = enter.getX();
        double d1 = enter.getZ();
        double d2 = 8.0D;

        if (worldserver1.dimension == -1) {
            d0 = MathHelper.a(d0 / d2, worldserver1.getWorldBorder().b()+ 16.0D, worldserver1.getWorldBorder().d() - 16.0D);
            d1 = MathHelper.a(d1 / d2, worldserver1.getWorldBorder().c() + 16.0D, worldserver1.getWorldBorder().e() - 16.0D);
        } else if (worldserver1.dimension == 0) {
            d0 = MathHelper.a(d0 * d2, worldserver1.getWorldBorder().b() + 16.0D, worldserver1.getWorldBorder().d() - 16.0D);
            d1 = MathHelper.a(d1 * d2, worldserver1.getWorldBorder().c() + 16.0D, worldserver1.getWorldBorder().e() - 16.0D);
        } else {
            BlockPosition blockposition;

            if (i == 1) {
                worldserver1 = this.server.worlds.get(0);
                blockposition = worldserver1.getSpawn();
            } else {
                blockposition = worldserver1.getDimensionSpawn();
            }

            d0 = (double) blockposition.getX();
            y = (double) blockposition.getY();
            d1 = (double) blockposition.getZ();
        }

        if (i != 1) {
            worldserver.methodProfiler.a("placing");
            d0 = (double) MathHelper.clamp((int) d0, -29999872, 29999872);
            d1 = (double) MathHelper.clamp((int) d1, -29999872, 29999872);
        }

        return new Location(worldserver1.getWorld(), d0, y, d1, yaw, pitch);
    }

    public void repositionEntity(Entity entity, Location exit, boolean portal) {
        WorldServer currentWorldServer = (WorldServer) entity.world;
        WorldServer exitWorldServer = ((CraftWorld) exit.getWorld()).getHandle();
        int i = currentWorldServer.dimension;

        entity.setPositionRotation(exit.getX(), exit.getY(), exit.getZ(), exit.getYaw(), exit.getPitch());

        if (entity.isAlive()) {
            currentWorldServer.entityJoinedWorld(entity, false);
        }

        currentWorldServer.methodProfiler.b();

        if (i != 1) {
            currentWorldServer.methodProfiler.a("placing");

            if (entity.isAlive()) {
                if (portal) {
                    Vector velocity = entity.getBukkitEntity().getVelocity();

                    exitWorldServer.getTravelAgent().adjustExit(entity, exit, velocity);
                    entity.setPositionRotation(exit.getX(), exit.getY(), exit.getZ(), exit.getYaw(), exit.getPitch());

                    if (entity.motX != velocity.getX() || entity.motY != velocity.getY() || entity.motZ != velocity.getZ()) {
                        entity.getBukkitEntity().setVelocity(velocity);
                    }
                }
                exitWorldServer.addEntity(entity);
                exitWorldServer.entityJoinedWorld(entity, false);
            }

            currentWorldServer.methodProfiler.b();
        }

        entity.spawnIn(exitWorldServer);
    }

    public void tick() {
        if (++this.u > 600) {
            this.sendAll(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.UPDATE_LATENCY, this.players));
            this.u = 0;
        }
    }

    public void sendAll(Packet packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            this.players.get(i).playerConnection.sendPacket(packet);
        }
    }

    public void sendAll(Packet packet, EntityHuman entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer =  this.players.get(i);

            if (entityhuman != null && entityhuman instanceof EntityPlayer && !entityplayer.getBukkitEntity().canSee(((EntityPlayer) entityhuman).getBukkitEntity())) {
                continue;
            }

            this.players.get(i).playerConnection.sendPacket(packet);
        }
    }

    public void sendAll(Packet packet, World world) {
        for (int i = 0; i < world.players.size(); ++i) {
            ((EntityPlayer) world.players.get(i)).playerConnection.sendPacket(packet);
        }

    }

    public void a(Packet packet, int i) {
        for (int j = 0; j < this.players.size(); ++j) {
            EntityPlayer entityplayer = this.players.get(j);

            if (entityplayer.dimension == i) {
                entityplayer.playerConnection.sendPacket(packet);
            }
        }

    }

    public void a(EntityHuman entityhuman, IChatBaseComponent ichatbasecomponent) {
        ScoreboardTeamBase scoreboardteambase = entityhuman.getScoreboardTeam();

        if (scoreboardteambase != null) {
            Collection collection = scoreboardteambase.getPlayerNameSet();
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                EntityPlayer entityplayer = this.getPlayer(s);

                if (entityplayer != null && entityplayer != entityhuman) {
                    entityplayer.sendMessage(ichatbasecomponent);
                }
            }

        }
    }

    public void b(EntityHuman entityhuman, IChatBaseComponent ichatbasecomponent) {
        ScoreboardTeamBase scoreboardteambase = entityhuman.getScoreboardTeam();

        if (scoreboardteambase == null) {
            this.sendMessage(ichatbasecomponent);
        } else {
            for (int i = 0; i < this.players.size(); ++i) {
                EntityPlayer entityplayer = this.players.get(i);

                if (entityplayer.getScoreboardTeam() != scoreboardteambase) {
                    entityplayer.sendMessage(ichatbasecomponent);
                }
            }

        }
    }

    public String b(boolean flag) {
        String s = "";
        ArrayList arraylist = Lists.newArrayList(this.players);

        for (int i = 0; i < arraylist.size(); ++i) {
            if (i > 0) {
                s = s + ", ";
            }

            s = s + ((EntityPlayer) arraylist.get(i)).getName();

            if (flag) {
                s = s + " (" + ((EntityPlayer) arraylist.get(i)).getUniqueID().toString() + ")";
            }
        }

        return s;
    }

    public String[] f() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = (this.players.get(i)).getName();
        }

        return astring;
    }

    public GameProfile[] g() {
        GameProfile[] agameprofile = new GameProfile[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            agameprofile[i] = (this.players.get(i)).getProfile();
        }

        return agameprofile;
    }

    public GameProfileBanList getProfileBans() {
        return this.k;
    }

    public IpBanList getIPBans() {
        return this.l;
    }

    public void addOp(GameProfile gameprofile) {
        this.operators.add(new OpListEntry(gameprofile, this.server.p(), this.operators.b(gameprofile)));

        Player player = server.server.getPlayer(gameprofile.getId());

        if (player != null) {
           player.recalculatePermissions();
        }
    }

    public void removeOp(GameProfile gameprofile) {
        this.operators.remove(gameprofile);

        Player player = server.server.getPlayer(gameprofile.getId());

        if (player != null) {
            player.recalculatePermissions();
        }
    }

    public boolean isWhitelisted(GameProfile gameprofile) {
        return !this.hasWhitelist || this.operators.d(gameprofile) || this.whitelist.d(gameprofile);
    }

    public boolean isOp(GameProfile gameprofile) {
        return this.operators.d(gameprofile) || this.server.T() && this.server.worlds.get(0).getWorldData().v() && this.server.S().equalsIgnoreCase(gameprofile.getName()) || this.t; // CraftBukkit
    }

    public EntityPlayer getPlayer(String s) {
        return this.playersByName.get(s);
    }

    public void sendPacketNearby(double d0, double d1, double d2, double d3, int i, Packet packet) {
        this.sendPacketNearby(null, d0, d1, d2, d3, i, packet);
    }

    public void sendPacketNearby(EntityHuman entityhuman, double d0, double d1, double d2, double d3, int i, Packet packet) {
        for (int j = 0; j < this.players.size(); ++j) {
            EntityPlayer entityplayer = this.players.get(j);

            if (entityhuman != null && entityhuman instanceof EntityPlayer && !entityplayer.getBukkitEntity().canSee(((EntityPlayer) entityhuman).getBukkitEntity())) {
               continue;
            }

            if (entityplayer != entityhuman && entityplayer.dimension == i) {
                double d4 = d0 - entityplayer.locX;
                double d5 = d1 - entityplayer.locY;
                double d6 = d2 - entityplayer.locZ;

                if (d4 * d4 + d5 * d5 + d6 * d6 < d3 * d3) {
                    entityplayer.playerConnection.sendPacket(packet);
                }
            }
        }

    }

    public void sendPacketNearbyIncludingSelf(EntityHuman entityhuman, double d0, double d1, double d2, double d3, int i, Packet packet) { // RageSpigot
        for (int j = 0; j < this.players.size(); ++j) {
            EntityPlayer entityplayer = this.players.get(j);

            if (entityhuman != null && !entityplayer.getBukkitEntity().canSeeEntity(entityhuman.getBukkitEntity())) {
                continue;
            }

            if (entityplayer.dimension == i) {
                double d4 = d0 - entityplayer.locX;
                double d5 = d1 - entityplayer.locY;
                double d6 = d2 - entityplayer.locZ;

                if (d4 * d4 + d5 * d5 + d6 * d6 < d3 * d3) {
                    entityplayer.playerConnection.sendPacket(packet);
                }
            }
        }
    }

    public void savePlayers() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.savePlayerFile(this.players.get(i));
        }
    }

    public void addWhitelist(GameProfile gameprofile) {
        this.whitelist.add(new WhiteListEntry(gameprofile));
    }

    public void removeWhitelist(GameProfile gameprofile) {
        this.whitelist.remove(gameprofile);
    }

    public WhiteList getWhitelist() {
        return this.whitelist;
    }

    public String[] getWhitelisted() {
        return this.whitelist.getEntries();
    }

    public OpList getOPs() {
        return this.operators;
    }

    public String[] n() {
        return this.operators.getEntries();
    }

    public void reloadWhitelist() {}

    public void b(EntityPlayer entityplayer, WorldServer worldserver) {
        WorldBorder worldborder = entityplayer.world.getWorldBorder();

        entityplayer.playerConnection.sendPacket(new PacketPlayOutWorldBorder(worldborder, PacketPlayOutWorldBorder.EnumWorldBorderAction.INITIALIZE));
        entityplayer.playerConnection.sendPacket(new PacketPlayOutUpdateTime(worldserver.getTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean("doDaylightCycle")));

        if (worldserver.S()) {
            entityplayer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            entityplayer.updateWeather(-worldserver.p, worldserver.p, -worldserver.r, worldserver.r);
        }

    }

    public void updateClient(EntityPlayer entityplayer) {
        entityplayer.updateInventory(entityplayer.defaultContainer);
        entityplayer.getBukkitEntity().updateScaledHealth();
        entityplayer.playerConnection.sendPacket(new PacketPlayOutHeldItemSlot(entityplayer.inventory.itemInHandIndex));
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public void setMaxPlayers(int slots) {
        this.maxPlayers = slots;
    }

    public String[] getSeenPlayers() {
        return this.server.worlds.get(0).getDataManager().getPlayerFileData().getSeenPlayers();
    }

    public boolean getHasWhitelist() {
        return this.hasWhitelist;
    }

    public void setHasWhitelist(boolean flag) {
        this.hasWhitelist = flag;
    }

    public List<EntityPlayer> b(String s) {
        ArrayList arraylist = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            if (entityplayer.w().equals(s)) {
                arraylist.add(entityplayer);
            }
        }

        return arraylist;
    }

    public int s() {
        return this.r;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public NBTTagCompound t() {
        return null;
    }

    private void a(EntityPlayer entityplayer, EntityPlayer entityplayer1, World world) {
        if (entityplayer1 != null) {
            entityplayer.playerInteractManager.setGameMode(entityplayer1.playerInteractManager.getGameMode());
        } else if (this.s != null) {
            entityplayer.playerInteractManager.setGameMode(this.s);
        }

        entityplayer.playerInteractManager.b(world.getWorldData().getGameType());
    }

    public void u() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.players.get(i).playerConnection.disconnect(this.server.server.getShutdownMessage());
        }
    }

    public void sendMessage(IChatBaseComponent[] iChatBaseComponents) {
        for (IChatBaseComponent component : iChatBaseComponents) {
            sendMessage(component, true);
        }
    }

    public void sendMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {
        this.server.sendMessage(ichatbasecomponent);
        int i = flag ? 1 : 0;

        this.sendAll(new PacketPlayOutChat(CraftChatMessage.fixComponent(ichatbasecomponent), (byte) i));
    }

    public void sendMessage(IChatBaseComponent ichatbasecomponent) {
        this.sendMessage(ichatbasecomponent, true);
    }

    public ServerStatisticManager a(EntityHuman entityhuman) {
        UUID uuid = entityhuman.getUniqueID();
        ServerStatisticManager serverstatisticmanager = uuid == null ? null : this.o.get(uuid);

        if (serverstatisticmanager == null) {
            File file = new File(this.server.getWorldServer(0).getDataManager().getDirectory(), "stats");
            File file1 = new File(file, uuid.toString() + ".json");

            if (!file1.exists()) {
                File file2 = new File(file, entityhuman.getName() + ".json");

                if (file2.exists() && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverstatisticmanager = new ServerStatisticManager(this.server, file1);
            serverstatisticmanager.a();
            this.o.put(uuid, serverstatisticmanager);
        }

        return serverstatisticmanager;
    }

    public void a(int i) {
        this.r = i;
        if (this.server.worldServer != null) {
            for (int k = 0; k < server.worlds.size(); ++k) {
                WorldServer worldserver = server.worlds.get(0);

                if (worldserver != null) {
                    worldserver.getPlayerChunkMap().a(i);
                }
            }

        }
    }

    public List<EntityPlayer> v() {
        return this.players;
    }

    public EntityPlayer a(UUID uuid) {
        return this.j.get(uuid);
    }

    public boolean f(GameProfile gameprofile) {
        return false;
    }

    public void removeFromPlayerNames(String name) {
        this.playersByName.remove(name);
    }

    public void setPlayerName(String name, EntityPlayer player) {
        this.playersByName.put(name, player);
    }

}
