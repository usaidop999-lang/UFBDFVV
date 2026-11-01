Friend Shield 26.2 - Fabric client mod (source). Original code, written from scratch.

Features
- Protected-player list: attacks on listed players are blocked (client-side).
- Compass in the action bar: arrow + name + distance of the nearest unprotected player,
  "+N more" for others, green "Friends: N" for protected players nearby.
- Keys (change in Options > Controls > Key Binds > Friend Shield):
  H = protect/unprotect the player you are looking at
  R = compass on/off
  G = cycle range 16/32/64/128/256
- Languages: English, Hindi. Settings: config/friendshield.properties

Build: use the GitHub Actions workflow (Actions > Build JAR) or run build.bat on Windows
(JDK 25 + Gradle 9.5.1). JAR: build/libs/friendshield-26.2-1.0.0.jar
Put the JAR in .minecraft/mods together with Fabric API 0.161.0+26.2.
Not compiled/tested by the author of this source; if the build errors, send the error text.
