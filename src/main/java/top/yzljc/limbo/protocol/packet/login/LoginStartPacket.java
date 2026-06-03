package top.yzljc.limbo.protocol.packet.login;

import top.yzljc.limbo.protocol.Packet;

public class LoginStartPacket
        implements Packet {

    private final String username;

    public LoginStartPacket(
            String username
    ) {
        this.username = username;
    }

    public String username() {
        return username;
    }
}