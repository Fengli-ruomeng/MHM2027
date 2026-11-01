# Getting Started

You join the world with an **LV0 gateway**. It's weak, but it already runs programs.

## 0. Switch it on

Your gateway starts **off** whenever you join a world or respawn. While it's off you have no HUD, no programs and no cyber gear, you're slowed, and anything can hack you without a fight. Press **G** to boot it: the boot screen plays for a few seconds, then Edge opens and the HUD comes back. You can close the boot screen early, but the boot carries on and you're blind and badly slowed until it finishes.

## 1. Read the HUD

Edge draws its own HUD, the **Lens**.

**Left edge: your gateway, as two vertical bars.**

- **B**, Buffer: your cyber health bar. Attacks and defences spend it, and it does not refill by itself.
- **R**, RAM: white for your programs, violet for Leak, magenta when you're out of memory.
- **H**, Heat, appears beside them while you're running warm, and turns magenta from 70.

The numbers show for a moment whenever they change. Hold **Left Alt** (change it under Controls) to see everything: level, kernel, Buffer, RAM, TOPS and Heat. Flaws that come and go, such as `thermal`, appear as chips under the bars, and so does a warning when your TOPS run low.

**Bottom left: your sockets.** S1, S2… and O1 are your sockets, with a short name for what sits in each. A lit border means the program is running; an Overload socket fills up with Heat.

**Right side: event cards.** Everything the mod tells you arrives as a card instead of a chat message. The tag says who is talking, such as `10.71.4.200 // ACCESS` or `WATCHER // NOTIFY`, and the colour says how much it matters:

| Colour | Means |
| --- | --- |
| Magenta | Someone is acting against you: an access attempt, a session, a panic |
| Orange | Needs your attention: a crash, a refusal, a flash in progress |
| Cyan | You or your programs: scans, notices, success |
| Grey | Background |

Cards fade after a few seconds; attacks stay longest.

**Banners.** The moments that matter most also get a warning banner: a headline over a strip of caution tape that reads **DANGER**, **WARNING** or **NOTICE**. You'll see **SYSTEM BREACH** when someone gets into your gateway, **KERNEL PANIC**, **OVERHEAT**, **EMP**, **FIRMWARE FLASH** and **LV ONLINE**, and **OUT OF MEMORY** for as long as it lasts. The details are on the card that comes with it.

**Glitches.** When you're hit or attacked, the HUD glitches and its text scrambles for a moment. You can turn the glitches down or off, and the banners off, under Mods → Edge → Config.

The numbers behind all of this are in [Gateway and Resources](Gateway-and-Resources).

## 2. Open Edge

Once your gateway is on, **G** opens **Edge**, your gateway's operating system: a terminal where you type commands, with clickable output and a taskbar. **Esc** closes it. Type `help` to see every command, or see [The Edge Desktop](Control-Panel).

## 3. Run your first program

Your storage starts with example scripts. Type `ls` to see them, then `run Monitor` (or click **Monitor.lua** in the `ls` output and press Enter). It warns you when your health gets low. Its window opens on the right and shows what it prints; `ps` lists everything running.

Try `run Status` too: it prints your RAM, TOPS and programs in its window and stops.

## 4. Write your own

Type `edit Hello` to create a file in the code editor. A program is a header plus the events it answers:

```lua
program { name = "Hello", type = "resident", tops = 2 }

function on_start()
  sys.log("Hello from my gateway")
  sys.every(5)
end

function on_timer()
  local hp = me.health()
  if hp < 8 then
    sys.notify("Careful: " .. math.floor(hp) .. " HP")
  end
end
```

The editor checks your code as you type and shows what it will cost. Save with **Ctrl+S**, press **Esc** to go back to Edge, then `run Hello`. [Scripting](Scripting) explains everything in this example.

## 5. Things that will catch you out

- **Programs that change your body need a socket.** `me.speed_boost` and the other boosts only run from a socket, and you can only change sockets at a **Flash Station**. See [Sockets and Overload](Sockets-and-Overload).
- **Buffer doesn't come back.** Craft **Cyber Potions** before you need them (see [Items](Items)).
- **Running out of TOPS crashes everything.** Your kernel panics, every program stops, and you get a short debuff. Always keep a little TOPS free.
- **Running out of RAM withers you.** Leak builds up when programs crash or when someone attacks you. Garbage collection clears it, but only while you're well fed.

## 6. Level up

From day 2, some zombies and skeletons spawn with gateways. They're tougher, skeletons will try to break into you, and they drop **Data Shards**. Craft a **Flash Station** and **Firmware LV1**, then flash your gateway to LV1. [Progression](Progression) has the full path to LV10.
