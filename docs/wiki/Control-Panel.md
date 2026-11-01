# The Edge Desktop

**Edge** is your gateway's operating system. Press **G** (change it under Controls) to open it; **Esc** closes it. If your gateway is off, G boots it first and Edge opens when the boot finishes (see [Gateway and Resources](Gateway-and-Resources#power)). The game keeps running while Edge is open, so your programs do too.

## The screen

- **Top bar**: EDGE, you and your address, your level and kernel, cell meters for Buffer, RAM, TOPS and Heat, and the in-game day and time.
- **The terminal**: type a command and press **Enter**. Output types itself in. Underlined words are clickable:
  - some run at once, such as **[stop]** and **[log]** next to a program in `ps`;
  - others put a command in the prompt for you to check, such as a file name in `ls` (it fills in `run Breach`). Press Enter to go ahead.
- **Quick commands** under the prompt: `help`, `ls`, `ps`, `status`, `socket ls` and `flaws`, one click each.
- **Taskbar**: **◆ EDGE** shows the help, there's a pill for each running program (click one to see its lines from the log), and your sockets sit on the right.

The terminal keeps its output and history until you leave the world, so you can close Edge and pick up where you were.

## Commands

| Command | Does |
| --- | --- |
| `ls` | What's in your storage: your programs, kernels and presets |
| `run <program>` | Start a program. Names with spaces work: `run Adrenaline Shot` |
| `stop <program>` | Stop one |
| `ps` | Running programs, with their state, TOPS and RAM |
| `log [program]` | The terminal log (the last 50 lines), or just one program's lines |
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
| Page Up / Page Down, mouse wheel | Scrolls the output |
| Ctrl+V | Pastes |
| Ctrl+L | Clears the terminal |
| Esc | Closes Edge |

## Program states

`ps` and the taskbar show what each program is doing:

| State | Meaning |
| --- | --- |
| running | It ran code this tick |
| idle | Waiting for its next event |
| sleeping | Inside `sys.sleep` |
| scanning | Waiting for `remote.inspect` |
| accessing, escalating | Waiting for an access attempt |
| paused | It used its whole budget this tick and continues next tick |

## The code editor

`edit <file>` opens a full editor for your storage, with Lua highlighting. Closing it takes you back to Edge.

- **Live checks**: as you type, the bar at the bottom shows any error with its line, or the program's type, TOPS, RAM and highest API level. Warnings tell you what won't run on your gateway yet.
- **Autocomplete**: **Ctrl+Space** opens suggestions for the APIs, events and hooks. Pick one with the arrow keys and **Enter** or **Tab**.
- **Keys**: **Ctrl+S** saves, **Ctrl+Z** undoes, **Ctrl+Y** or **Ctrl+Shift+Z** redoes, **Esc** closes.

Presets are locked, so they can't be opened. Your files live on the server, in your own folder, and only you can open them.
