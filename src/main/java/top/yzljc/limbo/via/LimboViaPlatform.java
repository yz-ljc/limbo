package top.yzljc.limbo.via;

import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import java.io.File;
import java.util.logging.Logger;

public final class LimboViaPlatform extends UserConnectionViaVersionPlatform {
    public LimboViaPlatform(File directory) { super(directory); }
    @Override public Logger createLogger(String name) { return Logger.getLogger("Limbo." + name); }
    @Override public boolean isProxy() { return false; }
    @Override public String getPlatformName() { return "Limbo"; }
    @Override public String getPlatformVersion() { return "1.0"; }
}
