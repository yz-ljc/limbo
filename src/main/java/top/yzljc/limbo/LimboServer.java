package top.yzljc.limbo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import top.yzljc.limbo.network.ClientHandler;
import top.yzljc.limbo.network.PacketFrameDecoder;

public class LimboServer {

    public static void main(String[] args)
            throws Exception {

        EventLoopGroup boss =
                new NioEventLoopGroup(1);

        EventLoopGroup worker =
                new NioEventLoopGroup();

        try {

            ServerBootstrap bootstrap =
                    new ServerBootstrap();

            bootstrap
                    .group(boss, worker)
                    .channel(
                            NioServerSocketChannel.class
                    )
                    .childHandler(
                            new ChannelInitializer<>() {

                                @Override
                                protected void initChannel(
                                        Channel ch
                                ) {

                                    ch.pipeline()
                                            .addLast(
                                                    new PacketFrameDecoder()
                                            )
                                            .addLast(
                                                    new ClientHandler()
                                            );

                                }
                            });

            ChannelFuture future =
                    bootstrap.bind(25565)
                            .sync();

            System.out.println(
                    "Limbo started"
            );

            future.channel()
                    .closeFuture()
                    .sync();

        } finally {

            boss.shutdownGracefully();
            worker.shutdownGracefully();

        }
    }
}