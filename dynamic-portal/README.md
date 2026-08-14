# DynamicPortal

A Paper 1.21.x plugin that creates a special **Gold Block** portal (visually and
audibly identical to a Nether portal) which automatically relocates to a new
random, safe location every **3 real-world days** — configurable, protected,
and fully persistent across restarts.

---

## 1. Requirements

- Paper (or a Paper fork) **1.21.x**
- **Java 21**
- Maven 3.6+ (to build from source)

---

## 2. Building the plugin

```bash
cd dynamic-portal
mvn clean package
```

The compiled jar will be at:

```
target/DynamicPortal-1.0.0.jar
```

> **Note on this delivery:** this sandbox environment has no network access
> and no Maven/JDK compiler installed, so `mvn clean package` could not be
> executed here to produce a `.jar`. The full source tree below is complete
> and was carefully hand-reviewed for correctness against the Paper 1.21 API.
> Run `mvn clean package` on your own machine (with internet access, since it
> needs to download `paper-api` from `repo.papermc.io`) to produce the final jar.

---

## 3. Installation

1. Build the jar (see above), or obtain `DynamicPortal-1.0.0.jar`.
2. Drop it into your server's `plugins/` folder.
3. Start (or restart) the server. This generates:
   - `plugins/DynamicPortal/config.yml`
   - `plugins/DynamicPortal/portal-data.yml`
4. Edit `config.yml` to match your worlds/boundaries (see below), then run
   `/portal reload`, or just restart the server.
5. On first run with no existing portal, the plugin automatically creates one
   at a random safe location in the configured world.

---

## 4. How the portal works

- The **frame** is built from `GOLD_BLOCK` (configurable).
- The **interior** is filled with real `NETHER_PORTAL` blocks, so it has the
  authentic purple swirling effect, particles, and ambient sound of a vanilla
  Nether portal.
- By default, standing in the portal does **not** send the player to the
  Nether — it only shows the configured entry title/message. This is
  intentional: the whole point of this portal is *its own* dynamic location,
  so sending players into an unrelated dimension would defeat the feature.
  If you actually want vanilla Nether/Overworld travel to also trigger, set
  `teleport.enable-vanilla-portal-travel: true` in `config.yml`.
- Every block belonging to the portal (frame **and** interior) is tracked by
  exact coordinates in `portal-data.yml` and is fully protected — see
  section 8.

---

## 5. Configuring random worlds

In `config.yml`:

```yaml
worlds:
  default: world
  allowed:
    - world
    - world_the_end
    - custom_world
  random-world-selection: false
```

- `default` — the world used when `random-world-selection` is `false`, and as
  a fallback if a chosen world isn't loaded.
- `allowed` — the full list of worlds the portal is permitted to appear in.
  Used by `/portal randomworld`, and to validate `/portal move <world>` /
  `/portal setworld <world>`.
- `random-world-selection: true` — each automatic relocation picks a random
  world from `allowed` instead of always using `default`.

Random placement boundaries (per relocation, applied within whichever world
is chosen):

```yaml
location:
  y: auto                     # or a fixed integer Y
  min-x: -5000
  max-x: 5000
  min-z: -5000
  max-z: 5000
  min-distance-from-spawn: 500
```

### Safety checks performed before placement

For every candidate location, the plugin verifies (retrying up to 200 times):

- Horizontal distance from world spawn ≥ `min-distance-from-spawn`.
- The ground block is solid and not lava, water, magma, cactus, or fire.
- The full footprint the frame will occupy is free of any solid blocks,
  water, or lava (so the portal is never generated inside terrain).
- The surrounding terrain height doesn't vary by more than 1 block under the
  frame's footprint (avoids spawning half-buried or on a cliff edge).
- There is open headroom directly above the structure (avoids cramped cave
  ceilings / trapping players inside stone).

If no safe spot is found after all attempts, the relocation is aborted, the
old portal is **not** deleted, and a warning is logged/sent to the command
sender.

---

## 6. How the 3-day relocation system works

- `relocation.interval-days` (default `3`) is stored purely as **real-world
  elapsed time**, using `System.currentTimeMillis()` — not server ticks, and
  not in-game days. It keeps counting even while the server is offline.
- On every portal creation/relocation, the plugin writes a `next-relocation`
  UNIX timestamp to `portal-data.yml`.
- A repeating task checks once per minute whether `now >= next-relocation`.
  If so, it removes the old portal, finds a new safe spot, builds the new
  portal, saves the new timestamp, and broadcasts the "moved" message.
- **On server startup**, if the stored `next-relocation` timestamp has
  already passed (i.e. the server was offline through the deadline), the
  portal relocates immediately once the server finishes starting — it does
  **not** wait for another full 3 days, and it does **not** reset the timer
  just because the server restarted.

Example: portal created **August 13, 2026, 12:00**, interval 3 days →
next relocation is scheduled for **August 16, 2026, 12:00**, regardless of
how many times the server restarts in between.

---

## 7. Commands

| Command | Description |
|---|---|
| `/portal` | Shows the help menu |
| `/portal help` | Shows the help menu |
| `/portal location` | Shows the portal's world, X/Y/Z, and time until next relocation |
| `/portal time` | Shows only the time remaining until the next relocation |
| `/portal teleport` | Teleports the player to just in front of the portal |
| `/portal move` | Immediately relocates the portal to a new random safe spot (respects `worlds.random-world-selection`) |
| `/portal move <world>` | Immediately relocates the portal to a random safe spot in the given world |
| `/portal setworld <world>` | Sets `worlds.default`, used for future random placements |
| `/portal randomworld` | Picks and displays a random world from `worlds.allowed` (does not move the portal) |
| `/portal reload` | Reloads `config.yml` (does **not** reset the relocation timer) |
| `/portal remove` | Removes the current portal |
| `/portal create` | (Re)creates the portal — at its last stored coordinates if one previously existed in that world, otherwise at a new safe location |

