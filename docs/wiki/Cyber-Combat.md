# Cyber Combat

Hacking happens in three steps: **find** a target, **break in** with an access attempt, then use the **session** you opened. The defender has an answer at every step. Nothing is instant, and nothing works every time.

Gateway mobs follow exactly the same rules as players, in both directions. Null players are immune to all of it.

## 1. Find and inspect

- `remote.target()`: the gateway under your crosshair, up to **16 + your level** blocks away.
- `remote.scan(radius)`: up to 8 gateways around you, nearest first.
- `remote.inspect(t)`: waits about **1 second**, then tells you the target's name, level and **flaws**. Your kernel's `rate_limit` makes this faster or slower. The target's programs hear `on_scanned`, so they know you looked.

The [Scanner](Items#kuyoz-mech) item does the same inspect by hand, without a program.

## 2. Break in: access attempts

```lua
local s, why = remote.access(t, method)
```

The target must be within **12 + your level** blocks when you start. After that, range doesn't matter. Your program pauses until the attempt ends.

### How long it takes

The **method** decides the base time:

| Method | Base time |
| --- | --- |
| `"force"` (the default) | About 3 s, against anyone |
| A flaw the target has: minor | About 2 s |
| A flaw the target has: major | About 1 s |
| A flaw the target has: critical | About 0.5 s |
| A flaw the target **doesn't** have | Fails at once, and warns the target |

Then **TOPS against TOPS**:

- You commit the TOPS your program reserved. The defender commits their kernel's reserve, plus anything they resist with.
- More TOPS than the defender makes it faster, fewer makes it slower, but never more than 2× either way.
- Your kernel's `verify` setting speeds up your own attempts (see [Kernels and Flaws](Kernels-and-Flaws)).
- No attempt takes less than half a second.

So: inspect first, then pick the target's worst flaw. Force always works, but it's slow.

### When it fails

`remote.access` returns `nil` and a reason:

| Reason | Meaning |
| --- | --- |
| `out_of_range` | Too far away, or not a valid target |
| `no_flaw` | You guessed a flaw they don't have. They were warned |
| `denied` | Their kernel denied you. You can't retry them for about 2 s |
| `cooldown` | You're locked out of them: a session ended less than 10 s ago, or they denied you less than 2 s ago |
| `rebooting` | They're rebooting, and a rebooting gateway can't be hacked |
| `gone` | You or they died, or left |

## Defending

When someone tries to break in:

- **You see it**: "Access attempt from 10.x.x.x (method)" above your hotbar.
- **Your programs hear it**: `on_access_attempt(req)` with `req.from`, `req.method` and `req.tops`. They can react, but they can't decide.
- **Your kernel decides**, through `on_access_request(req)`:

| Answer | Effect | Cost |
| --- | --- | --- |
| `allow()` (default) | The attempt runs at normal speed | Nothing |
| `deny()` | Blocks it; they can't retry for 2 s | **256 MB** of Buffer. If you can't pay, it's allowed instead |
| `delay(seconds)` | Adds up to 2 s, time for your programs to act | Nothing |
| `resist(tops)` | Commits extra TOPS so the attempt runs slower | Those TOPS, for the length of the attempt, plus **1.5 Heat each** |

Denying forever runs out of Buffer. Resisting hard starves your programs and heats you into the `thermal` flaw. There's no free defence.

Other defences:

- **Rebooting** ends every attempt and session on you at once, but stops all your programs and leaves you debuffed for 3 s.
- **Hacking them back**: they defend with their TOPS too.
- `sys.end_session(who)` closes a session already open on you, for **512 MB** of Buffer.

## 3. The session

A session lasts **6 seconds**. Every action has a cap and a cooldown, the same for everyone:

| Action | Does | Cooldown |
| --- | --- | --- |
| `s.motor.neutralize(seconds)` | Heavy slowness and no jumping, up to 4 s | 4 s |
| `s.motor.look(dir)` | Turns their view: `"up"`, `"down"`, `"left"`, `"right"` or `"back"` | 1 s |
| `s.motor.jump()` | Makes them jump | 1 s |
| `s.memory.leak(mb)` | Adds Leak, up to half their RAM | 2 s |
| `s.thermal.heat(amount)` | Adds up to 25 Heat | 1 s |
| `s.storage.peek()` | Lists their inventory | |
| `s.ps()` | Lists their running programs | |
| `s.kill(name)` | **Root only.** Stops one of their programs | 1 s |
| `s.core.drop()` | **Root only.** Makes them drop what they're holding | 2 s |
| `s.escalate()` | Tries for root (below) | |
| `s.time_left()`, `s.is_root()`, `s.close()`, `s.target` | Session info, and ending it | |

An action that can't run returns `false` and why: `"closed"`, `"needs_root"` or `"cooldown"`.

**Root**: `s.escalate()` is a second access attempt under the same rules, at half the time. If it works, root actions unlock. The target sees "is escalating to root".

When a session ends, you can't access the same target again for **10 seconds**.

## A full example

```lua
program { name = "Freeze", type = "transient", tops = 6 }

local RANK = { minor = 1, major = 2, critical = 3 }

function on_run()
  local t = remote.target()
  if not t then sys.log("no target") return end

  -- Inspect, then use their worst flaw if they have one.
  local info = remote.inspect(t)
  local method, best = "force", 0
  for class, severity in pairs(info and info.flaws or {}) do
    if RANK[severity] > best then method, best = class, RANK[severity] end
  end

  local s, why = remote.access(t, method)
  if not s then sys.log("failed: " .. why) return end
  s.motor.neutralize(4)
  s.thermal.heat(25)
  s.close()
end
```

## Between players

Players can hack each other unless the server turns it off (`playerTargets` in the config, see [Server Admins](Server-Admins)).
