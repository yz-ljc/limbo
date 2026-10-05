package top.yzljc.limbo.proxy;

import com.google.common.net.InetAddresses;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import top.yzljc.limbo.network.VarInts;
import top.yzljc.limbo.util.MinecraftTypes;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class Forwarding {
    private Forwarding() {}
    public static PlayerProfile bungee(String host, String username) {
        String[] fields = host.split("\u0000", -1);
        require(fields.length == 3 || fields.length == 4, "BungeeCord IP forwarding is required");
        require(!fields[0].isEmpty() && fields[0].length() <= 255, "Invalid forwarded host");
        var address = InetAddresses.forString(fields[1]);
        String id = fields[2];
        require(id.matches("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"), "Invalid forwarded UUID");
        if (id.length() == 32) id = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20);
        List<PlayerProfile.Property> properties = new ArrayList<>();
        if (fields.length == 4) {
            var array = JsonParser.parseString(fields[3]).getAsJsonArray();
            require(array.size() <= 16, "Too many forwarded properties");
            for (var entry : array) {
                var p = entry.getAsJsonObject();
                String name = p.get("name").getAsString(), value = p.get("value").getAsString();
                String signature = p.has("signature") && !p.get("signature").isJsonNull() ? p.get("signature").getAsString() : null;
                require(name.length() <= 64 && value.length() <= 32767 && (signature == null || signature.length() <= 32767), "Oversized profile property");
                properties.add(new PlayerProfile.Property(name, value, signature));
            }
        }
        return new PlayerProfile(UUID.fromString(id), username, address, properties);
    }
    public static PlayerProfile velocity(ByteBuf data, ProxySettings settings, String expectedUsername) {
        require(data.readableBytes() >= 33 && data.readableBytes() <= 131072, "Invalid Velocity payload size");
        byte[] signature = new byte[32], payload = new byte[data.readableBytes() - 32];
        data.readBytes(signature); data.getBytes(data.readerIndex(), payload);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            require(MessageDigest.isEqual(signature, mac.doFinal(payload)), "Invalid Velocity forwarding signature");
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
        require(VarInts.read(data) == 1, "Unsupported Velocity forwarding version");
        var address = InetAddresses.forString(MinecraftTypes.readString(data, 45));
        UUID uuid = new UUID(data.readLong(), data.readLong());
        String username = MinecraftTypes.readString(data, 16);
        require(username.equals(expectedUsername), "Forwarded username does not match login");
        int count = VarInts.read(data);
        require(count >= 0 && count <= 16, "Too many forwarded properties");
        List<PlayerProfile.Property> properties = new ArrayList<>();
        for (int i = 0; i < count; i++) properties.add(new PlayerProfile.Property(
                MinecraftTypes.readString(data, 64), MinecraftTypes.readString(data, 32767),
                data.readBoolean() ? MinecraftTypes.readString(data, 32767) : null));
        require(!data.isReadable(), "Trailing forwarding data");
        return new PlayerProfile(uuid, username, address, properties);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new CorruptedFrameException(message);
    }
}
