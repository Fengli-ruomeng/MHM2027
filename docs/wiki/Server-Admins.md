# Server Admins

## Config

Balance settings are per world, in `<world>/serverconfig/mhm2027-server.toml`. In singleplayer you can also change them in-game from the mod list's config screen.

| Section | Key | Default | What it does |
| --- | --- | --- | --- |
| memory | `gcRateFullSaturation` | 0.02 | Share of RAM that garbage collection clears per second at full saturation |
| memory | `gcRateZeroSaturation` | 0.005 | The same, at zero saturation |
| memory | `gcMinFoodLevel` | 6 | Garbage collection stops at or below this food level |
| heat | `heatCoolingPerSecond` | 2 | Heat lost per second |
| runtime | `instructionsPerTops` | 20 | Lua instructions per tick per reserved TOPS. Lower it to save server time |
| players | `playerTargets` | true | Whether players can scan and hack other players |
| defence | `denyBufferMb` | 256 | Buffer a `deny()` costs |
| defence | `endSessionBufferMb` | 512 | Buffer that closing a session costs |
| mobs | `wiredMobs` | true | Whether wired zombies and skeletons spawn |
| mobs | `wiredStartDay` | 2 | First day they appear |
| mobs | `wiredFullDay` | 30 | Day their chance reaches its maximum |
| mobs | `wiredMaxChance` | 0.25 | That maximum chance |
| mobs | `randomMobKernels` | true | Whether wired mobs roll random kernels (off: all Baseline) |
| station | `kernelCooldownSeconds` | 60 | Wait between kernel installs |
| overload | `presetReach` | 6 | Levels above the gateway an Overload preset may reach |
| overload | `scriptReach` | 2 | The same for players' own Overload scripts |
| overload | `leakShare` | 0.1 | Share of an Overload program's RAM leaked per second |
| overload | `lockSeconds` | 60 | Overload socket lock after overheating |
| gear | `empRadius` | 8 | EMP Activator radius |
| gear | `empMobStunSeconds` | 3 | How long an EMP stuns gateway mobs |
| potions | `bufferRestoreShares` | 0.1, 0.2, 0.35, 0.5, 0.75 | Share of max Buffer restored by Cyber Potions I–V |

**Testing tip**: `wiredStartDay = 1`, `wiredFullDay = 1` and `wiredMaxChance = 1.0` make every new zombie and skeleton wired.

## Commands

`/edge` is for operators (permission level 2). Players use the panel instead.

| Command | Does |
| --- | --- |
| `/edge` or `/edge status [player]` | A gateway's level, resources, kernel, sockets and flaws |
| `/edge scripts` | Your storage |
| `/edge run <script>`, `/edge stop <program>`, `/edge ps` | Start, stop and list your programs |
| `/edge log` | Your terminal log |
| `/edge kernel`, `/edge kernel install <script>` | Show or install your kernel. This skips the Flash Station and cooldown checks |
| `/edge flaws` | Your flaws, including near misses |
| `/edge reboot` | Reboot your terminal |
| `/edge set level\|buffer\|leak\|heat <targets> <value>` | Change a gateway. Works on mobs too; `level` gives a mob a gateway |
| `/edge mobkernel <targets> <schedule> <gc> <verify> <rate_limit>` | Set a mob's kernel |
| `/edge wire <targets> [level]` | Turn zombies and skeletons into wired mobs, at their natural level or a given one |

## Storage

Each player's scripts live in `<world>/edge/players/<uuid>/`, one `.lua` file each. The example scripts and README are added once per player; delete one and it won't come back.

## Datapacks

### Presets: `data/<namespace>/edge_presets/<name>.lua`

A preset is an ordinary script with two extra header fields:

```lua
program {
  name = "Adrenaline Shot", type = "resident", tops = 3,
  level = 0,                 -- the gateway level needed; the code runs at level + 1
  description = "A speed burst whenever one hit takes 3 or more health",
}
```

- Kernels work the same, with a `kernel` header.
- For an **Overload** preset (`overload = true`), `level` is the level its code runs at, and gateways up to 6 levels below may run it.
- A preset whose code reaches past its level is rejected at load, with the reason in the server log.

To give one out: `/give @p mhm2027:program_chip[mhm2027:preset="<namespace>:<name>"]`. Use `kernel_chip` for kernel presets.

### Flaw rules: `data/<namespace>/edge_flaws/<class>.json`

Each file is one flaw class and its rules. If several rules match, the highest severity wins:

```json
{
  "id": "edge:intake",
  "rules": [
    { "when": { "setting": "rate_limit", "at_least": 8, "near": 2 }, "severity": "minor", "cause": "rate_limit {rate_limit}" },
    { "when": { "setting": "rate_limit", "at_least": 12, "near": 2 }, "severity": "major", "cause": "rate_limit {rate_limit}" }
  ]
}
```

- A rule reads a kernel **setting** (`verify`, `gc`, `schedule`, `rate_limit`) or a live **state** (`heat`, `programs`, `enhancements`, `hooks_spare`), with `equals`, `at_least` or `at_most`. `all` combines conditions.
- `near` makes the panel warn players when they're close.
- Severities are `minor`, `major` and `critical`.
- Classes with `"dynamic": true` are checked a few times a second; the rest only when a kernel is installed.

Players and mobs use the same rules.
