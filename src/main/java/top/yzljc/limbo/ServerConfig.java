package top.yzljc.limbo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import top.yzljc.limbo.world.MapSettings;
import top.yzljc.limbo.proxy.ProxySettings;

public record ServerConfig(String host, int port, String motd, String serverBrand, int maxPlayers,
                           int keepAliveSeconds, int timeoutSeconds, int viewDistance, MapSettings map, ProxySettings proxy) {
    public ServerConfig(String host, int port, String motd, String serverBrand, int maxPlayers,
                        int keepAliveSeconds, int timeoutSeconds, int viewDistance, MapSettings map) {
        this(host, port, motd, serverBrand, maxPlayers, keepAliveSeconds, timeoutSeconds, viewDistance, map, ProxySettings.defaults());
    }
    public ServerConfig(String host, int port, String motd, String serverBrand, int maxPlayers,
                        int keepAliveSeconds, int timeoutSeconds, int viewDistance) {
        this(host, port, motd, serverBrand, maxPlayers, keepAliveSeconds, timeoutSeconds, viewDistance, MapSettings.defaults());
    }
    public ServerConfig {
        if (map == null) throw new IllegalArgumentException("map settings are required");
        if (proxy == null) throw new IllegalArgumentException("proxy settings are required");
        if (host == null || motd == null) throw new IllegalArgumentException("host and motd are required");
        if (serverBrand == null || serverBrand.length() > 256)
            throw new IllegalArgumentException("server-brand must be at most 256 characters");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be 0..65535");
        if (maxPlayers < 1) throw new IllegalArgumentException("max-players must be positive");
        if (keepAliveSeconds < 1 || timeoutSeconds <= keepAliveSeconds)
            throw new IllegalArgumentException("timeout-seconds must exceed keepalive-seconds > 0");
        if (viewDistance < 2 || viewDistance > 8) throw new IllegalArgumentException("view-distance must be 2..8");
    }
    public static ServerConfig load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        }
        String mapFile = p.getProperty("map-file", "").trim();
        Path mapPath = mapFile.isEmpty() ? null : file.toAbsolutePath().getParent().resolve(mapFile).normalize();
        MapSettings map = new MapSettings(mapPath,
                integer(p, "map-origin-x", 0), integer(p, "map-origin-y", 0), integer(p, "map-origin-z", 0),
                decimal(p, "spawn-x", 0.5), decimal(p, "spawn-y", 64), decimal(p, "spawn-z", 0.5),
                (float) decimal(p, "spawn-yaw", 0), (float) decimal(p, "spawn-pitch", 0));
        return new ServerConfig(p.getProperty("host", "0.0.0.0"), integer(p, "port", 25565),
                p.getProperty("motd", "Limbo"), p.getProperty("server-brand", "Limbo"), integer(p, "max-players", 1000),
                integer(p, "keepalive-seconds", 10), integer(p, "timeout-seconds", 30),
                integer(p, "view-distance", 2), map, ProxySettings.load(p, file));
    }
    private static double decimal(Properties p, String key, double fallback) {
        return Double.parseDouble(p.getProperty(key, Double.toString(fallback)));
    }
    private static int integer(Properties p, String key, int fallback) {
        return Integer.parseInt(p.getProperty(key, Integer.toString(fallback)));
    }
}
