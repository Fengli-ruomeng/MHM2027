# Gateway and Resources

Your gateway has a **level**, from LV0 up to LV10 (LV11 is creative only, and Null is LV-1). The level sets how much of everything you have.

## Levels

| Level | RAM | TOPS | Max Buffer | Standard sockets | Overload sockets | Hook points |
| --- | --- | --- | --- | --- | --- | --- |
| LV0 | 128 MB | 25 | 1 GB | 1 | 0 | 2 |
| LV1 | 256 MB | 45 | 3 GB | 2 | 0 | 2 |
| LV2 | 384 MB | 60 | 5 GB | 2 | 0 | 2 |
| LV3 | 512 MB | 85 | 8 GB | 2 | 0 | 3 |
| LV4 | 768 MB | 100 | 10 GB | 4 | 1 | 3 |
| LV5 | 1 GB | 120 | 12 GB | 4 | 1 | 3 |
| LV6 | 1.5 GB | 135 | 15 GB | 4 | 1 | 4 |
| LV7 | 2.5 GB | 155 | 20 GB | 4 | 1 | 4 |
| LV8 | 4 GB | 175 | 30 GB | 4 | 1 | 4 |
| LV9 | 7 GB | 195 | 50 GB | 4 | 1 | 4 |
| LV10 | 12 GB | 300 | 100 GB | 8 | 2 | 4 |

Your level also sets your reach: you can scan gateways up to **16 + level** blocks away and try to break into them up to **12 + level** blocks away.

You raise your level at a Flash Station (see [Progression](Progression)). The level is kept when you die.

## RAM

Every running program takes RAM for its code, and so does your kernel. **Leak** takes RAM too: it's memory that crashed programs and attackers leave behind.

**Garbage collection** clears Leak by itself, but how fast depends on how well fed you are:

- At full saturation it clears **2%** of your total RAM per second; at zero saturation, **0.5%**.
- At **6 food points or less** it stops completely.
- Your kernel's `gc` setting changes the speed: `lazy` is half, `aggressive` is double.

**Out of memory** (programs plus Leak above your RAM) is dangerous:

- You get **Wither** for as long as it lasts.
- Once a second, your kernel's `on_low_ram` hook decides what to stop. Without the hook, your newest program stops.

## TOPS

TOPS is computing power. Each program **reserves** the TOPS in its header for as long as it runs, and your kernel reserves a little too (2, plus 0 to 3 for garbage collection).

- A program can't start if it would leave you with less than **1 free TOPS**.
- When you stop a program, its TOPS come back over 3 seconds.
- If you run out of TOPS anyway, your kernel **panics**:
  - every program crashes, each leaving a quarter of its RAM as Leak;
  - nothing can start for 5 seconds, and you get a short debuff;
  - then your kernel's `on_panic` hook decides what starts again.
- A kernel with `schedule = "priority"` doesn't panic. It crashes your lowest-priority programs until you fit again (see [Kernels and Flaws](Kernels-and-Flaws)).

A program can only run so much code each tick: 20 Lua instructions per tick for each TOPS it reserved. A program that goes over is paused until the next tick, and the panel shows it as "paused".

## Buffer

Buffer is your cyber health bar.

- **Deny** an access attempt: 256 MB.
- **Close a session** someone opened on you: 512 MB.
- It **never refills by itself**. Only Cyber Potions refill it (see [Items](Items)).
- Flashing to a new level raises your maximum, not your current Buffer.

## Heat

Heat runs from 0 to 100 and cools by 2 a second. Two things raise it:

- **Resisting** an access attempt: 1.5 Heat for every TOPS you resist with.
- **Overload** programs, every second they run (see [Sockets and Overload](Sockets-and-Overload)).

Above **70 Heat** you have the `thermal` flaw, so attackers get in faster. At 100, every Overload program crashes.

## Reboots and downtime

| What happens | Downtime | Debuff |
| --- | --- | --- |
| You reboot (panel or `sys.reboot`) | 3 s | Slowness, Weakness |
| Kernel panic | 5 s | Slowness, Weakness |
| Installing a kernel | 10 s | Slowness, Weakness, Hunger, 2 fewer hearts |

While your gateway is down, nothing runs, every session on you or by you ends, and **nobody can break into you**. A reboot is a real escape, but it stops everything you had running.

## Death

When you die, your programs stop and every session ends. You keep your level, your kernel, your storage, your written presets and your sockets.
