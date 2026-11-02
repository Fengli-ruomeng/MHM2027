# Why Level Up?

*Beginner's Guide, page 5 of 13.*

At LV0 your programs can watch, warn and hack. What they **can't** do is change your body: no speed, no jumping, no healing. Those powers unlock level by level, and so does nearly everything else.

## What each level gives you

| Level | New powers for your programs | Sockets | TOPS | Buffer (denies) | Kernel hooks |
| --- | --- | --- | --- | --- | --- |
| LV0 | Watch, warn, scan, inspect, hack | 1 | 25 | 1 GB (4) | 2 |
| LV1 | `me.speed_boost`: run faster | 2 | 45 | 3 GB (12) | 2 |
| LV2 | `me.jump_boost`, `me.fall_guard`: jump higher, no fall damage | 2 | 60 | 5 GB (20) | 2 |
| LV3 | `me.regen_burst`: heal on demand | 2 | 85 | 8 GB (32) | 3 |
| LV4 | Overload sockets: run code *above* your level | 4 + 1 | 100 | 10 GB (40) | 3 |
| LV5 | `me.max_health_boost`: extra hearts | 4 + 1 | 120 | 12 GB (48) | 3 |
| LV6 | `me.damage_boost`, `me.knockback_resist`: hit harder, stand firm | 4 + 1 | 135 | 15 GB (60) | 4 |
| LV10 | The top: 300 TOPS, 100 GB of Buffer | 8 + 2 | 300 | 100 GB (400) | 4 |

Every level also adds a block to how far you can scan (16 + level) and hack (12 + level). The full table is in [Gateway and Resources](Gateway-and-Resources#levels).

## Three programs you can't run yet

Each of these is complete. Write them now, and the editor tells you exactly what's missing ("needs gateway LV2"); they'll install the moment you get there.

### Mountain Goat (LV2)

Higher jumps and no fall damage, all the time.

```lua
-- Needs LV2 and a socket.
program { name = "Goat", type = "resident", tops = 2 }

function on_start()
  sys.every(1)
  on_timer()
end

function on_timer()
  -- Each lasts 10 s and cools down for 8 or 10 s. On cooldown a call
  -- just returns false, so trying every second keeps both up for good.
  me.jump_boost(0.3, 10)
  me.fall_guard(10)
end
```

### Combat Medic (LV3)

Below 4 hearts, heal 3 of them back, paid in hunger. If healing is still cooling down, run instead.

```lua
-- Needs LV3 and a socket.
program { name = "Medic", type = "resident", tops = 3 }

function on_start()
  sys.low_health(8)        -- on_low_health fires below 8 health (4 hearts)
end

function on_low_health(hp)
  if me.regen_burst(6) then
    sys.notify("Patched up: +6 health")
  elseif me.speed_boost(0.4, 4) then
    sys.notify("Can't heal yet. Run!")
  end
end
```

Powers return `true` when they work and `false` when they're cooling down, so a program can try a plan B.

### Juggernaut (LV6)

A last stand: when you drop below 30%, five extra hearts, no knockback and harder hits.

```lua
-- Needs LV6 and a socket.
program { name = "Juggernaut", type = "resident", tops = 4 }

function on_low_health(hp)
  me.max_health_boost(10, 20)    -- 10 extra health (5 hearts) for 20 s
  me.knockback_resist(10)
  me.damage_boost(0.4, 10)       -- 40% harder hits for 10 s
  sys.notify("Last stand")
end
```

## Sockets: how many powers at once

Programs that change your body run only from a **socket**, and you only have so many: one at LV0, two from LV1, four from LV4. Mountain Goat and Combat Medic together need two. You choose what goes in them at a Flash Station (`socket add Medic`), so before a fight you're choosing your loadout. See [Sockets and Overload](Sockets-and-Overload).

## TOPS: how much at once

Every running program books TOPS, and so does your kernel (3 for Baseline). A realistic kit:

| Program | TOPS |
| --- | --- |
| Kernel (Baseline) | 3 |
| Swarm alarm ([page 4](Your-First-Program)) | 2 |
| Scanner | 3 |
| Combat Medic | 3 |
| Mountain Goat | 2 |
| A hunting program ([page 11](Cool-Programs)) | 8 |
| **Total** | **21** |

That squeezes into LV0's 25, with nothing left over. But TOPS does more than let programs run:

- **In a hacking fight, TOPS is strength.** Your program attacks with the TOPS it booked, against the defender's. More TOPS, faster break-ins, up to twice as fast. A hacking program with `tops = 20` needs a bigger gateway.
- **Your firewall resists with TOPS.** Spare TOPS is what it throws in to slow an attacker down.

## Defence grows too

- **Buffer**: each attempt your firewall denies costs 256 MB. LV0's 1 GB is 4 denies; LV4's 10 GB is 40.
- **Hook points**: your kernel may handle 2 kinds of trouble at LV0, 3 from LV3, 4 from LV6 (see [page 8](Tuning-Your-Kernel)).
- **RAM**: attackers can pump junk (Leak) into you, up to half your RAM at a time. At LV0's 128 MB, two of those and you're withering. At LV4's 768 MB it takes a lot more.

## How to level up

1. **Build a Flash Station**: copper ingots, redstone, a Blank Data Chip and smooth stone.
2. **Craft the next firmware.** LV1 is a Blank Data Chip, 4 copper ingots and 2 redstone. Each level needs the one before.
3. **Collect Data Shards** from wired mobs: LV1 needs 4. They drop 1 to 4 each, more from higher levels.
4. **Flash.** Put the firmware and shards in the station and stay within 6 blocks for 10 seconds. You're defenceless while it runs, so do it somewhere safe.

All the recipes and costs are in [Progression](Progression).

**Next: [Why You Need a Firewall](Why-You-Need-A-Firewall)**
