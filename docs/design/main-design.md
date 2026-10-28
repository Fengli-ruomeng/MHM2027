<!-- Exported from the Claude Doc 'MoreHarderMinecraft: Edge - Design Draft v2'. The Claude Doc is the source of truth. -->

# MoreHarderMinecraft: Edge — Design Draft v2

·

**Cyber**** system** (kernel, programs, mobs, scanning) has its own tab, which overrides this tab's Null Gateway, Programs, Script language and Cyber combat sections: Scripting system

## How to read this draft

v2 keeps every system from v1 and fixes the parts that contradicted each other or had no rules yet. Nothing was cut; where v1 was silent, v2 makes a concrete call you can overrule.

- **[Decision]** marks a call made where v1 said nothing. Change any of them freely.
- **[Changed]** marks a rule rewritten because the v1 wording conflicted with itself.
- All numbers are starting values for playtesting, not final balance.

| Area | v1 | v2 |
| --- | --- | --- |
| RAM | Occupied by scripts and also regenerates; unclear how it reaches zero | A capacity that programs occupy, plus Leak that attacks add; Wither when Leak overflows it |
| Levelling | No way to raise the gateway level | Firmware flashed at a station, gated by vanilla progression |
| Tier curve | 1 GB jumps to 4 GB at LV7 | About 1.5x RAM per level |
| Overload | "High risk", risk undefined | Heat and Leak costs, at most 6 levels above yours |
| Combat | Attack and defence described loosely | Five-stage intrusion with a counter at each stage |
| Null Gateway | No rules for getting it | A permanent route choice with a real cost |

## Design pillars

Every system in Edge must pass four tests; a feature that fails one is cut. This is the rule that stops the bloat of MHM 2025/2026 from coming back.

1. **Vanilla first.** Cyber effects work through vanilla systems: attributes, status effects, hunger, XP, crafting. No second health bar, no mana.
2. **One resource set.** Every mechanic spends RAM, TOPS or Buffer. A feature that needs a fourth resource gets redesigned.
3. **Presets finish the game; code raises the ceiling.** A player who never writes a line can still beat Edge. Scripting makes you stronger and more flexible.
4. **Harder, not longer.** Difficulty comes from smarter enemies and resource pressure, not bigger health pools or grinding.

**Backdrop (optional).** Every living thing now carries a gateway, a bridge between its body and a network no one controls. The mobs that learned to use theirs are the new threat.

## Core resources

Edge has three resources and two kinds of slot. Each one now has a single job, with clear rules for what fills it, what drains it, and what happens at zero.

### RAM: capacity **[Changed]**

RAM is a fixed capacity set by gateway level. Programs occupy RAM while they run and free it when they stop.

- **Leak** is a second amount that also fills RAM. Enemy payloads, crashes and Overload programs add Leak.
- **Garbage collection (GC)** clears Leak over time, at a rate set by hunger saturation. This keeps the v1 link between RAM and food.
- **Free RAM = capacity − running programs − Leak.** A program can't start without enough free RAM.
- **Out of memory:** when Leak pushes free RAM below zero, the player has Wither until it is positive again. Each second, the newest program is also force-stopped until free RAM is back to zero or above.
- Starting GC rates: 2% of capacity per second at full saturation, 0.5% at zero saturation, none while hunger is 6 or lower.

### TOPS: throughput

TOPS is how much computation you can run each tick. Each program reserves a fixed TOPS budget while it runs.

- Freed TOPS comes back over 3 seconds, as in v1.
- **At least 1 TOPS must stay free.** At 0 free TOPS every program crashes (a kernel panic). Each crashed program adds Leak equal to 25% of its RAM, and nothing can start for 5 seconds.
- Spare TOPS powers offence (see Cyber combat).
- Under the hood, 1 TOPS = 10 script instructions per tick. A script that tries to exceed its budget is slowed, not crashed.

### Buffer: cyber shield

Buffer is the cyber health bar; intrusion pressure hits it first.

- Units: Buffer is in GB, attack pressure in MB per second. 1 GB absorbs 1,024 MB.
- **[Decision]** Buffer never regenerates by itself. Cyber Potions refill it and Yate armour raises the maximum. This is the main difficulty lever in cyber combat.
- At 0 Buffer the attacker still has to beat any running defensive scripts. With none running, the breach succeeds.

### Slots

- **Standard slots** hold anything that changes the body or binds an ability: a Universal Adapter running a script, or a preset.
- **[Decision]** Scripts that only read data or run logic need no slot, only RAM and TOPS.
- **Overload slots** hold Overload presets only (see Programs).

## Gateway tiers

RAM now grows about 1.5x every level instead of jumping between bands, so no level feels empty. TOPS and slots are unchanged from v1.

