# FAQ

*Beginner's Guide, page 13 of 13.*

## My program won't start

The terminal says why. The usual reasons:

| It says | Because | Fix |
| --- | --- | --- |
| `needs gateway LV2` | It uses a power your level doesn't have | Level up ([page 5](Why-Level-Up)), or remove that call |
| `put it in a socket at a Flash Station` | It changes your body | At a station: `socket add <name>` |
| `Not enough TOPS` | Your running programs have booked them all | `stop` something, or lower its `tops` |
| `is an Overload program` | It has `overload = true` | It needs an Overload socket, from LV4 ([Sockets and Overload](Sockets-and-Overload)) |
| `The terminal is rebooting` | You just rebooted or panicked | Wait a few seconds |
| Nothing works, no HUD | Your gateway is off | Press **G** to boot it |
| A syntax error | A typo in the code | `edit <name>`: the bottom bar shows the line |

## My program started but does nothing

- Check its window (`log <name>`): any errors or prints?
- Is the event it waits for actually happening? Put `print("here")` at the top of the event function.
- Transient and query programs do their work in `on_run`; without one they end straight away. A resident does nothing until `on_start` runs or one of its events happens.

## Why am I suddenly slowed and can't jump?

A wired skeleton hacked you: its arrow started an access attempt, and your kernel let it through. Read [Why You Need a Firewall](Why-You-Need-A-Firewall). For now: kill the skeleton, or type `reboot` in Edge to throw it out.

## Why am I withering?

You're **out of memory**: programs plus Leak are more than your RAM. The R bar is magenta. Stop a program, and eat: your gateway cleans up Leak faster when you're full, and not at all at 6 food or less.

## My Buffer never comes back

It doesn't regenerate. Only Cyber Potions refill it ([Items](Items#cyber-potions)). If it keeps running out, your firewall is denying too much; see [Building a Firewall](Building-A-Firewall#version-2-spend-wisely).

## Where do Data Shards come from?

Wired mobs, when you had a hand in killing them: 1 to 4 each, more from higher-level mobs. They appear from day 2, give off small sparks, and are tougher deeper underground. See [Wired Mobs](Wired-Mobs).

## Do my programs keep running?

- **When Edge is closed:** yes.
- **When you die or log out:** no. Your gateway is **off** when you come back; press G, then start your programs again. A quick-wheel slot per program makes that fast.
- **After a reboot or panic:** no. Your kernel's `on_panic` hook can restart them ([page 8](Tuning-Your-Kernel)).

## What do I lose when I die?

Nothing of your gateway: your level, kernel, storage, presets, sockets and wheel all stay. Only running programs stop.

## Can I break the game with a bad program?

No. A program can only run as much code per tick as its TOPS allow, and then it waits. An endless loop just slows itself down (try `run Spinner`).

## How do I stop a program?

`stop <name>`, the **×** on its window, or the × in its row in `top`.

## Where are my script files?

On the server, in `<world>/edge/players/<your uuid>/`. In singleplayer, `folder` in Edge opens it. Only you can read or change them.

## Can other players hack me?

Unless the server turns it off (`playerTargets` in the config, see [Server Admins](Server-Admins)), yes, by exactly the same rules as mobs.

## I just want normal Minecraft

Flash **Null Firmware** at a Flash Station: no gateway, no HUD, and nothing can hack you. Restore Firmware brings it back, at LV0. See [Progression](Progression#the-null-route).

---

That's the guide. From here, the [API Reference](API-Reference) lists everything your programs can call, and [Cyber Combat](Cyber-Combat), [Kernels and Flaws](Kernels-and-Flaws) and [Gateway and Resources](Gateway-and-Resources) have every rule and number.

**Back to the start: [Beginner's Guide](Beginners-Guide)**
