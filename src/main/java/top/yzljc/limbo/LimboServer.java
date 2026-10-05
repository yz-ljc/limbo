package top.yzljc.limbo;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import top.yzljc.limbo.network.*;
import top.yzljc.limbo.via.ViaSupport;
import top.yzljc.limbo.world.FakeWorld;
import top.yzljc.limbo.proxy.VelocityLoginHandler;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class LimboServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Limbo");
    private final ServerConfig config;
    private final FakeWorld world;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();
    private final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ConcurrentHashMap<UUID, Channel> players = new ConcurrentHashMap<>();
    private Channel listener;
    private boolean closed;

    public LimboServer(ServerConfig config) throws java.io.IOException {
        this.config = config;
        this.world = FakeWorld.load(config.map());
    }
    public synchronized LimboServer start() throws InterruptedException {
        if (listener != null || closed) throw new IllegalStateException("Server already started or closed");
        listener = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        clients.add(ch);
                        var connection = ViaChannelInitializer.createUserConnection(ch, false);
                        ch.pipeline()
                                .addLast("read-timeout", new ReadTimeoutHandler(config.timeoutSeconds()))
                                .addLast("frame-decoder", new PacketFrameDecoder())
                                .addLast("frame-encoder", new PacketFrameEncoder())
                                .addLast("proxy-forwarding", new VelocityLoginHandler(config.proxy()))
                                .addLast(Via.getManager().getInjector().getDecoderName(), new ViaDecodeHandler(connection))
                                .addLast(Via.getManager().getInjector().getEncoderName(), new ViaEncodeHandler(connection))
                                .addLast("limbo", new ClientHandler(LimboServer.this, connection));
                    }
                }).bind(config.host(), config.port()).sync().channel();
        LOGGER.info("Limbo listening on " + listener.localAddress() + " (proxy=" + config.proxy().mode() + ", Java 1.8 - 26.3)");
        return this;
    }
    public ServerConfig config() { return config; }
    public FakeWorld world() { return world; }
    public int port() { return ((java.net.InetSocketAddress) listener.localAddress()).getPort(); }
    public int onlinePlayers() { return players.size(); }
    public synchronized boolean admit(UUID uuid, Channel channel) {
        return !closed && players.size() < config.maxPlayers() && players.putIfAbsent(uuid, channel) == null;
    }
    public void remove(UUID uuid, Channel channel) { if (uuid != null) players.remove(uuid, channel); }
    @Override public void close() {
        Channel serverChannel;
        synchronized (this) {
            if (closed) return;
            closed = true;
            serverChannel = listener;
        }
        // Never hold the admission lock while waiting for event loops to stop.
        if (serverChannel != null) serverChannel.close().syncUninterruptibly();
        clients.close().awaitUninterruptibly();
        boss.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        workers.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
    }
    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length == 0 ? "limbo.properties" : args[0]);
        ServerConfig config = ServerConfig.load(configPath);
        ViaSupport.initialize(Path.of("via"));
        LimboServer server = new LimboServer(config);
        var stopping = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable shutdown = () -> {
            if (!stopping.compareAndSet(false, true)) return;
            server.close();
            ((com.viaversion.viaversion.ViaManagerImpl) Via.getManager()).destroy();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown, "limbo-shutdown"));
        try {
            server.start();
            server.listener.closeFuture().sync();
        } finally {
            shutdown.run();
        }
    }
}
