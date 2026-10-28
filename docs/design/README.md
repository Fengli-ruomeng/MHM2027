# Design docs

Snapshots of the Claude Doc "MoreHarderMinecraft: Edge - Design Draft v2", so tools that can't open
Claude Docs (Cursor, plain editors) can read the design from the repo. The Claude Doc stays the
source of truth; refresh these files when it changes.

| File | Tab | Notes |
| --- | --- | --- |
| `main-design.md` | Main design | Resources, gateway tiers, progression, Null route, gear. Its Programs, Script language and Cyber combat sections are superseded by the next two tabs |
| `scripting-and-api.md` | Scripting and API | Lua runtime, events, hooks, kernel settings, the API set |
| `cyber-system.md` | Cyber system | **Add this one yourself** (see below) |

## Adding the Cyber system tab

Open the Claude Doc, go to the **Cyber system** tab, export it as Markdown (or copy all of its text),
and save it here as `cyber-system.md`.

## What the code already implements

- Gateway tiers, RAM/TOPS/Buffer/Leak/Heat, GC, out-of-memory, kernel panic, reboot
- Sandboxed Lua runtime (Cobalt), programs, kernels with settings and hooks, the `me.*` and `sys.*` APIs
- Control panel (G), program and kernel chips, locked datapack presets, the in-game code editor
