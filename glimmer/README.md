# Glimmer (Fabric, Minecraft 26.2)

Client-side cosmetic effects. No cheats: it only spawns particles locally and never touches
packets, combat, movement, hitboxes or reach.

Layers: aura, orbiting sparks, movement trail, swing trail (weapons, hand and block swings),
hit burst, footstep rings, weapon glow. Run `/glimmer` in game to open the animated settings menu.
Settings save to `config/glimmer-v3.json`.

ScaleMe is supported: the swing trail follows ScaleMe's swing speed, arc size, item scale and
"disable swing" option.

## Build
Needs JDK 25 and Gradle 9.3+:

    gradle build
