# Glimmer (Fabric, Minecraft 26.2)

Client-side cosmetic effects. No cheats: it only spawns particles locally and never touches
packets, combat, movement, hitboxes or reach.

Run `/glimmer` in game for the animated settings menu. Settings save to `config/glimmer-v4.json`.
`/glimmer dump` writes `glimmer-dump.txt` (class names of the game's renderer) for development.

## Build
Needs JDK 25 and Gradle 9.3+:

    gradle build
