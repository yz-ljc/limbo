package top.yzljc.limbo.via;

import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import java.nio.file.Path;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import top.yzljc.limbo.network.ClientHandler;

public final class ViaSupport {
    private ViaSupport() {}
    public static void sendTabHat(UserConnection user, java.util.UUID uuid, boolean visible) {
        var packet = com.viaversion.viaversion.api.protocol.packet.PacketWrapper.create(
                com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ClientboundPackets1_21_2.PLAYER_INFO_UPDATE, user);
        var actions = new java.util.BitSet(8);
        actions.set(7); // UPDATE_HAT, added in 1.21.4
        packet.write(com.viaversion.viaversion.api.type.Types.PROFILE_ACTIONS_ENUM1_21_4, actions);
        packet.write(com.viaversion.viaversion.api.type.Types.VAR_INT, 1);
        packet.write(com.viaversion.viaversion.api.type.Types.UUID, uuid);
        packet.write(com.viaversion.viaversion.api.type.Types.BOOLEAN, visible);
        packet.send(com.viaversion.viaversion.protocols.v1_21_2to1_21_4.Protocol1_21_2To1_21_4.class);
    }
    public static synchronized void initialize(Path directory) {
        if (Via.isLoaded()) return;
        ViaManagerImpl.initAndLoad(new LimboViaPlatform(directory.toFile()), new NoopInjector() {
            @Override public ProtocolVersion getServerProtocolVersion() { return ProtocolVersion.v1_8; }
            @Override public java.util.SortedSet<ProtocolVersion> getServerProtocolVersions() {
                return new java.util.TreeSet<>(java.util.Set.of(ProtocolVersion.v1_8));
            }
        }, new ViaCommandHandler(false), new ViaPlatformLoader() {
            @Override public void load() {
                Via.getManager().getProviders().use(PickItemProvider.class, new PickItemProvider() {
                    @Override public void pickItemFromBlock(UserConnection user, BlockPosition position, boolean includeData) {
                        var handler = user.getChannel().pipeline().get(ClientHandler.class);
                        if (handler != null) handler.pickBlock(position.x(), position.y(), position.z());
                    }
                });
                Via.getManager().getProviders().use(VersionProvider.class, new BaseVersionProvider() {
                    @Override public ProtocolVersion getClosestServerProtocol(UserConnection connection) {
                        return ProtocolVersion.v1_8;
                    }
                });
            }
            @Override public void unload() {}
        });
    }
    public static boolean supported(ProtocolVersion version) {
        return ProtocolVersion.isRegistered(version.getVersion())
                && !version.isSnapshot()
                && version.newerThanOrEqualTo(ProtocolVersion.v1_8)
                && version.olderThanOrEqualTo(ProtocolVersion.v26_3)
                && (version.equals(ProtocolVersion.v1_8) ||
                    Via.getManager().getProtocolManager().getProtocolPath(version, ProtocolVersion.v1_8) != null);
    }
}
