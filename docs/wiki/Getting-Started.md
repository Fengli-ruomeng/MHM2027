# Getting Started

You join the world with an **LV0 gateway**. It's weak, but it already runs programs.

## 1. Read the HUD

The top-left corner shows your gateway at a glance:

- **LV0** and your free **TOPS**: how much computing you have left.
- **RAM**: what your programs and your Leak use, out of your total.
- **Buffer**: your cyber health bar. Attacks and defences spend it, and it does not refill by itself.
- **Heat**: shown once you start running hot.
- **Flaws**: any weakness you have right now, such as `thermal` when you overheat.

The details are in [Gateway and Resources](Gateway-and-Resources).

## 2. Open the panel

Press **G**. The panel has four tabs:

- **Status**: bars for RAM, TOPS, Buffer and Heat, plus your kernel, sockets and flaws.
- **Processes**: running programs, your sockets, and the scripts in your storage.
- **Kernel**: your kernel's settings and flaws, and the kernels you could install.
- **Log**: what your programs printed, and anything that went wrong.

See [Control Panel](Control-Panel) for every button.

## 3. Run your first program

Your storage starts with example scripts. In **Processes**, find **Monitor.lua** and press **Run**. It warns you when your health gets low. Open the **Log** tab to see it start.

Try **Status.lua** too: it prints your RAM, TOPS and programs to the log and stops.

## 4. Write your own

Press **Code editor** in the Processes tab and create a file. A program is a header plus the events it answers:

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

The editor checks your code as you type and shows what it will cost. Save with **Ctrl+S**, then run it from the Processes tab. [Scripting](Scripting) explains everything in this example.

## 5. Things that will catch you out

- **Programs that change your body need a socket.** `me.speed_boost` and the other boosts only run from a socket, and you can only change sockets at a **Flash Station**. See [Sockets and Overload](Sockets-and-Overload).
- **Buffer doesn't come back.** Craft **Cyber Potions** before you need them (see [Items](Items)).
- **Running out of TOPS crashes everything.** Your kernel panics, every program stops, and you get a short debuff. Always keep a little TOPS free.
- **Running out of RAM withers you.** Leak builds up when programs crash or when someone attacks you. Garbage collection clears it, but only while you're well fed.

## 6. Level up

From day 2, some zombies and skeletons spawn with gateways. They're tougher, skeletons will try to break into you, and they drop **Data Shards**. Craft a **Flash Station** and **Firmware LV1**, then flash your gateway to LV1. [Progression](Progression) has the full path to LV10.
