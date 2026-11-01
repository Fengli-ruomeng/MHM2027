# The Edge Desktop

**Edge** is your gateway's operating system. Press **G** (change it under Controls) to open it; **Esc** closes it. If your gateway is off, G boots it first and Edge opens when the boot finishes (see [Gateway and Resources](Gateway-and-Resources#power)). The game keeps running while Edge is open, so your programs do too.

## The screen

- **Top bar**: EDGE, you and your address, your level and kernel, cell meters for Buffer, RAM, TOPS and Heat, and the in-game day and time.
- **Windows**: the terminal on the left, and a window for each program you open down the right. See [Windows](#windows).
- **Taskbar**: **◆ EDGE** shows the help. Then come the pills: the terminal, kernel.log when it's open, every running program, and any ended program whose window is still open. The dot shows what each is doing. Your sockets sit on the right.

Edge keeps its windows, output and history until you leave the world, so you can close it and pick up where you were.

## The terminal

Type a command and press **Enter**. Output types itself in. Underlined words are clickable:

- some run at once, such as **[stop]** and **[log]** next to a program in `ps`;
- others put a command in the prompt for you to check, such as a file name in `ls` (it fills in `run Breach`). Press Enter to go ahead.

Under the prompt are quick commands: `help`, `ls`, `ps`, `status`, `socket ls` and `flaws`, one click each. Typing always goes to the terminal, and brings it forward.

## Windows

Every program has its own window, showing what it printed with `print` and `sys.log`, newest at the bottom, each line stamped with the time of day. A window keeps the last 40 lines.

- **Opening**: `run` opens the program's window once it starts. `log <program>` or its taskbar pill brings it back. Programs started some other way, such as by another program, get a pill but no window until you open one.
- **Title bar**: the name, its socket (S1, O1...) or type, and what it's doing right now.
- **Moving**: drag the title bar. Drag the corner to resize. Double-click the title bar, or press **□**, to fill the screen.
- **_** sends the window to the taskbar. Click its pill to bring it back; clicking the pill of the window on top sends it away again.
- **×** **stops the program** and closes its window. The terminal's × closes Edge.
- **Overload** programs show your Heat at the bottom of their window.

When a program ends, its window stays and says how: **finished**, **stopped** (and why, such as a reboot) or **crashed**. For a crash it shows the error and the line it happened on, with buttons:

- **restart** runs it again in the same window;
- **edit line 12** opens the script in the editor with the cursor on that line (not for presets);
- **close** closes the window.

Edge keeps the windows of the last 8 programs that ended.

**kernel.log** (`log` with no program) is the kernel's own window: installs, reboots, panics, access attempts, denials, resists and sessions.

## Commands

| Command | Does |
| --- | --- |
| `ls` | What's in your storage: your programs, kernels and presets |
| `run <program>` | Start a program and open its window. Names with spaces work: `run Adrenaline Shot` |
| `stop <program>` | Stop one |
| `ps` | Running programs, with their state, TOPS and RAM |
| `log [program]` | Open a program's window, or kernel.log |
| `status` | Level, kernel, Buffer, RAM and Leak, TOPS, Heat and sockets |
| `flaws` | Your flaws and their causes, and the ones you're close to |
| `socket ls` / `socket add <program>` / `socket rm <program>` | Your sockets; changes need a Flash Station (see [Sockets and Overload](Sockets-and-Overload)) |
| `kernel` / `kernel ls` / `kernel install <name>` | Your kernel; installs need a Flash Station (see [Kernels and Flaws](Kernels-and-Flaws)) |
| `edit <file>` | Open one of your scripts in the code editor |
| `burn <file>` | Copy one of your scripts onto a Blank Data Chip from your inventory |
| `folder` | Open your storage folder (singleplayer only) |
| `reboot` | Restart the gateway (see [Gateway and Resources](Gateway-and-Resources#reboots-and-downtime)) |
| `clear` | Clear the terminal |
| `help` | List the commands |
| `exit` | Close Edge |

## Keys

| Key | Does |
| --- | --- |
| Enter | Runs the command |
| Tab | Completes the command or name you're typing; with several matches it lists them |
| Up / Down | Goes back and forward through your commands |
| Mouse wheel | Scrolls the window under the mouse |
| Page Up / Page Down | Scrolls the window on top |
| Ctrl+1 to Ctrl+9 | Clicks that taskbar pill: opens, brings forward or sends away its window |
| Ctrl+W | Closes the window on top, stopping its program (not the terminal) |
| Ctrl+V | Pastes |
| Ctrl+L | Clears the terminal |
| Esc | Closes Edge |

## Program states

`ps`, the title bars and the taskbar dots show what each program is doing:

| State | Meaning |
| --- | --- |
| running | It ran code this tick |
| idle | Waiting for its next event |
| sleeping | Inside `sys.sleep` |
| scanning | Waiting for `remote.inspect` |
| accessing, escalating | Waiting for an access attempt |
| paused | It used its whole budget this tick and continues next tick |
| finished, stopped, crashed | It has ended; its window says why |

## The code editor

`edit <file>` opens a full editor for your storage, with Lua highlighting. Closing it takes you back to Edge.

- **Live checks**: as you type, the bar at the bottom shows any error with its line, or the program's type, TOPS, RAM and highest API level. Warnings tell you what won't run on your gateway yet.
- **Autocomplete**: **Ctrl+Space** opens suggestions for the APIs, events and hooks. Pick one with the arrow keys and **Enter** or **Tab**.
- **Keys**: **Ctrl+S** saves, **Ctrl+Z** undoes, **Ctrl+Y** or **Ctrl+Shift+Z** redoes, **Esc** closes.

Presets are locked, so they can't be opened. Your files live on the server, in your own folder, and only you can open them.
