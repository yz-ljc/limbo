package top.yzljc.limbo.protocol;

import java.util.HashMap;
import java.util.Map;

public class ProtocolManager {

    private final Map<Integer, ProtocolAdapter> protocols =
            new HashMap<>();

    public void register(ProtocolAdapter adapter) {
        protocols.put(
                adapter.protocolVersion(),
                adapter
        );
    }

    public ProtocolAdapter get(int version) {
        return protocols.get(version);
    }

}