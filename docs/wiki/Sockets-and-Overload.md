# Sockets and Overload

Programs that only read or decide can run as many at a time as your RAM and TOPS allow. Programs that **change your body** are limited by **sockets**.

## Standard sockets

A program needs a socket if it uses any body-changing call: `me.speed_boost`, `me.jump_boost`, `me.fall_guard`, `me.regen_burst`, `me.max_health_boost`, `me.damage_boost` or `me.knockback_resist`. The **Socket** column in the [API Reference](API-Reference) marks them.

- How many sockets you have depends on your level: 1 at LV0, 2 from LV1, 4 from LV4, 8 at LV10.
- A socket holds something from your storage: one of your scripts, or a preset you've written into your gateway. It isn't an item, and it isn't lost when you die.
- You can **only change sockets at a Flash Station**, within 6 blocks. Use the **Socket** and **Remove** buttons in the panel's Processes tab.
- Away from the station you can still start and stop socketed programs as often as you like.
- Starting a body-changing program that isn't socketed fails with "put it in a socket at a Flash Station".
- Removing a running program from its socket stops it.

So before a fight, choose at the station which enhancements to bring.

## Overload

From **LV4** your gateway has Overload sockets: 1, or 2 at LV10. They run code from **above your level**, and you pay for it in Heat.

An Overload program:

- has `overload = true` in its header;
- must be **resident** (a one-shot would never pay its Heat);
- runs only from an Overload socket.

How far above your level it may reach:

| Comes from | Reaches |
| --- | --- |
| A preset (loot or datapack) | Your level + 6 |
| Your own script | Your level + 2 |

Both numbers can be changed in the server config.

### The cost

Every second an Overload program runs:

- **Heat** goes up by **2 plus the level gap**. An LV4 gateway running LV10 code gains 8 Heat a second.
- **Leak** goes up by a tenth of its RAM.

Heat still cools by 2 a second. At **100 Heat**, every Overload program crashes, each leaves its RAM as Leak, and the Overload sockets **lock for 60 seconds**. And above 70 Heat you have the `thermal` flaw, so attackers get in faster.

Overload is a burst: turn it on for the fight that needs it, then turn it off and cool down.

### Writing one

```lua
program { name = "Surge", type = "resident", tops = 4, overload = true }

-- damage_boost is LV6. On an LV4 gateway that's a gap of 2: 4 Heat a second.
function on_start()
  sys.every(1)
  on_timer()
end

function on_timer()
  me.damage_boost(0.3, 10)   -- on cooldown it returns false; the timer just tries again
end
```

The editor knows about Overload. It checks the code against your level + 2 and shows the Heat it will cost, for example "Overload: Heat 4/s".

**Overclock**, an Overload preset that comes with the mod, runs LV8 code: strength, speed and knockback resistance.
