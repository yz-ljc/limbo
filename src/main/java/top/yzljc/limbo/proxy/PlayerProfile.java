package top.yzljc.limbo.proxy;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public record PlayerProfile(UUID uuid, String username, InetAddress address, List<Property> properties) {
    public PlayerProfile { properties = List.copyOf(properties); }
    public record Property(String name, String value, String signature) {}
    public static PlayerProfile offline(String username, InetAddress address) {
        return new PlayerProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)),
                username, address, List.of());
    }
}
