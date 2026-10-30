# Control Panel

Press **G** (change it under Controls) to open the panel; press it again to close it. The game keeps running while it's open, so your programs do too.

## Status

Bars for RAM (programs, then Leak), TOPS, Buffer and Heat, then:

- your kernel and how many programs are running;
- how many sockets and hook points your gateway has;
- your active flaws;
- a warning while you're rebooting or out of memory.

## Processes

**Running programs**, with their type, RAM, reserved TOPS, average and peak TOPS used, and their state:

| State | Meaning |
| --- | --- |
| running | It ran code this tick |
| idle | Waiting for its next event |
| sleeping | Inside `sys.sleep` |
| scanning | Waiting for `remote.inspect` |
| accessing, escalating | Waiting for an access attempt |
| paused | It used its whole budget this tick and continues next tick |

**Sockets**: S1, S2… are standard sockets, O1… are Overload sockets. **Remove** takes a program out of its socket. Sockets can only change at a Flash Station, so elsewhere the buttons are greyed out. See [Sockets and Overload](Sockets-and-Overload).

**Storage**: everything you can run. Your own files show as `Name.lua`; presets show in gold with a tag.

| Button | Does |
| --- | --- |
| Run | Starts it |
| Socket | Puts it in a free socket of the right kind (at a Flash Station) |
| Burn | Copies the file onto a Blank Data Chip from your inventory, to give or trade |
| Edit | Opens it in the code editor |

At the bottom:

- **Reboot**: stops everything and ends every session on you; see [Gateway and Resources](Gateway-and-Resources).
- **Code editor**: opens the editor.
- **Open folder**: opens your storage folder (singleplayer only).

## Kernel

Your installed kernel: its settings, hooks and cost, then every flaw you have. Flaws you're close to triggering are listed in grey.

Below that are the kernels in your storage. **Baseline** is built in. **Install** swaps your kernel. You can only do it within 6 blocks of a Flash Station, and only once every 60 seconds. See [Kernels and Flaws](Kernels-and-Flaws).

## Log

The last 50 lines from your terminal: programs starting, stopping and crashing, `sys.log` and `print` output, kernel events and attacks on you. Scroll to go back.

## The code editor

A full editor for your storage, with Lua highlighting.

- **Live checks**: as you type, the bar at the bottom shows any error with its line, or the program's type, TOPS, RAM and highest API level. Warnings tell you what won't run on your gateway yet.
- **Autocomplete**: **Ctrl+Space** opens suggestions for the APIs, events and hooks. Pick one with the arrow keys and **Enter** or **Tab**.
- **Keys**: **Ctrl+S** saves, **Ctrl+Z** undoes, **Ctrl+Y** or **Ctrl+Shift+Z** redoes, **Esc** closes.

Presets are locked, so they don't appear in the editor. Your files live on the server, in your own folder, and only you can open them.
