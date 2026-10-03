# Glimmer (Fabric, Minecraft 26.2)

Client-side cosmetic effects. No cheats: it only spawns vanilla particles locally and never touches
packets, combat, movement, hitboxes or reach. Safe to use on servers like Hypixel in principle, but
check the server's own mod rules if you're unsure.

Layers: aura, orbiting sparks, movement trail, hit burst. Everything is adjustable live with `/glimmer`
(run it alone for the command list) and saved to `config/glimmer.json`.

## Build
Needs JDK 25 and Gradle 9.3+:

    gradle build

Jar ends up in `build/libs/glimmer-1.0.0+26.2.jar`. Or push to GitHub and download the jar from the
Actions tab (workflow included).