| Level | Band | RAM | TOPS | Buffer | Standard slots | Overload slots |
| --- | --- | --- | --- | --- | --- | --- |
| LV-1 | Null | 0 | 0 | 0 | 0 | 0 |
| LV0 | Default | 128 MB | 25 | 1 GB | 1 | 0 |
| LV1 | Entry | 256 MB | 45 | 3 GB | 2 | 0 |
| LV2 | Entry | 384 MB | 60 | 5 GB | 2 | 0 |
| LV3 | Entry | 512 MB | 85 | 8 GB | 2 | 0 |
| LV4 | Mid | 768 MB | 100 | 10 GB | 4 | 1 |
| LV5 | Mid | 1 GB | 120 | 12 GB | 4 | 1 |
| LV6 | Mid | 1.5 GB | 135 | 15 GB | 4 | 1 |
| LV7 | Advanced | 2.5 GB | 155 | 20 GB | 4 | 1 |
| LV8 | Advanced | 4 GB | 175 | 30 GB | 4 | 1 |
| LV9 | Advanced | 7 GB | 195 | 50 GB | 4 | 1 |
| LV10 | Apex | 12 GB | 300 | 100 GB | 8 | 2 |
| LV11 | Creative | 1 TB | 10,000 | 10 TB | 16 | 8 |

**[Changed]** RAM is set per level, not per band. LV5 Buffer rises from 10 to 12 GB so every level gains something. LV10 RAM drops from 15 to 12 GB to stay on the curve.

## Progression

**[Decision]** The gateway levels up by flashing firmware at a new block, the **Flash Station**. Each firmware uses materials gated by vanilla progress, so your gateway level follows how far you are in the normal game.

- Flashing costs the firmware item plus XP levels.
- It takes 10 seconds. During the flash your Buffer is 0 and every program stops, so flash somewhere safe.
- Every firmware needs **Data Shards**, dropped by gateway mobs. Cyber combat is how you progress.
- **[Decision]** Gateway level is kept on death. Presets in slots drop with your items.

| Target | Vanilla gate | Key materials | Data Shards | XP levels |
| --- | --- | --- | --- | --- |
| LV1 | Early game | Copper, redstone | 4 | 5 |
| LV2 | Iron tools | Iron, redstone | 8 | 8 |
| LV3 | Villages, gold | Gold, lapis | 12 | 10 |
| LV4 | Diamonds | Diamond, amethyst | 16 | 15 |
| LV5 | Nether entered | Blaze powder, quartz | 24 | 20 |
| LV6 | Fortress or bastion | Ghast tear, crying obsidian | 32 | 25 |
| LV7 | Netherite | Netherite scrap | 48 | 30 |
| LV8 | Ancient City | Echo shard, sculk | 64 | 35 |
| LV9 | The End | Shulker shell, chorus fruit | 96 | 40 |
| LV10 | Both bosses | Nether star, dragon's breath | 128 | 50 |

### Null Gateway (LV-1) **[Decision]**

Null is a permanent route for players who want no cyber layer at all.

- Null Firmware is cheap and can be flashed from day one. Flashing it wipes every program and slot.
- While Null, you are immune to all hacking and can't use any cyber gear or programs.
- Leaving Null resets you to LV0. That cost stops PvP players from switching to immunity mid-fight.
- Gateway mobs also get physical upgrades, so Null players still face a harder game.

## Programs

Three program types share one runtime. They differ in who wrote them and how far above your level they can reach.

| Type | Comes from | Highest API level | Slot | Cost |
| --- | --- | --- | --- | --- |
| Script | You, in the in-game editor | Your gateway level | Only if it changes the body or an ability | RAM and TOPS, shown in the editor |
| Preset | Loot, or compiled at a Flash Station | Your level + 1 | Standard | Fixed RAM and TOPS, shown on the item |
| Overload preset | Rare loot, compiled at LV4+ | Your level + 6 | Overload | RAM, TOPS, Heat and Leak |

### Overload rules **[Changed]**

v1 called Overload high-risk without saying what the risk was. v2 gives it two costs.

- **Heat:** the Overload slot gains Heat each second the preset runs, equal to 2 + the level gap. At 100 Heat the preset crashes, adds Leak equal to its RAM, and the slot locks for 60 seconds.
- **Leak:** an Overload preset adds Leak every second it runs, so saturation and GC matter.
- Heat cools once the preset stops; Hasik coolant tools speed this up.
- Example: an LV4 player running an LV10 preset gains 8 Heat a second and overheats after about 12 seconds. Overload is a burst, not a permanent state.

### Sharing presets **[Decision]**

You can compile your own script into a preset at a Flash Station. It keeps your level as its requirement, so a friend one level below you can still run it. This gives multiplayer groups a reason to trade code.

## Script language and APIs

**[Decision]** EdgeScript (working name) is a small event-driven language. It runs only on the server, in a sandbox with no access to the game's own code.

```
program "Adrenaline"
on health_lost(amount) when amount >= 4 {
  move.speed_boost(0.3, 3s)
}
```

- Scripts react to events (health lost, hit taken, intrusion detected, a timer) instead of looping. This keeps server cost low.
- The editor shows a script's RAM and TOPS cost before you run it.
- Every API call checks your gateway level. Calling above it returns a privilege error, as in v1.
- Reading your own stats and events works at every level; changing anything needs the level shown in the table.

