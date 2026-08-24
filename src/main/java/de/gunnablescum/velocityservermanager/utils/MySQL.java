package de.gunnablescum.velocityservermanager.utils;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.gunnablescum.velocityservermanager.ServerManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.text.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by Noah Fetz on 20.05.2016.
 * Contributors: GunnableScum
 */
@SuppressWarnings("CallToPrintStackTrace") // I don't care about proper logging, if the DBMS or internet fails, the console and logs already gets to see it.
public class MySQL {

    private static HikariDataSource dataSource;

    public static void init() {
        Config databaseconfig;
        try {
            databaseconfig = new Config(ServerManager.getInstance().getDataDirectory(), "mysql.yml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                databaseconfig.getString("MySQL.Host", "localhost"),
                databaseconfig.getInt("MySQL.Port", 3306),
                databaseconfig.getString("MySQL.Database", "VelocityProxyManager")));
        config.setUsername(databaseconfig.getString("MySQL.User", "root"));
        config.setPassword(databaseconfig.getString("MySQL.Password", ""));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setAutoCommit(false);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try {
            dataSource = new HikariDataSource(config);
        } catch(Exception ex) {
            ServerManager.getInstance().getLogger().error("Couldn't connect to Database. Please reconfigure the Plugin with proper Database Credentials!");
            ServerManager.getInstance().getProxyServer().shutdown(Component.text("VelocityServerManager: Couldn't connect to Database. Please reconfigure the Plugin with proper Database Credentials!"));
            ex.printStackTrace();
            return;
        }

        createTable();
    }

    public static boolean isConnected() {
        if(dataSource == null) return false;
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public static void createTable(){
        try {
            update("CREATE TABLE IF NOT EXISTS servermanager_servers(id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, name TEXT, ip TEXT, port INT, flags INT)", new ArrayList<>());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void update(String qry, @Nullable List<SQLStatementParameter> parameters){
        try(Connection connection = dataSource.getConnection()) {
            try(PreparedStatement ps = connection.prepareStatement(qry)) {
                if (parameters != null) {
                    for(SQLStatementParameter parameter : parameters) {
                        switch (parameter.type()) {
                            case STRING -> ps.setString(parameter.index(), (String) parameter.value());
                            case INT -> ps.setInt(parameter.index(), (int) parameter.value());
                            case BOOL -> ps.setBoolean(parameter.index(), (boolean) parameter.value());
                            case LONG -> ps.setLong(parameter.index(), (long) parameter.value());
                            case BYTE -> ps.setByte(parameter.index(), (byte) parameter.value());
                        }
                    }
                }

                ps.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e; // Rethrow the exception after rollback to trigger console logging
            }
        } catch (SQLException e) {
            ServerManager.getInstance().getLogger().error("VelocityServerManager: Something went wrong while connecting to the database, error details are below.");
            e.printStackTrace();
        }
    }

    @Nullable
    public static DatabaseRegisteredServer getServer(String name) {
        try(Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM servermanager_servers WHERE name = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            DatabaseRegisteredServer server = new DatabaseRegisteredServer(
                    rs.getString("name"),
                    rs.getString("ip"),
                    rs.getInt("port"),
                    rs.getByte("flags")
            );
            rs.close();
            return server;
        } catch(SQLException e) {
            ServerManager.getInstance().getLogger().error("VelocityServerManager: Something went wrong while connecting to the database, error details are below.");
            e.printStackTrace();
        }
        return null;
    }

    public static boolean doesServerExist(String name) {
        return getServer(name) != null;
    }

    public static List<DatabaseRegisteredServer> getAllServers() {
        List<DatabaseRegisteredServer> servers = new ArrayList<>();
        try(
                Connection connection= dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM servermanager_servers");
                ResultSet rs = ps.executeQuery()
        ) {
            while(rs.next()){
                DatabaseRegisteredServer server = new DatabaseRegisteredServer(
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getInt("port"),
                        rs.getByte("flags")
                );
                servers.add(server);
            }
            return servers;
        } catch (SQLException e) {
            ServerManager.getInstance().getLogger().error("VelocityServerManager: Something went wrong while connecting to the database, error details are below.");
            e.printStackTrace();
        }
        return List.of();
    }

    public static List<DatabaseRegisteredServer> getAllServerWithLobbyFlag() {
        List<DatabaseRegisteredServer> servers = new ArrayList<>();
        try(
                Connection connection= dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM servermanager_servers WHERE flags = ?");
        ) {
            ps.setByte(1,ServerFlag.LOBBY.bit);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                DatabaseRegisteredServer server = new DatabaseRegisteredServer(
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getInt("port"),
                        rs.getByte("flags")
                );
                servers.add(server);
            }
            return servers;
        } catch (SQLException e) {
            ServerManager.getInstance().getLogger().error("VelocityServerManager: Something went wrong while connecting to the database, error details are below.");
            e.printStackTrace();
        }
        return List.of();
    }

    public static void createServer(String name, String ip, int port) {
        update("INSERT INTO servermanager_servers(name, ip, port, flags) VALUES(?, ?, ?, ?)", List.of(
                new SQLStatementParameter(SQLStatementParameterType.STRING, 1, name),
                new SQLStatementParameter(SQLStatementParameterType.STRING, 2, ip),
                new SQLStatementParameter(SQLStatementParameterType.INT, 3, port),
                new SQLStatementParameter(SQLStatementParameterType.BYTE, 4, ServerFlag.EMPTY.bit)
        ));
        Objects.requireNonNull(getServer(name)).addToProxy();
    }

    public static void insertFallbackServer(RegisteredServer server) {
        update("INSERT INTO servermanager_servers(name, ip, port, flags) VALUES(?, ?, ?, ?)", List.of(
                new SQLStatementParameter(SQLStatementParameterType.STRING, 1, server.getServerInfo().getName()),
                new SQLStatementParameter(SQLStatementParameterType.STRING, 2, server.getServerInfo().getAddress().getHostString()),
                new SQLStatementParameter(SQLStatementParameterType.INT, 3, server.getServerInfo().getAddress().getPort()),
                new SQLStatementParameter(SQLStatementParameterType.BYTE, 4, ServerFlag.PROXY_MANAGED.bit) // 2 = Don't manage with VSM
        ));
    }

    public static void deleteServer(String name) {
        update("DELETE FROM servermanager_servers WHERE name = ?", List.of(
                new SQLStatementParameter(SQLStatementParameterType.STRING, 1, name)
        ));
    }

    public static void insertFallbackServers(List<RegisteredServer> servers) {
        servers.forEach(MySQL::insertFallbackServer);
    }

    public static void deleteFallbackServers() {
        update("DELETE FROM servermanager_servers WHERE flags = 9", null);
    }
}
