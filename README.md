# Limbo

面向 Minecraft Java 服务器网络的独立 Limbo 后端，为玩家提供隔离的末地场景。适用于大厅维护、连接等待等临时停留场景，可接入 BungeeCord、Waterfall 和 Velocity。

支持 Minecraft Java **1.8.x–26.3 正式版**，需要 **Java 21 或更高版本**。

## 功能特性

- **独立场景**：玩家之间互不可见，方块修改仅对当前玩家生效，重新连接后恢复地图。
- **自定义地图**：支持 Alpha `.schematic`，保留方块朝向、告示牌文字及文字样式。
- **生存交互**：生存背包、方块放置与挖掘、物品整理、背包内 2×2 合成。
- **物品选取**：支持中键选取；背包外禁用 Q / Ctrl+Q 丢弃，背包内可正常丢弃。
- **代理接入**：支持传统转发和 Velocity Modern Forwarding，接收玩家 UUID、IP 与皮肤资料。
- **玩家显示**：TAB 显示玩家本人，同步皮肤第二层及帽子显示设置。
- **服务器信息**：可配置 MOTD、Server Brand、在线人数上限和出生点。

场景固定为末地，无伤害，掉入虚空不会传送回出生点。进服显示 Limbo 提示，`/limbo` 提供场景说明；`/lobby` 等切服命令由代理侧配置。

## 安装与启动

解压 `limbo-1.0-SNAPSHOT.zip`，编辑根目录的 `limbo.properties`，然后从该目录启动服务。

**Windows**

```powershell
.\bin\limbo.bat
```

**Linux / macOS**

```sh
./bin/limbo
```

分发包包含启动脚本、运行依赖、配置、地图及许可证。`lib/` 目录需与启动脚本一同部署。

当前随包配置使用 **25566 端口、Bungee 传统转发和 `worlds/limbo.schematic` 地图**。代理接入方式见下一节。离线直连可将 `proxy-mode` 改为 `none`。

启动脚本也支持指定配置文件：

```powershell
.\bin\limbo.bat C:\servers\limbo\limbo.properties
```

配置文件采用 UTF-8 编码，修改后重启生效。地图和密钥文件的相对路径均以配置文件所在目录为基准。

## 代理接入

| 接入方式 | `proxy-mode` | 客户端版本 |
| --- | --- | --- |
| BungeeCord / Waterfall | `bungee` | 1.8.x–26.3 |
| Velocity legacy | `bungee` | 1.8.x–26.3 |
| Velocity Modern Forwarding | `velocity` | 1.13–26.3 |
| 离线直连 | `none` | 1.8.x–26.3 |

代理本身也需支持相应的客户端版本。玩家身份认证由代理负责，Limbo 使用代理转发的 UUID 和皮肤资料。

### BungeeCord / Waterfall

同机部署时，Limbo 的 `limbo.properties`：

```properties
host=127.0.0.1
port=25566
proxy-mode=bungee
proxy-trusted-addresses=127.0.0.1,::1
```

BungeeCord / Waterfall 的 `config.yml`：

```yaml
ip_forward: true

servers:
  limbo:
    motd: Limbo
    address: 127.0.0.1:25566
    restricted: false
```

`ip_forward` 位于配置顶层，与 `servers` 同级。修改后完整重启代理。通过 `/server limbo` 连接；将 `limbo` 加入监听器的 `priorities` 可将其设为入口或后备服务器。

### Velocity

**Modern Forwarding**

Limbo 的 `limbo.properties`：

```properties
host=127.0.0.1
port=25566
proxy-mode=velocity
proxy-trusted-addresses=127.0.0.1,::1
forwarding-secret-file=forwarding.secret
```

Velocity 的 `velocity.toml`：

```toml
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
limbo = "127.0.0.1:25566"
try = ["limbo"]
```

将以上条目合并到现有配置，并将代理使用的 `forwarding.secret` 复制到 Limbo 配置文件旁。两端密钥内容必须一致。

**Legacy 转发**

接入 1.8–1.12.2 客户端时，Velocity 设置 `player-info-forwarding-mode = "legacy"`，Limbo 设置 `proxy-mode=bungee`。后端地址配置与上例相同。

### 可信代理地址

`proxy-trusted-addresses` 填写代理连接 Limbo 时使用的来源地址，支持多个 IP 或 CIDR，以逗号分隔。

| 部署方式 | 配置示例 |
| --- | --- |
| 同机回环连接 | `127.0.0.1,::1` |
| 独立代理机器 | `192.168.1.10` |
| 多台代理 | `192.168.1.10,192.168.1.11` |
| 容器网络 | `172.18.0.0/16`，按实际网段配置 |

