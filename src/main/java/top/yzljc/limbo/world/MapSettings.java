package top.yzljc.limbo.world;

import java.nio.file.Path;

public record MapSettings(Path file, int originX, int originY, int originZ,
                          double spawnX, double spawnY, double spawnZ, float yaw, float pitch) {
    public static MapSettings defaults() { return new MapSettings(null, 0, 0, 0, 0.5, 64, 0.5, 0, 0); }
    public MapSettings {
        if (Math.abs((long) originX) > 29_999_000 || Math.abs((long) originZ) > 29_999_000
                || originY < 0 || originY > 255)
            throw new IllegalArgumentException("Map origin outside supported world bounds");
        if (!Double.isFinite(spawnX) || !Double.isFinite(spawnY) || !Double.isFinite(spawnZ)
                || Math.abs(spawnX) > 29_999_000 || Math.abs(spawnZ) > 29_999_000
                || spawnY < 0 || spawnY > 256 || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("Invalid map spawn position/rotation");
    }
}
