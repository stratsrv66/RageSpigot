package me.joeleoli.ragespigot;

import me.joeleoli.ragespigot.command.KnockbackCommand;
import me.joeleoli.ragespigot.handler.MovementHandler;
import me.joeleoli.ragespigot.handler.PacketHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.MinecraftServer;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;

public enum RageSpigot {

    INSTANCE;

    private RageSpigotConfig config;
    private Set<PacketHandler> packetHandlers = new HashSet<>();
    private Set<MovementHandler> movementHandlers = new HashSet<>();

    public RageSpigotConfig getConfig() {
        return this.config;
    }

    public Set<PacketHandler> getPacketHandlers() {
        return this.packetHandlers;
    }

    public Set<MovementHandler> getMovementHandlers() {
        return this.movementHandlers;
    }

    public void setConfig(RageSpigotConfig config) {
        this.config = config;
    }

	public void addPacketHandler(PacketHandler handler) {
		this.packetHandlers.add(handler);
	}

	public void addMovementHandler(MovementHandler handler) {
		this.movementHandlers.add(handler);
	}

	public void registerCommands() {
		Map<String, Command> commands = new HashMap<>();

		commands.put("knockback", new KnockbackCommand());

		for (Map.Entry<String, Command> entry : commands.entrySet()) {
			MinecraftServer.getServer().server.getCommandMap().register(entry.getKey(), "Spigot", entry.getValue());
		}
	}

}
