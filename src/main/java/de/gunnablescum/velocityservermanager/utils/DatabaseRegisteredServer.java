package de.gunnablescum.velocityservermanager.utils;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import de.gunnablescum.velocityservermanager.ServerManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import static de.gunnablescum.velocityservermanager.utils.ServerFlag.*;

public record DatabaseRegisteredServer(String name, String address, int port, byte flags) {

    private final static ProxyServer proxyServer = ServerManager.getInstance().getProxyServer();

    public static void addAllServers() {
        for(DatabaseRegisteredServer server : MySQL.getAllServers()){
            if (!server.active()) continue;
            server.addToProxy();
        }
    }

    public boolean active() {
        return !hasFlag(DISABLED);
    }

    public void empty(boolean force) {
        if(!active()) return;
        Optional<RegisteredServer> server = ServerManager.getInstance().getProxyServer().getServer(name);
        if(server.isEmpty()) return;
        for(Player all : server.get().getPlayersConnected()) {
            if (!force && all.hasPermission("servermanager.ignorekick")) continue;
            all.createConnectionRequest(ServerManager.lobbies.get(new SecureRandom().nextInt(ServerManager.lobbies.size()))).connect();
            all.sendMessage(force ? Messages.previousServerDeletedInfo() : Messages.previousServerEmptied());
        }
    }

    public void removeFromProxy() {
        Optional<RegisteredServer> server = ServerManager.getInstance().getProxyServer().getServer(name);
        server.ifPresent(registeredServer -> ServerManager.getInstance().getProxyServer().unregisterServer(registeredServer.getServerInfo()));
    }

    public void addToProxy() {
        ServerManager.getInstance().getProxyServer().registerServer(new ServerInfo(name, new InetSocketAddress(address, port)));
    }

    public void reloadInProxy() {
        removeFromProxy();
        addToProxy();
    }

    @Nullable
    public RegisteredServer getFromProxy() {
        return proxyServer.getServer(name).orElse(null);
    }

    public void deleteFromDatabase() {
        MySQL.deleteServer(name);
    }

    public boolean setFlag(ServerFlag flag) {
        int newFlags = flags | flag.bit;
        if (flags == newFlags) return false;
        MySQL.update("UPDATE servermanager_servers SET flags = ? WHERE name = ?", List.of(
                new SQLStatementParameter(SQLStatementParameterType.INT, 1, newFlags),
                new SQLStatementParameter(SQLStatementParameterType.STRING, 2, name)
        ));
        return true;
    }

    public boolean unsetFlag(ServerFlag flag) {
        int newFlags = flags & ~flag.bit;
        if (flags == newFlags) return false;
        MySQL.update("UPDATE servermanager_servers SET flags = ? WHERE name = ?", List.of(
                new SQLStatementParameter(SQLStatementParameterType.INT, 1, newFlags),
                new SQLStatementParameter(SQLStatementParameterType.STRING, 2, name)
        ));
        return true;
    }

    public void setActive(boolean isActive) {
        if(isActive) {
            unsetFlag(DISABLED);
        } else {
            setFlag(DISABLED);
        }
    }

    public void sendInfo(CommandSource source) {
        if(!source.hasPermission("servermanager.servers.info")) {
            source.sendMessage(Messages.noPermission());
            return;
        }
        MiniMessage mm = MiniMessage.miniMessage();
        source.sendMessage(Messages.PREFIX.append(mm.deserialize("<gray>Info about<dark_gray>: " + name())));
        source.sendMessage(mm.deserialize("<gray>Systemname<dark_gray>:<green> " + name()));
        source.sendMessage(mm.deserialize("<gray>Status<dark_gray>: " + onlineOfflineString(ServerManager.serverStatusCache.getOrDefault(name(), false))));
        source.sendMessage(mm.deserialize("<gray>IP<dark_gray>:<green> " + address() + ":" + port()));
        source.sendMessage(mm.deserialize("<gray>Flags<dark_gray>: " + ServerFlag.miniMessageFormatted(flags)));
        if(!active()) return;

        StringBuilder players = null;
        RegisteredServer registeredServer = getFromProxy();
        if (registeredServer == null) {
            return;
        }

        for (Player all : registeredServer.getPlayersConnected()) {
            if (players == null) {
                players = new StringBuilder("<green>" + all.getUsername());
            } else {
                players.append("<gray>, ").append(all.getUsername());
            }
        }

        if(players == null) players = new StringBuilder("<red>None");

        source.sendMessage(mm.deserialize("<gray>Players<dark_gray>: " + players));
    }

    public boolean isProxyManaged() {
        return hasFlag(PROXY_MANAGED);
    }

    public boolean isProxyManagedLimbo(){
        return hasFlag(PROXY_MANAGED_LIMBO);
    }

    public boolean hasFlag(ServerFlag flag) {
        return (flags & flag.bit) == flag.bit;
    }

    private String onlineOfflineString(Boolean bool){
        return bool ? "<green>Online</green>" : "<red>Offline</red>";
    }

}
