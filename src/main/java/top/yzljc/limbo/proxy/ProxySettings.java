package top.yzljc.limbo.proxy;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProxySettings {
    public enum Mode { NONE, VELOCITY, BUNGEE }
    private final Mode mode;
    private final String secret;
    private final List<Network> trusted;

    public ProxySettings(Mode mode, String secret, List<String> trusted) {
        this.mode = Objects.requireNonNull(mode);
        this.secret = Objects.requireNonNull(secret);
        this.trusted = trusted.stream().map(Network::parse).toList();
        if (mode == Mode.VELOCITY && secret.isBlank()) throw new IllegalArgumentException("Velocity requires a forwarding secret");
        if (mode != Mode.NONE && this.trusted.isEmpty()) throw new IllegalArgumentException("proxy-trusted-addresses must not be empty");
    }
    public Mode mode() { return mode; }
    String secret() { return secret; }
    public boolean trusted(InetAddress address) { return trusted.stream().anyMatch(n -> n.contains(address)); }
    public static ProxySettings defaults() { return new ProxySettings(Mode.NONE, "", List.of("127.0.0.1", "::1")); }
    public static ProxySettings load(Properties p, Path config) throws IOException {
        Mode mode = Mode.valueOf(p.getProperty("proxy-mode", "none").trim().toUpperCase(Locale.ROOT));
        String secret = "";
        if (mode == Mode.VELOCITY) {
            Path file = config.toAbsolutePath().getParent().resolve(p.getProperty("forwarding-secret-file", "forwarding.secret").trim());
            secret = Files.readString(file).trim();
        }
        List<String> trusted = Arrays.stream(p.getProperty("proxy-trusted-addresses", "127.0.0.1,::1").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new ProxySettings(mode, secret, trusted);
    }
    @Override public String toString() { return "ProxySettings[mode=" + mode + "]"; }

    private record Network(byte[] address, int prefix) {
        static Network parse(String input) {
            String[] parts = input.split("/", -1);
            if (parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy network");
            byte[] address = InetAddresses.forString(parts[0]).getAddress(); // literals only, never DNS
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
            if (prefix < 0 || prefix > address.length * 8) throw new IllegalArgumentException("Invalid trusted proxy prefix");
            return new Network(address, prefix);
        }
        boolean contains(InetAddress other) {
            byte[] bytes = other.getAddress();
            if (bytes.length != address.length) return false;
            for (int bit = 0; bit < prefix; bit++)
                if (((bytes[bit / 8] ^ address[bit / 8]) & (1 << (7 - bit % 8))) != 0) return false;
            return true;
        }
    }
}
