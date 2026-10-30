# Progression

You level your gateway up by **flashing firmware** at a **Flash Station**. Each firmware needs materials from a stage of the normal game, plus **Data Shards** from gateway mobs and some XP.

## The Flash Station

Crafted from:

```
C R C      C = copper ingot
C B C      R = redstone
S S S      B = Blank Data Chip, S = smooth stone
```

Mine it with a pickaxe. It has a firmware slot, three Data Shard slots (enough for LV10's 128 shards), a **Flash** button, and a status line that tells you what's missing.

The station is also where you **change sockets** and **install kernels**, within 6 blocks.

## Firmware

Each firmware is a shapeless recipe: a **Blank Data Chip** plus the materials below. Firmware goes **one level at a time**: Firmware LV3 needs an LV2 gateway.

| Firmware | Materials (plus a Blank Data Chip) | Data Shards | XP levels |
| --- | --- | --- | --- |
| LV1 | 4 copper ingots, 2 redstone | 4 | 5 |
| LV2 | 4 iron ingots, 4 redstone | 8 | 8 |
| LV3 | 4 gold ingots, 4 lapis lazuli | 12 | 10 |
| LV4 | 2 diamonds, 4 amethyst shards | 16 | 15 |
| LV5 | 4 blaze powder, 4 Nether quartz | 24 | 20 |
| LV6 | 2 ghast tears, 4 crying obsidian | 32 | 25 |
| LV7 | 2 netherite scrap | 48 | 30 |
| LV8 | 4 echo shards, 2 sculk | 64 | 35 |
| LV9 | 4 shulker shells, 4 chorus fruit | 96 | 40 |
| LV10 | 1 Nether star, 1 dragon's breath | 128 | 50 |

Shards and XP are paid at the station, not in the recipe. Data Shards drop from [wired mobs](Wired-Mobs).

## Flashing

Put the firmware and the shards in the station and press **Flash**. A flash takes **10 seconds**, and for those 10 seconds you're defenceless:

- every program stops, and your kernel is down, so it can't deny or resist;
- your Buffer shows **0**;
- you can still be hacked, faster than usual.

Stay within **6 blocks** of the station. The cost is only taken when the flash **completes**. If you walk away, die, log out or the station is broken, the flash is cancelled and nothing is used up.

Afterwards your Buffer comes back to what it was; flashing doesn't refill it. Your kernel reloads at the new level, and new sockets appear empty.

So flash somewhere safe.

## The Null route

If you want none of the cyber layer, flash **Null Firmware**: a Blank Data Chip plus flint, no shards, 1 XP level.

A **Null** gateway (LV-1):

- is **immune to all hacking**, at every stage;
- can't run programs or use cyber gear;
- loses everything written into it: presets, sockets and kernel are wiped.

Gateway mobs still get their physical upgrades, so the game stays harder even for Null players.

To leave Null, flash **Restore Firmware**: a Blank Data Chip plus redstone and a copper ingot, 5 XP levels. It puts you back at **LV0** with a fresh Buffer, never at your old level. That way nobody can switch to immunity in the middle of a fight and switch back afterwards.