`0.0.0.0` 在此处不是通配符。跨机器或容器部署时，还需调整 Limbo 的监听地址与代理中的后端地址。传统转发依赖可信来源限制；启用代理模式后，玩家通过代理入口连接。

## 地图配置

支持使用 **Minecraft 1.8 方块的 Alpha `.schematic` 文件**。地图宽、长各不超过 128 格，导入后的方块高度范围为 Y=0–255。

```properties
map-file=worlds/limbo.schematic
map-origin-x=0
map-origin-y=0
map-origin-z=0
spawn-x=24.5
spawn-y=30
spawn-z=21.5
spawn-yaw=-90
spawn-pitch=0
```

以上坐标适用于随包地图。替换地图时，需一并调整地图偏移和出生点；出生位置应留出两格空气并位于地面上方。

使用内置小屋地图的配置：

```properties
map-file=
spawn-x=0.5
spawn-y=64
spawn-z=0.5
spawn-yaw=0
spawn-pitch=0
```

告示牌支持四行文字、中文、颜色和 JSON 文字样式。地图采用固定照明，每位玩家最多保留 8192 个方块修改位置，断开连接后清除。

### 功能范围

- 地图为静态场景，不执行红石、流体、植物生长、实体 AI 或世界存档。
- 地图导入支持 Alpha `.schematic`；Sponge `.schem`、完整世界存档、箱子内容和自定义头颅不在支持范围内。
- 合成采用 1.8 基础配方，支持背包内 2×2 合成；不提供方块容器界面、工具耐久、多格方块放置和掉落物实体。
- 中键选取支持 1.8 和 1.21.4–26.3；1.9–1.21.3 的选取物品同步尚不完整。
- TAB 头像使用代理提供的皮肤资料。离线直连不查询皮肤，旧版原版客户端在未加密连接中会隐藏头像。

## 配置参考

以下为配置项缺省值；实际部署以 `limbo.properties` 中的设置为准。

| 配置项 | 缺省值 | 说明 |
| --- | --- | --- |
| `host` | `0.0.0.0` | 监听地址 |
| `port` | `25565` | 监听端口 |
| `motd` | `Limbo` | 服务器列表描述，支持中文 |
| `server-brand` | `Limbo` | 自定义 Server Brand，最多 256 字符 |
| `max-players` | `1000` | 同时在线人数上限 |
| `keepalive-seconds` | `10` | 保活间隔，单位为秒 |
| `timeout-seconds` | `30` | 连接超时，须大于保活间隔 |
| `view-distance` | `2` | 出生点周围区块半径，范围为 2–8；同时加载地图占用的区块 |
| `proxy-mode` | `none` | `none`、`bungee` 或 `velocity` |
| `proxy-trusted-addresses` | `127.0.0.1,::1` | 可信代理 IP 或 CIDR 列表 |
| `forwarding-secret-file` | `forwarding.secret` | Velocity 转发密钥文件 |
| `map-file` | 空 | 地图文件路径，空值使用内置小屋 |
| `map-origin-x` / `y` / `z` | `0 / 0 / 0` | 导入地图的原点坐标 |
| `spawn-x` / `y` / `z` | `0.5 / 64 / 0.5` | 出生坐标 |
| `spawn-yaw` / `pitch` | `0 / 0` | 出生朝向，单位为度 |

## 源码构建

项目使用 Gradle Wrapper，构建环境需要 JDK 21 或更高版本。

**Windows**

```powershell
.\gradlew.bat build installDist
```

**Linux / macOS**

```sh
./gradlew build installDist
```

| 构建产物 | 路径 |
| --- | --- |
| ZIP 分发包 | `build/distributions/limbo-1.0-SNAPSHOT.zip` |
| TAR 分发包 | `build/distributions/limbo-1.0-SNAPSHOT.tar` |
| 可直接启动的目录 | `build/install/limbo/` |

`build` 包含自动测试，覆盖协议登录、代理转发、地图、背包交互和皮肤同步。单独执行测试可使用 `./gradlew test`，Windows 使用 `.\gradlew.bat test`。独立客户端测试脚本位于 `tools/protocol-smoke/`。

构建依赖为 ViaVersion 5.12.0。使用本地依赖时，可通过 `-PviaJar=libs/ViaVersion-5.12.0.jar` 指定对应版本的官方 JAR。

## 开源协议

本项目采用 GNU General Public License v3.0 或更新版本（`GPL-3.0-or-later`）授权，详见 [LICENSE](LICENSE)。第三方组件及资源遵循各自的许可证。

## 第三方组件

项目基于 Netty 和 ViaVersion，最初参考 [LOOHP/Limbo](https://github.com/LOOHP/Limbo)。第三方组件来源与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，许可证文本位于 `licenses/`。