| Level | Unlocks |
| --- | --- |
| LV0 | Terminal, diagnostics, read your own stats and events |
| LV1 | Movement speed |
| LV2 | Jump height, step-up, fall damage; basic firewall |
| LV3 | Hunger, saturation, natural regeneration |
| LV4 | Network scan within 16 blocks; first offensive payloads |
| LV5 | Max health; advanced firewall that reveals the attacker |
| LV6 | Attack damage, attack speed, knockback |
| LV7 | Read a breached target's stats and programs |
| LV8 | Debuff a breached target (slowness, weakness, blindness) |
| LV9 | Stop a breached target's programs; plant Leak |
| LV10 | Seize terminal: briefly control a breached mob's targeting |
| LV11 | Privilege escalation (Creative only) |

## Cyber combat

An intrusion runs in five stages, and the defender has a counter at every one, so no hack is instant or unanswerable.

*[Diagram: intrusion flow · 5 stages, a counter at each]*

Moving out of sight ends any intrusion before the Buffer runs out; after that, prepared defences decide the fight.

### Pressure formula

```latex
\text{Drain (MB/s)} = P \times \left(1 + \frac{T}{100}\right) - F
```

P is the payload's strength, T the attacker's free TOPS, and F the defender's firewall rating. Example: an LV4 attacker with a strength-50 payload and 60 free TOPS drains 80 MB/s, so an LV1 defender with 3 GB of Buffer and no firewall lasts about 38 seconds.

### Limits **[Decision]**

- A breach never kills directly. In PvP, debuffs top out at level II and last at most 10 seconds per exploit window.
- After a breach the target is immune for 20 seconds, so nobody can be chain-breached.
- Null Gateway players are immune at every stage. Even LV11 brute-force escalation, which bypasses firewalls, can't reach them.

## Gateway mobs and hardware

**[Decision]** Gateway mobs are upgraded versions of vanilla mobs, not a new creature roster, so the world stays recognisable. They drop Data Shards and can be hacked back with the same intrusion rules.

- They start appearing on day 2. By day 30, about 1 in 4 hostile mobs carries a gateway (starting value).
- Their level depends on depth and dimension: low on the surface, higher underground, in the Nether and in the End.
- Each one also has a physical upgrade, so they stay dangerous to Null players.

| Mob (working name) | Level | Where | Threat |
| --- | --- | --- | --- |
| Wired Zombie | LV1–3 | Overworld, night | Speed bursts when hurt |
| Wired Skeleton | LV3–4 | Overworld, caves | Scans you, then slows you on hit |
| Netrunner (illager) | LV5–7 | Raids, mansions | Drains your Buffer from range |
| Wired Brute (piglin) | LV7–8 | Bastions | Firewall that has to be broken before melee damage lands |
| Echo Warden variant | LV8–9 | Ancient Cities | Plants Leak, forcing out-of-memory |

### Hardware

Every cyber item has a minimum gateway level to use.

| Class | Role | First items |
| --- | --- | --- |
| Alontesi | Offence | Injector: launches a payload up to 32 blocks and skips the scan step. Spike blade: melee hits add pressure. |
| Yate | Protection | Armour that raises max Buffer and firewall rating. Swords and maces that add firewall rating while held. |
| Hasik | Utility | Scanner, signal jammer block (stops locks in a radius), coolant for Heat, Universal Adapters. |

## Difficulty layer

**[Decision]** v1 promises a full difficulty overhaul but lists no changes, so v2 ties difficulty to the cyber layer instead of adding unrelated tweaks.

- **Smarter enemies:** gateway mobs are the main source of extra difficulty.
- **Food is a combat stat:** GC runs on saturation, so poor food means slower Leak recovery mid-fight.
- **Scarce Buffer:** Cyber Potions are the only refill. A weak version is crafted from a glass bottle, redstone and 2 Data Shards; stronger versions are brewed after the Nether.
- **Risky upgrades:** flashing firmware leaves you defenceless for 10 seconds.

Any other vanilla tweak (mob health, villager prices, and so on) goes on a separate list and must pass the four pillar tests before it is added.

## Open decisions

These are the calls v2 couldn't make for you. Tick each one once it's settled.

- [ ] Do slotted presets drop on death, or stay installed?
- [ ] Is PvP hacking on by default, or a server game rule?
- [ ] Is leaving Null a full reset to LV0, or is one free switch back allowed?
- [ ] Is the TOPS jump from 195 (LV9) to 300 (LV10) the intended apex spike?
- [ ] How many gateway mobs ship in the first release?
- [ ] Final name for the script language.

## Build order

The mod is playable after step 2; each later step adds one system on top.

1. Gateway data, HUD, terminal diagnostics, Flash Station for LV0 to LV3.
2. Slots and 4 hardcoded presets; RAM, Leak and GC; TOPS and kernel panic; out-of-memory Wither.
3. EdgeScript interpreter and editor, with the LV0 to LV3 APIs.
4. Intrusion against 2 gateway mobs; Data Shards; Cyber Potions.
5. Overload, the first Alontesi, Yate and Hasik items, firmware up to LV10.
6. PvP hacking, Null Gateway, the rest of the mob roster.
