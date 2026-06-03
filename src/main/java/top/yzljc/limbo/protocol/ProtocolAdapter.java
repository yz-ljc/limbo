package top.yzljc.limbo.protocol;

import top.yzljc.limbo.session.Session;

public interface ProtocolAdapter {

    int protocolVersion();

    void sendLoginSuccess(Session session);

    void sendConfiguration(Session session);

    void sendJoinGame(Session session);

    void sendKeepAlive(Session session);

}