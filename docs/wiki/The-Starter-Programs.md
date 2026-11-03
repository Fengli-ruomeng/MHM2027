# The Starter Programs

*Beginner's Guide, page 3 of 13.*

Your storage starts with eight example programs. Some are tools you'll keep using; some are there to show you how something works. Type `ls` in Edge to see them, and `cat <name>` to read one.

## Run these now (LV0)

### Monitor: a health alarm

`run Monitor`. Every 2 seconds it checks your health, and when you drop below 30% it puts a card on your screen. It warns once per drop, not every 2 seconds.

**Why it's useful:** in a hard fight you're watching the mobs, not your hearts. **What it teaches:** the basic shape of a program that runs all the time: start a timer, check something, react.

### Scanner: know your enemy

`run Scanner`. Every 3 seconds it finds the nearest gateway and **inspects** it: name, level and **flaws**, written in its window. It also tells you when someone inspects *you*.

**Why it's useful:** a skeleton's flaws tell you how to hack it fast (see [page 9](Hacking-Mobs-And-Players)). The target callout shows them beside the mob for a minute after. **What it teaches:** loops and tables, going through every flaw a target has.

### Counterpunch: hit back without lifting a finger

`run Counterpunch`. When something with a gateway hits you for 3 or more, it hacks the attacker, **freezes it** for 1.5 seconds and turns its head to the sky.

**Why it's useful:** it buys you time against a wired zombie at no cost but TOPS. **What it teaches:** that programs can attack, and that attacks take time (about 3 seconds at first).

## Needs a socket (LV1)

### Adrenaline: speed after a big hit

When a single hit takes 4 or more health, you run 30% faster for 2 seconds. It changes your body, so it needs **LV1** and has to sit in a **socket**: `socket add Adrenaline` at a Flash Station, then `run Adrenaline`.

**What it teaches:** body-changing powers are gated by level and by sockets. See [Why Level Up?](Why-Level-Up).

## Kernels: your firewall and your recovery plan

These aren't programs; they're **kernels**, the software that runs your gateway. You install one at a Flash Station with `kernel install <name>`, and only one at a time. Until you do, you run **Baseline**, which lets every attack straight through.

### Gatekeeper: a starter firewall

- Anyone trying to break in through one of your **flaws** is turned away (costs Buffer).
- Anyone using **force** is slowed down by your spare TOPS.
- When someone gets in anyway, you get a card.

**Why it's useful:** it's the difference between a skeleton freezing you in one arrow and not at all. [Why You Need a Firewall](Why-You-Need-A-Firewall) explains it fully.

### Bulwark: crash recovery

Bulwark doesn't defend. It decides what happens when things go wrong: after a **kernel panic** it restarts Monitor, and when you run **out of memory** it stops Spinner first instead of your newest program.

**What it teaches:** kernels have other jobs besides the firewall. See [Tuning Your Kernel](Tuning-Your-Kernel).

## Demonstrations

### Status: a quick report

`run Status` prints your free RAM, free TOPS and every running program into its window, then stops. Yes, it's a bit dull. It's there to show the third kind of program, the **query**: run once, read, and finish.

### Spinner: you can't break the game

Spinner is a loop that never ends, on purpose. Run it and watch `ps`: it says "over budget". Each tick it may run 20 Lua instructions per TOPS it booked; when it uses them up, it waits for the next tick. An endless loop only slows itself down. Stop it with `stop Spinner`.

## Where to go from here

None of these is very ambitious. They're starting points. By page 11 you'll be writing programs that hunt mobs on their own, disarm skeletons and keep score of your fights.

Presets are a different thing: locked programs on chips, made by the mod or your server's datapacks. You can run them but not read them. Some do things your level couldn't do yet. See [Items](Items#chips).

**Next: [Your First Program](Your-First-Program)**
