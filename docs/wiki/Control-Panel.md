# The Edge Desktop

**Edge** is your gateway's operating system. Press **G** (change it under Controls) to open it; **Esc** closes it. If your gateway is off, G boots it first and Edge opens when the boot finishes (see [Gateway and Resources](Gateway-and-Resources#power)). The game keeps running while Edge is open, so your programs do too.

## The screen

- **Top bar**: EDGE, you and your address, your level and kernel, cell meters for Buffer, RAM, TOPS and Heat, and the in-game day and time.
- **Windows**: the terminal on the left, a window for each program you open down the right, and the code editor over most of the screen when you open it. See [Windows](#windows).
- **Taskbar**: **◆ EDGE** shows the help. Then come the pills: the terminal, the editor, top and kernel.log when they're open, every running program, and any ended program whose window is still open. The dot shows what each is doing. Your sockets sit on the right.

Edge keeps its windows, output, history and the editor's unsaved code until you leave the world, so you can close it and pick up where you were.

## The terminal

Type a command and press **Enter**. Output types itself in. Underlined words are clickable:

- some run at once, such as **[stop]** and **[log]** next to a program in `ps`;
- others put a command in the prompt for you to check, such as a file name in `ls` (it fills in `run Breach`). Press Enter to go ahead.

Long lines wrap to fit the window, and re-flow when you resize it. A wrapped line continues under its label (a file list under `programs`, a help line under its description), and a clickable word cut in two works on both rows. Only output text wraps: title bars, buttons, the prompt, progress bars and `top`'s table never do.

Under the prompt are quick commands: `help`, `ls`, `ps`, `status`, `socket ls` and `flaws`, one click each.

The keyboard goes to the window on top. Click a window to bring it forward. When the editor is on top, typing goes into your code; anywhere else, typing goes to the terminal and brings it forward.

## Windows

Every program has its own window, showing what it printed with `print` and `sys.log`, newest at the bottom, each line stamped with the time of day. A window keeps the last 40 lines.

- **Opening**: `run` opens the program's window once it starts. `log <program>` or its taskbar pill brings it back. Programs started some other way, such as by another program, get a pill but no window until you open one.
- **Title bar**: the name, its socket (S1, O1...) or type, and what it's doing right now.
- **Moving**: drag the title bar. Drag the corner to resize. Double-click the title bar, or press **□**, to fill the screen.
- **_** sends the window to the taskbar. Click its pill to bring it back; clicking the pill of the window on top sends it away again.
- **×** **stops the program** and closes its window. The terminal's × closes Edge.
- **Overload** programs show your Heat at the bottom of their window.
- **Access attempts and inspections** show a bar at the bottom of the window while the program waits: the target's address, the method, how far along it is and the seconds left. Magenta is an access attempt (or an escalation to root), cyan an inspection.

When a program ends, its window stays and says how: **finished**, **stopped** (and why, such as a reboot) or **crashed**. For a crash it shows the error and the line it happened on, with buttons:

- **restart** runs it again in the same window;
- **edit line 12** opens the script in the editor with the cursor on that line (not for presets);
- **close** closes the window.

Edge keeps the windows of the last 8 programs that ended.

**kernel.log** (`log` with no program) is the kernel's own window: installs, reboots, panics, access attempts, denials, resists and sessions.

**top** is a live process table, updated twice a second. At the top: the TOPS your programs and kernel have reserved, your RAM and your Heat. Then every running program, busiest first:

| Column | Shows |
| --- | --- |
| NAME | Click it to open the program's window |
| TYPE, STATE | As in `ps` |
| RES | The TOPS it reserved in its header |
| USE | The TOPS it actually used, on average. Orange when it's close to what it reserved, so it often has to finish next tick |
| PEAK | The most it has used in a tick |
| RAM | Its RAM |

In a narrow window, TYPE, PEAK and RES drop out, in that order. **×** at the end of a row stops that program. The last line is your kernel's own reserve. Use top to tune a program's `tops`: reserve a little more than its USE.

## Commands

| Command | Does |
| --- | --- |
| `ls` | What's in your storage: your programs, kernels and presets |
| `cat <file>` | Print one of your scripts, numbered and highlighted. Presets are locked |
| `run <program>` | Start a program and open its window. Names with spaces work: `run Adrenaline Shot` |
| `stop <program>` | Stop one |
| `ps` | Running programs, with their state, TOPS and RAM |
| `top` | Open the live process table (see [Windows](#windows)) |
| `log [program]` | Open a program's window, or kernel.log |
| `status` | Level, kernel, Buffer, RAM and Leak, TOPS, Heat and sockets |
| `flaws` | Your flaws and their causes, and the ones you're close to |
| `wheel` / `wheel set <1-8> <command>` / `wheel rm <1-8>` | The quick wheel's shortcuts (see [The quick wheel](#the-quick-wheel)) |
| `scan [radius]` | The gateways around you, nearest first: address, name, level and distance. Up to 8, within your scan range (16 + your level blocks). Players are orange. You can scan about once a second; your kernel's `rate_limit` changes that, as it does for `remote.inspect`. Nobody is told you scanned |
| `socket ls` / `socket add <program>` / `socket rm <program>` | Your sockets; changes need a Flash Station (see [Sockets and Overload](Sockets-and-Overload)) |
| `kernel` / `kernel ls` / `kernel install <name>` | Your kernel; installs need a Flash Station (see [Kernels and Flaws](Kernels-and-Flaws)) |
| `edit [file]` | Open the code editor, with one of your scripts in it. A name you don't have yet, such as `edit Hello`, creates it |
| `man [topic]` | How something works. `man me.speed_boost` shows an API's call, what it does, the level it needs, what it costs and whether it needs a socket. `man me` lists a namespace; `man on_scanned` an event; `man on_access_request` a hook; `man scan` a command. `man` alone lists everything, all clickable |
| `burn <file>` | Copy one of your scripts onto a Blank Data Chip from your inventory |
| `folder` | Open your storage folder (singleplayer only) |
| `reboot` | Restart the gateway (see [Gateway and Resources](Gateway-and-Resources#reboots-and-downtime)) |
| `clear` | Clear the terminal |
| `help` | List the commands |
| `exit` | Close Edge |

## The quick wheel

The quick wheel holds eight shortcuts you fire without opening Edge. Hold **V** in game: point at one and let go, or press its number (1 to 8) while V is held. The numbers fire at once and the wheel stays open, so you can chain them; right-click cancels. You keep moving while it's open; only the camera stops so the mouse can point.

A shortcut is any Edge command, run as if you'd typed it, so it lands in the terminal's history:

```
wheel set 1 run Breach
wheel set 2 stop Overclock
wheel set 3 scan 8
wheel rm 3
wheel
```

`wheel` alone lists them. You can change them anywhere, even off a Flash Station; a body-changing program still needs its socket to run. While the wheel is open, the panel beside it shows what the pointed-at shortcut does (for `run`, the program's type, TOPS and RAM, and a preset's description), the gateway under your crosshair and your free TOPS.

Edge stays closed: the answer comes back on a card. Shortcuts that need a window (`log`, `top`, `edit`, `cat`) open Edge. Programs you run from the wheel use `remote.target()` to find what you're looking at.

## Keys

| Key | Does |
| --- | --- |
| Enter | Runs the command |
| Tab | Completes the command or name you're typing; with several matches it lists them |
| Up / Down | Goes back and forward through your commands |
| Mouse wheel | Scrolls the window under the mouse |
| Page Up / Page Down | Scrolls the window on top |
| Ctrl+1 to Ctrl+9 | Clicks that taskbar pill: opens, brings forward or sends away its window |
| Ctrl+W | Closes the window on top, stopping its program (not the terminal). The editor asks first if you have unsaved changes |
| Ctrl+V | Pastes |
| Ctrl+L | Clears the terminal |
| Esc | Closes Edge (in the editor, it first closes the suggestion list or question that's open) |

The editor has keys of its own; see [The code editor](#the-code-editor).

## Program states

`ps`, the title bars and the taskbar dots show what each program is doing:

| State | Meaning |
| --- | --- |
| running | It ran code this tick |
| idle | Waiting for its next event |
| sleeping | Inside `sys.sleep` |
| scanning | Waiting for `remote.inspect`; its window shows a bar |
| accessing, escalating | Waiting for an access attempt; its window shows a bar |
| busy | Its job is bigger than one tick's budget, so it carries on next tick. Normal for big jobs |
| over budget | Busy for 2 seconds straight: it can't keep up. Book more TOPS, or do less each time |
| finished, stopped, crashed | It has ended; its window says why |

## The code editor

`edit` opens the editor in its own window, with Lua highlighting. It covers most of the desktop and leaves a strip of the terminal showing; move, resize or maximise it like any window. `edit <file>` opens a file in it, and **edit line 12** on a crashed program's window opens the file at that line.

- **Files**: your scripts are listed on the left (K marks a kernel). Click one to open it. **+ Program** and **+ Kernel** create a new one from a template; **Save**, **Rename** and **Delete** are at the top. Rename needs the file saved first.
- **Live checks**: as you type, the bar at the bottom shows any error with its line, or the program's type, TOPS, RAM and highest API level. Warnings tell you what won't run on your gateway yet.
- **Autocomplete**: **Ctrl+Space** opens suggestions for the APIs, events and hooks. Pick one with the arrow keys and **Enter** or **Tab**.
- **Keys**: **Ctrl+S** saves, **Ctrl+Z** undoes, **Ctrl+Y** or **Ctrl+Shift+Z** redoes, **Ctrl+A**, **Ctrl+C**, **Ctrl+X** and **Ctrl+V** work as usual, **Tab** and **Shift+Tab** indent.
- **Unsaved work**: the title bar and the taskbar pill say when you have unsaved changes (**editor \***). Closing Edge keeps them; switching files, creating one or closing the editor's window asks before throwing them away.

Presets are locked, so they can't be opened. Your files live on the server, in your own folder, and only you can open them.
