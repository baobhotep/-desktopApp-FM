# Pakowanie aplikacji desktop (F6.1)

## SBT Native Packager

Projekt używa **sbt-native-packager** (JavaAppPackaging). Aplikacja desktop jest pakowana jako dystrybucja Universal (skrypty startowe + lib/).

### Budowanie dystrybucji (stage)

```bash
sbt "desktop/stage"
```

Katalog wynikowy: `desktop/target/universal/stage/`

- `bin/fm-game-desktop` (Linux/macOS) – skrypt uruchamiający
- `bin/fm-game-desktop.bat` (Windows)
- `lib/` – JAR-y zależności

### Uruchomienie ze stage

```bash
cd desktop/target/universal/stage
./bin/fm-game-desktop
```

Na Windows: `bin\fm-game-desktop.bat`

Baza H2 i plik sesji („zapamiętaj mnie”) trafiają do katalogu użytkownika (`~/.local/share/FMGame` lub `%APPDATA%\FMGame`), chyba że ustawisz `GAME_DATA_DIR` / `DATABASE_URL`.

### Instalator (jpackage, JDK 14+)

Aby zbudować instalator (.msi na Windows, .dmg na macOS), po `stage` użyj **jpackage** (dostarczanego z JDK):

```bash
sbt "desktop/stage"
cd desktop/target/universal/stage
jpackage --name "FM Game" --app-version 1.0 --input . --main-jar lib/fmgame.desktop.<wersja>.jar --main-class fmgame.desktop.DesktopLauncher --dest ../../packages
```

Dokładna nazwa JAR-a zależy od wersji w `build.sbt` (np. `fm-game-desktop_3-0.1.0-SNAPSHOT.jar` w `lib/`). Dla jednego JAR-a z wszystkimi zależnościami można wcześniej zbudować fat JAR (np. przez sbt-assembly) i przekazać go do `--main-jar`.

### Uruchomienie w trybie deweloperskim

```bash
sbt "desktop/run"
```

Wymaga `-XstartOnFirstThread` (już ustawione w build) pod macOS/LWJGL.
