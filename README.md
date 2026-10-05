# Limbo

基于 Java 21、Netty 和 ViaVersion 的独立 Minecraft Java 版 Limbo 服务器，提供每位玩家独立的静态地图。

支持 **Java 1.8.x 至 26.3 的正式版协议**，包括 1.20.x、1.21.x、26.1–26.1.2、26.2、26.3。内部实现协议 47（1.8），由固定版本 ViaVersion 5.12.0 完成新版协议转换。未知协议直接拒绝，不把高于某个版本号的客户端默认当作兼容。

## 运行

需要 Java 21 或更新版本。服务端本身不要求安装 Minecraft 或其他服务端。

Windows，项目根目录：

```powershell
.\gradlew.bat build installDist
.\build\install\limbo\bin\limbo.bat
```

Linux/macOS：

```sh
./gradlew build installDist
./build/install/limbo/bin/limbo
```

默认监听 `0.0.0.0:25565`，读取当前工作目录的 `limbo.properties`。
也可以传入配置路径：

```powershell
.\build\install\limbo\bin\limbo.bat C:\servers\limbo\limbo.properties
```

`build/distributions/limbo-1.0-SNAPSHOT.zip` 是包含启动脚本、依赖、配置和文档的完整分发包。解压后在分发包根目录运行 `bin/limbo.bat` 或 `bin/limbo`。不要只复制 `build/libs` 中不带依赖的 JAR。

### Maven 仓库下载困难时

