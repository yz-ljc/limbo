# Third-party notices

The server integrates ViaVersion 5.12.0, licensed under GNU GPL version 3 or later.

- Source and release: https://github.com/ViaVersion/ViaVersion/tree/5.12.0
- License text: [licenses/ViaVersion-LICENSE.txt](licenses/ViaVersion-LICENSE.txt)
- Distribution of software integrating ViaVersion must comply with its GPL terms, including corresponding source obligations. The upstream platform guide documents this requirement: https://github.com/ViaVersion/ViaVersion/wiki/Creating-a-new-ViaVersion-platform

Other direct runtime dependencies:
- Netty 4.1.118.Final — Apache-2.0 — https://github.com/netty/netty
- Gson 2.13.2 — Apache-2.0 — https://github.com/google/gson
- Guava 33.4.8-jre — Apache-2.0 — https://github.com/google/guava

`src/main/resources/world/blocks-1.8.json` and `crafting-1.8.json` are subsets of the
block, collision-shape, item and recipe data distributed with PrismarineJS
`minecraft-data@3.117.0`, using the 1.8.8 dataset.
Upstream declares this dataset MIT: https://github.com/PrismarineJS/minecraft-data#license.
Copyright PrismarineJS contributors. License text: [licenses/minecraft-data-LICENSE.txt](licenses/minecraft-data-LICENSE.txt).
The data is used for inventory crafting, local block collision and
breakability checks; neighbor-dependent legacy shapes remain approximations as
documented upstream: https://github.com/PrismarineJS/minecraft-data/blob/master/doc/blockCollisionShapes.md.
The bundled block subset corrects the two huge mushroom blocks (99/100) from
zero hardness to 0.2 to preserve survival mining behavior.

Their JARs retain upstream notices. The full ViaVersion release JAR, when selected with `-PviaJar`, also includes upstream bundled dependency notices.

Development-only dependencies:
- JUnit Jupiter — EPL-2.0
- PrismarineJS minecraft-protocol 1.68.0 — MIT — https://github.com/PrismarineJS/node-minecraft-protocol

The original project credits LOOHP/Limbo: https://github.com/LOOHP/Limbo.
