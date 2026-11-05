# Items

Everything is in the **Edge** creative tab.

## Chips

**Blank Data Chip**: crafted two at a time.

```
C R C      C = copper ingot, R = redstone
G G G      G = gold nugget
```

**Program Chip / Kernel Chip**: code on an item, for trading and loot. Hold use for a moment to write it into your gateway.

- **Your own code** (burned from storage with `burn <file>` in Edge): writing copies the file into your storage. The chip is kept, so you can share it as often as you like. The tooltip shows who wrote it, its cost and the APIs it uses.
- **Presets** (they shimmer, and the tooltip says "Preset"): locked code from loot or datapacks. Writing one adds it to your gateway, where you can run it but never read it, and **uses up the chip**. A preset needs a gateway level, and its code runs **one level above** that, so a preset can do things you couldn't write yourself yet.

Presets that come with the mod: Adrenaline Shot, Featherfall, Field Medic, the Aegis kernel, and Overclock (an [Overload](Sockets-and-Overload) preset).

## Data Shard

Dropped by any gateway mob that a player helped kill. Higher-level mobs drop more. Every firmware needs them (see [Progression](Progression)), and so do Cyber Potions.

## Flash Station and Firmware

See [Progression](Progression).

## Cyber Potions

The only way to refill **Buffer**. A Cyber Potion restores a share of your **max** Buffer instantly:

| Level | Restores | How to make it |
| --- | --- | --- |
| I | 10% | Craft: glass bottle, redstone, 2 Data Shards |
| II | 20% | Brew Cyber Potion I with a Data Shard |
| III | 35% | Brew II with a Data Shard |
| IV | 50% | Brew III with a Data Shard |
| V | 75% | Brew IV with a Data Shard |

Splash and lingering versions brew the usual way (gunpowder, dragon's breath), so you can refill your teammates, or a gateway mob you'd rather keep around. A potion drunk mid-flash isn't wasted: it adds to the Buffer you get back.

## Alontesi Tech

Experimental weapons that run on your gateway.

### Programmable Bow

> *Every arrow already knows where it's going.*

A bow with blue plating at the grip. It draws, fires and enchants like a bow, and takes any arrow. What's different is where the arrow goes:

- **While you hold a session on a gateway** (you've hacked it: see [Cyber Combat](Cyber-Combat)), every arrow plots a course for it and **follows it as it moves**. With sessions on several, it goes for the nearest. You'll see a cyan trail, sparks, and a flickering line of light from the arrow to its target.
- It gives up and drops like an ordinary arrow when no linked gateway is within **50 blocks** of it (the session ended, or they're too far), when they've been out of sight behind blocks for half a second, or after 10 seconds.
- **With no session open, there's nothing to aim for.** The arrow flies wild: a magenta trail, swerving at random for two seconds, then it falls. It's no use like that.

So hack first, shoot second. A session lasts 6 seconds: open one with `remote.access` and every arrow you loose in that time goes where it should.

**How to get one:** flash a plain bow at a [Flash Station](Progression#the-flash-station). Put the bow in the firmware slot and **8 Data Shards** in the shard slots, and press Flash; it costs **5 XP levels**. Like any flash it goes through your gateway, which is down for the 10 seconds. Enchantments, damage and a custom name carry over. A Null gateway can't do it.

## KuyoZ Mech

General-purpose cyber tools in white casings. None of them work on a Null gateway.

### Scanner

> *A path previously unconsidered.*

Hold use for 1 second while looking at a gateway within your scan range. It shows the gateway's name, level, distance and flaws on a card, just like `remote.inspect`, but with no program. The target's programs hear `on_scanned`.

```
I G I      I = iron ingot, G = glass pane
I C I      C = Blank Data Chip
W R W      W = white dye, R = redstone
```

### EMP Activator

> *Let's hope no one dies because of this.*

Hold use for 1.5 seconds and it sends out a pulse, then it's used up. **Every gateway within 8 blocks is forced to reboot, yours included**: programs stop, sessions end, and everyone gets the reboot debuff.

- **Gateway mobs** are stunned for 3 seconds and lose any hack they were running.
- **Unaffected**: Null players, ordinary mobs, and anyone in the middle of a flash.

Shapeless recipe: Blank Data Chip, 2 copper ingots, 2 redstone, a Data Shard and white dye.