从 [ViaVersion 5.12.0 官方发布页](https://github.com/ViaVersion/ViaVersion/releases/tag/5.12.0) 下载 `ViaVersion-5.12.0.jar`，例如放在项目根目录的 `libs/` 中：

```powershell
.\gradlew.bat build installDist "-PviaJar=libs/ViaVersion-5.12.0.jar"
```

该文件的官方 SHA-256：

```text
72c40a6a702d67f226fc9a0d8ad82aba1483fdabe2e6159bcdddb2dc070750b0
```

本地 JAR 必须与固定的 5.12.0 API 和版本数据一致；分发包会自动包含所选 JAR，不需要额外安装 ViaVersion 插件。

## 配置

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| host | 0.0.0.0 | 监听地址 |
| port | 25565 | 监听端口 |
| motd | Limbo | 服务器列表说明；示例配置使用带版本范围的说明 |
| server-brand | Limbo | 客户端看到的 Server Brand，可自定义，最多 256 字符 |
| max-players | 1000 | 同时登录人数上限 |
| keepalive-seconds | 10 | 保活间隔 |
| timeout-seconds | 30 | 读取、登录和保活等待超时；必须大于保活间隔 |
| view-distance | 2 | 出生点周围区块半径，范围 2–8；另外发送地图实际占用的区块 |
| map-file | 空 | 空值使用内置小屋；或填写旧版 Alpha `.schematic` 路径 |
| map-origin-x / y / z | 0 / 0 / 0 | 导入地图的最小角坐标；内置地图不使用此偏移 |
| spawn-x / y / z | 0.5 / 64 / 0.5 | 玩家出生坐标 |
| spawn-yaw / pitch | 0 / 0 | 出生朝向，单位为度 |

配置文件按 UTF-8 读取，支持中文 MOTD。ViaVersion 会在当前工作目录的 `via/` 中生成自己的配置文件。

例如设置 `server-brand=My Network Limbo`，重启服务后，新连接的客户端会收到这个 Brand。它通过旧版 `MC|Brand` / 新版 `minecraft:brand` 消息发送，与服务器列表 MOTD 分开设置；是否显示及显示位置取决于客户端。

## 地图

维度固定为**末地**，默认加载悬空草地、小屋和树，不需要准备文件。玩家出生在小屋门前，生存模式、使用生存背包、不能飞行、无伤害；掉入虚空不会拉回。导入的地图同样加载在末地，没有维度配置项。

要替换成自己的建筑，使用 **1.8 方块组成的旧版 Alpha `.schematic`**（GZip NBT，包含 `Width`、`Height`、`Length`、`Blocks`、`Data`）。例如一个 32 × 16 × 32 的建筑，其第一层是地面：

```properties
map-file=maps/lobby.schematic
map-origin-x=-16
map-origin-y=63
map-origin-z=-16
spawn-x=0.5
spawn-y=64
spawn-z=0.5
spawn-yaw=0
spawn-pitch=0
```

路径相对于 `limbo.properties` 所在目录。出生点要留出两格空气，脚下有地板；以上示例需按你的建筑调整。重启后加载新地图。

地图宽、长各最多 128 格，高度加 Y 偏移不能超过 256。读取时保留方块 ID 和数据值，非法尺寸、缺失数组和 1.8 不支持的方块会导致启动报错，不会静默替换成空气。支持导入 `TileEntities` 中立式、墙上告示牌的 `Text1`～`Text4`，兼容旧式纯文本和 JSON 文字组件（含中文、颜色），坐标随地图偏移。暂不读取 Sponge `.schem`、世界存档、实体及其他方块实体内容，因此箱子内容、自定义头颅等不会导入。地图使用固定全亮照明，不运行光照、红石、流体、植物生长等更新。

游戏模式保持生存。1.21.4–26.3 不发送 `instabuild` 创造能力位，避免新版打开创造背包；通过独立的 `PICK_ITEM_FROM_BLOCK` 请求回应选中的物品，每次取得一个。1.8 保留原有本地中键能力。早期客户端在生存模式下可能不发送选取物品的同步包，因此 **1.9–1.21.3 的纯客户端选取后放置不保证服务器同步**；这不影响地图加载和中键在本地取得物品。

放置检查生存触及距离、玩家碰撞箱、目标是否可替换以及点击面/命中点；成功扣除一个物品，失败回传原方块和物品数量。支持普通方块、原木朝向、楼梯/半砖上下方向及半砖合并。普通方块按开始/取消/完成挖掘流程处理，不再按下左键就删除；挖掘进度由客户端依照硬度和工具计算，基岩等不可破坏，零硬度植物仍可瞬间破坏。

玩家背包由服务端记录完整的物品栏和鼠标持有物，支持左/右键拆分合并、Shift 移动、数字键交换、拖拽分配和背包自带的 **2×2 合成格**。合成配方采用地图对应的 1.8 基础配方：例如原木变木板、木板变木棍、四块木板变工作台；领取结果才扣除材料，支持 Shift 批量领取。再次中键选取当前手持同类方块会保留原有数量，放置只扣一个。关闭背包时合成材料和鼠标物品尽量退回物品栏。

关闭背包时禁用 Q / Ctrl+Q 丢弃：原生 1.8 直接忽略丢弃请求，保留尚未上报的本地中键物品；新版恢复客户端预扣的手持物品。打开背包后，仍可拿起物品并点击窗口外丢弃（左键整组、右键一个），也保留背包界面中的丢弃操作。

进服自动显示两行彩色 Limbo 提示，输入 `/limbo` 显示说明。说明中的 `/lobby` 是提示文案；实际切服仍需代理或大厅服务接入。

服务器记录收到的拆放方块时，仅修改当前连接的副本，最多 8192 个差异位置，重进即恢复。这里仍是静态 Limbo，不是完整的生存服务端：没有掉落物实体、工具耐久、NBT 特殊合成和方块容器界面，不支持多格方块放置，也不做完整的反作弊挖掘计时。背包满时无法退回的关闭背包溢出物、手动丢弃的物品不会生成掉落实体。旧版依赖邻居的栅栏等碰撞形状是近似值。没有玩家实体广播，其他玩家和其他人的改动都不可见。

## 接入代理

使用 `proxy-mode` 选择一种接入方式，修改后重启。默认 `none` 保留离线直连；`velocity` 使用 Velocity Modern Forwarding；`bungee` 支持 BungeeCord、Waterfall 和 Velocity 的 legacy 转发。启用代理模式后必须通过所配置的转发方式登录，不会在校验失败时退回离线身份。

两种代理模式均接收玩家 UUID、真实 IP 和带签名的皮肤属性，使用转发 UUID 登录，并将皮肤属性发送到自己的 TAB 条目。只信任 `proxy-trusted-addresses` 中的代理连接地址，支持逗号分隔的 IPv4、IPv6 和 CIDR；默认只允许本机代理，跨机器部署时填代理实际连接后端所用的地址。

### Velocity Modern Forwarding（1.13–26.3）

Limbo 的 `limbo.properties`：

```properties
host=127.0.0.1
port=25566
proxy-mode=velocity
proxy-trusted-addresses=127.0.0.1,::1
forwarding-secret-file=forwarding.secret
```

将 Velocity 使用的 `forwarding.secret` 复制到 Limbo 配置文件旁；密钥文件路径相对于 `limbo.properties`，不会打进分发包或输出到日志。缺失或空密钥会阻止启动，错误签名、姓名不符、未响应转发请求都会拒绝登录。Limbo 协商 forwarding version 1，校验 HMAC-SHA256，不依赖玩家聊天签名密钥。

Velocity 的 `velocity.toml` 合并以下设置（不要重复已有的配置段）：

```toml
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
limbo = "127.0.0.1:25566"
try = ["limbo"]
```

保留代理原有的正版认证设置；Limbo 后端本身不执行 Mojang 登录认证。参见 [Velocity 官方转发配置](https://docs.papermc.io/velocity/player-information-forwarding/)。Velocity Modern Forwarding 不支持原生 1.8–1.12.2 连接；需要这些客户端时使用下方 legacy 转发。

### BungeeCord / Waterfall / Velocity legacy（1.8–26.3）

Limbo：

```properties
host=127.0.0.1
port=25566
proxy-mode=bungee
proxy-trusted-addresses=127.0.0.1,::1
```

BungeeCord/Waterfall 的 `config.yml` 启用 `ip_forward: true`，并在现有 `servers` 中加入：

```yaml
ip_forward: true
servers:
  limbo:
    motd: Limbo
    address: 127.0.0.1:25566
    restricted: false
```

`ip_forward` 必须位于 Bungee 配置顶层，不能放到 `servers.limbo` 内；更改后完整重启代理。仅添加后端地址不会开启身份转发。如果显示 `BungeeCord IP forwarding is missing`，说明连接已通过可信地址检查，但握手未携带转发字段；若显示 `Invalid BungeeCord forwarding data`，查看 Limbo 控制台的格式错误原因。来源 IP 不在允许列表时会单独记录实际连接地址。

需要默认落入 Limbo 时，将现有监听器的 `priorities` 设置为包含 `limbo`；已有网络也可通过代理的 `/server limbo` 切入。Velocity legacy 则使用 `player-info-forwarding-mode = "legacy"`，后端地址配置同上。代理本身也必须支持所接入客户端的版本；Limbo 的 ViaVersion 不会扩展代理前端支持的版本。

传统转发没有密钥签名，因此可信代理地址限制不可省略；后端同机部署可直接绑定 `127.0.0.1`。这两种模式是分别可选的，不混用，也不接受玩家绕过代理直接注入转发资料。`/lobby` 的切服命令需要在代理侧提供；目前 Limbo 的 `/limbo` 只显示说明。

## 行为与边界

- 支持服务器列表状态查询、真实在线人数和 Ping。
- 直连模式根据 `OfflinePlayer:<用户名>` 生成稳定 UUID；代理模式使用验证后的转发 UUID，认证由代理负责。
- 玩家以生存模式进入末地的独立假地图，保持生存背包，默认出生点为 `(0.5, 64, 0.5)`。
- 默认发送 5 × 5 个区块（包含内置建筑）、中键选取能力、时间和位置同步。
- 不进行边界或虚空拉回，不运行地形生成、实体 AI、存档或伤害逻辑。
- 验证保活响应，清理掉线会话，拒绝重复用户名和超额连接。
- 新版本注册表、Tags、配置确认、传送确认和包格式由 ViaVersion 转换。
- 暂无账号系统、队列业务、聊天广播或后端切服指令。离线直连用户名不是已认证身份。
- 当前 TAB 仅包含自己。代理转发的皮肤属性和签名会保留，正版代理连接可供客户端渲染头像；同步帽子、外套、袖子、裤腿等第二层显示标记，并跟随客户端“皮肤自定义”开关更新，1.21.4+ 另行同步 TAB 帽子层。离线直连不查询皮肤，旧版原版客户端还会在未加密离线连接中隐藏头像。
- 不支持 Bedrock、1.7 及更早版本，也不承诺快照、预发布版或未来版本。

## 验证

```powershell
.\gradlew.bat test
```

测试包括：

- VarInt 正负边界、分片/粘包、非法帧长度、出站缓冲区释放。
- 全部 **50 个正式协议编号**的真实 TCP 状态查询和 Ping。
- 1.20.2–26.3 全部 14 个协议编号的登录、注册表 NBT、配置确认、完整 JoinGame、25 个区块、位置确认和两轮保活。
- 未知版本拒绝、重复登录处理、持续发送移动包但不回答保活时的超时断开。
- 自定义 Brand 的 UTF-8 配置读取，以及旧版和全部新版客户端的实际消息传输。
- 默认地图出生点、区块内方块编码、跨区块 schematic 导入、错误地图拒绝、玩家改动隔离。
- 所有新版的生存模式及选取能力，1.21.4–26.3 的中键请求与物品回应；真实 TCP 双玩家拆放隔离和掉虚空不拉回。
- 26.3 不带创造能力位的能力包、放置碰撞拒绝及物品扣除；背包拆分合并、整组物品放置、2×2 合成材料消耗与网络同步。
- schematic 告示牌富文本、中文、空行及偏移坐标；进服提示与 `/limbo`；巨型蘑菇块挖掘、1.8 本地中键后禁止快捷丢弃、现代库存恢复和背包内丢弃。
- Velocity 1.13 登录插件协商，以及两种代理在 1.20.2、1.21.4、26.1、26.2、26.3 下的 UUID、皮肤和完整登录；错误密钥、资料篡改、姓名不符、直连、非可信代理和转发超时拒绝。
- 皮肤外层模型标志及自定义开关，1.21.4+ 的 TAB 帽子层更新；另使用官方 BungeeCord 2102 验证 1.8、1.20.1、1.21.4、26.1 下的实际代理转发（`tools/protocol-smoke/skin-smoke.cjs`，通过 `LIMBO_PORT` 指定测试代理端口）。这些检查验证收发数据，不代替 GUI 渲染验收。

独立客户端测试使用 PrismarineJS `minecraft-protocol@1.68.0`，避免只依靠同一套服务端协议实现自测：

```powershell
npm ci --prefix tools/protocol-smoke --ignore-scripts
$env:LIMBO_PORT = "25565"
node tools/protocol-smoke/smoke.cjs
```

可指定版本，例如：

```powershell
node tools/protocol-smoke/smoke.cjs 1.20.2 1.21.11 26.1
```

测试自定义 Brand 时，将环境变量 `LIMBO_BRAND` 设置为配置中的值；测试器会校验客户端收到的 Brand。默认期望 `Limbo`。脚本按内置地图验证，1.21.4 及以上还会中键选取出生点脚下的圆石并核对返回物品；运行此脚本时保持 `map-file=`。

49 个客户端版本名称覆盖 1.8–26.1 的全部 48 个协议编号。该客户端库尚未支持 26.2/26.3，这两个版本使用上述 TCP 探针验证，并按官方 ViaVersion 源码核对新增字段：26.2 的登录会话 UUID、26.3 的列表型注册表及带坐标/视角的传送确认。**自动测试不等于所有原版图形客户端都已人工进入游戏验证**，也不代表模组兼容性或并发容量测试。

## 代码结构

- `LimboServer`：监听、连接管线、人数管理和关闭。
- `ServerConfig`：配置读取和合法性检查。
- `network/ClientHandler`：1.8 内部状态机、登录、保活和地图交互。
- `world/`：静态地图、schematic 读取、出生点设置和每个玩家的临时方块副本。
- `network/PacketFrame*`：长度帧处理；出站顺序为协议转换后再封装长度。
- `protocol/LimboPackets`：完整的最小 1.8 Limbo 数据包。
- `via/`：ViaVersion 平台、固定内部协议和支持范围。
- `src/test/`：单元测试及网络探针。
- `tools/protocol-smoke/`：独立客户端矩阵测试。
- 各版本注册表由 ViaVersion 提供，不再保留旧的手工注册表文件。

升级支持版本时，需要更新 ViaVersion 固定依赖、`ViaSupport.supported` 的最高版本、显示文案和测试矩阵，然后重新验证。不要仅放宽版本判断。

最初项目参考 [LOOHP/Limbo](https://github.com/LOOHP/Limbo)。当前协议转换依赖的许可与来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