`/portal location` example output:

```
Portal Location:
World: survival
X: 1240
Y: 72
Z: -830
Next relocation: 2d 14h 32m
```

---

## 8. Permissions

| Permission | Default | Grants |
|---|---|---|
| `dynamicportal.admin` | `op` | Full access: `move`, `setworld`, `randomworld`, `reload`, `remove`, `create`, plus everything below |
| `dynamicportal.teleport` | `true` | `/portal teleport` (only enforced if `permissions.teleport-requires-permission: true`) |
| `dynamicportal.location` | `true` | `/portal location`, `/portal time` (only enforced if `permissions.location-requires-permission: true`) |
| `dynamicportal.reload` | `op` | `/portal reload` |

By default, `teleport` and `location` are granted to everyone so normal
players can enjoy the portal; flip the two `permissions.*-requires-permission`
flags in `config.yml` to `true` if you'd rather gate those behind explicit
permission grants (e.g. via LuckPerms).

---

## 9. Protection

The plugin tracks the **exact block coordinates** (frame + interior) of the
active portal in `portal-data.yml` and checks every relevant event against
that bounding box — nothing is disabled globally, and blocks anywhere else on
your server are completely unaffected. Protected against:

- Block breaking (`BlockBreakEvent`) — cancelled **even for operators and
  creative-mode players**, since the event fires regardless of game mode/op
  status.
- Block placing inside the portal's space (`BlockPlaceEvent`).
- Explosions, TNT, and creepers (`EntityExplodeEvent`, `BlockExplodeEvent`) —
  portal blocks are stripped out of the explosion's block list so the rest of
  the explosion still happens normally, it just can't touch the portal.
- Pistons pushing or pulling portal blocks (`BlockPistonExtendEvent`,
  `BlockPistonRetractEvent`).
- Fire spreading onto or burning portal blocks (`BlockIgniteEvent`,
  `BlockBurnEvent`).
- Water/lava flowing into the portal's space (`BlockFromToEvent`).
- Block fading/decay events targeting portal blocks (`BlockFadeEvent`).

Each protection type can be toggled independently under `protection:` in
`config.yml`. The only way to remove the portal is `/portal remove` (or an
automatic relocation), both of which go through the plugin's own controlled
removal logic.

---

## 10. Configuration reference (`config.yml`)

```yaml
portal:
  frame-material: GOLD_BLOCK
  width: 4
  height: 5

relocation:
  enabled: true
  interval-days: 3

location:
  y: auto
  min-x: -5000
  max-x: 5000
  min-z: -5000
  max-z: 5000
  min-distance-from-spawn: 500

worlds:
  default: world
  allowed:
    - world
  random-world-selection: false

permissions:
  location-requires-permission: false
  teleport-requires-permission: false

protection:
  prevent-block-break: true
  prevent-block-place: true
  prevent-explosions: true
  prevent-fire: true
  prevent-piston: true
  prevent-liquid-flow: true

teleport:
  enable-vanilla-portal-travel: false
  entry-message-enabled: true
  entry-cooldown-seconds: 5

messages:
  prefix: "&5&lPortal &8» "
  moved: "&dThe portal has moved to a new location!"
  protected: "&cThis portal is protected."
  # ... see the shipped config.yml for the full list, all colorized with '&' codes.

debug: false
```

---

## 11. Persistence (`portal-data.yml`)

Automatically managed by the plugin — you shouldn't need to edit it by hand.

```yaml
exists: true
world: world
x: 1240
y: 72
z: -830
width: 4
height: 5
created-at: 1755000000000
next-relocation: 1755259200000
bounds:
  min-x: 1240
  min-y: 72
  min-z: -830
  max-x: 1243
  max-y: 76
  max-z: -830
```

`created-at` / `next-relocation` are UNIX millisecond timestamps, which is
what makes the 3-day timer immune to server restarts.

---

## 12. Project structure

```
dynamic-portal/
├── pom.xml
├── README.md
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   └── config.yml
    └── java/com/dynamicportal/
        ├── DynamicPortalPlugin.java        # main class
        ├── config/ConfigManager.java       # typed config.yml access
        ├── data/
        │   ├── PortalData.java             # persisted state POJO
        │   └── PortalDataManager.java      # portal-data.yml load/save
        ├── portal/
        │   ├── PortalManager.java          # create/remove/relocate/protect logic
        │   └── SafeLocationFinder.java     # safe random-location search
        ├── commands/PortalCommand.java     # /portal command + tab completion
        ├── listeners/
        │   ├── PortalProtectionListener.java  # break/explode/piston/fire/liquid protection
        │   └── PortalEntryListener.java       # entry message + vanilla-travel blocking
        └── util/
            ├── TimeUtil.java               # duration formatting
            └── MessageUtil.java            # '&' color code translation
```

---

## 13. Design notes

- All world/block mutation (building, removing, safe-location scanning) runs
  **synchronously on the main thread** — Bukkit's world/chunk API is not
  safe to call off-thread, and this only runs on rare events (startup,
  every-3-days relocation, or an admin command), so the brief main-thread
  work is negligible.
- The relocation checker is a single lightweight repeating task (once per
  minute) — no per-tick or per-player polling.
- Protection checks are simple bounding-box integer comparisons against the
  single active portal, so they're cheap even on busy servers.
- Colors use standard `&`-code legacy formatting for simplicity and broad
  compatibility; the code is structured so MiniMessage/Adventure `Component`
  output could be swapped in later without touching the rest of the plugin.
