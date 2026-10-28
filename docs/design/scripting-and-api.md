<!-- Exported from the Claude Doc 'MoreHarderMinecraft: Edge - Design Draft v2'. The Claude Doc is the source of truth. -->

# Scripting and API

Players write programs and kernels in sandboxed Lua. The mod measures TOPS while code runs and works out RAM from the APIs the code uses. This is the first, deliberately small API set; more will be added over time.

## Language and editing

Scripts are Lua, because it is easy to learn, closes blocks with `end` (so there is no indentation to type in a Minecraft text box), and is well supported in VS Code. ComputerCraft: Tweaked has run player Lua in Minecraft for years with its own sandboxed runtime (Cobalt), and is the reference to study.

- **Sandboxed.** Only Lua's safe libraries (`math`, `string`, `table`) and the mod's APIs are available. No file access, no loading other code.
- **Files first.** Scripts are ordinary `.lua` files in a folder. Players edit them in VS Code; the mod reloads a file when it is saved, and the in-game terminal shows its RAM, TOPS, errors and flaws.
- **Autocomplete.** A definition file generated from the API registry gives VS Code's Lua extension autocomplete and parameter hints for `me`, `sys` and `remote`.
- **In-game editor later.** A basic editor with line numbers and a live cost panel comes after the first release; syntax colouring and autocomplete in-game can wait longer.
- **Multiplayer.** Installing a program sends the file to the server, which checks and runs it. The server never trusts the player's computer.

## Runtime resources

TOPS is a speed limit measured while code runs, not a price computed in advance. This allows real loops without risking server performance.

- **1 TOPS = 20 Lua instructions per tick** (a server setting, so admins can tune load). LV0's 25 TOPS is 500 instructions per tick; LV10's 300 is 6,000.
- **Each program reserves a budget** in its header (`tops = 4`). Reserved TOPS are held while it runs, and at least 1 TOPS must stay free.
- **API calls count.** Each API's `steps` value is charged against the budget, so a scan costs more than reading your own health.
- **Over budget means a pause, not a crash.** The program stops for the rest of the tick and continues next tick. A bad loop only slows itself.
- **Code RAM** is known on install: a base size by program type plus the footprint of every API the code uses.
- **Data RAM** is measured while running: whatever the program keeps between events. Past its limit, the program is killed and leaves Leak behind.
- **`sys.sleep(seconds)`** pauses a program cheaply, so transient programs can run timed combos.
- **Mob kernels** are presets implemented natively in Java behind the same API, so only player code runs in the Lua runtime.

Players see a live `top`-style view in the terminal, which is also how they tune reservations:

| Program | Type | RAM | TOPS reserved | TOPS used | State |
| --- | --- | --- | --- | --- | --- |
| Adrenaline | resident | 6 MB | 4 | 0.2 avg, 3 peak | idle |
| Sweep | transient | 12 MB | 10 | 10 | paused (over budget) |

## File structure

Each file starts with a header table, then defines functions named after the events it handles. Everything else is plain Lua.

```lua
program { name = "Adrenaline", type = "resident", tops = 4 }

function on_hit_taken(attacker, amount)
  if amount >= 4 then
    me.speed_boost(0.3, 2)   -- 30% faster for 2 seconds
  end
end
```

```lua
kernel { name = "Baseline", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.rate > 4 then
    return deny()
  end
  return allow()
end
```

- `program { … }` takes `name`, `type` (`resident`, `transient` or `query`) and `tops`.
- `kernel { … }` takes `name` and the kernel settings below.
- Times are in seconds. Values that last between events live in ordinary Lua variables outside functions, and count as data RAM.
- Most mistakes, such as an API above your gateway level or a query trying to change your body, are reported on install. At runtime, calls fail only for in-game reasons (no free TOPS, target out of range) and return `false` or `nil`.

## Design rules

The API decides how the game feels, so these rules matter more than any single function.

1. **Small verbs that combine.** There is no `win()` button; strong plays come from chaining small actions.
2. **Reading is cheap, changing is expensive.** Anything that changes a body has a cost, a cooldown and a cap.
3. **Events are the fun.** Most cleverness is in reacting, so there are plenty of events.
4. **Levels raise limits, not power.** Self-enhancement unlocks by level; actions on others exist from LV0 with the same caps for everyone.
5. **Information is a weapon.** Reads return data that general programs branch on.

**Naming.** Three namespaces: `me` for your own body, `sys` for your terminal, `remote` for anything aimed at another entity. `me` is used instead of `self`, which Lua programmers already use inside methods. `link` is reserved for a later friendly feature, such as teammates sharing programs.

## Events and hooks

### Program events

| Event | Fires when |
| --- | --- |
| `on_start()`, `on_stop()` | The program starts or stops |
| `on_run(target)` | A transient or query program is fired; target comes from the crosshair or the scan list |
| `on_timer()` | Every interval set with `sys.every(seconds)`, at least 0.25 s |
| `on_hit_taken(attacker, amount)` | You take damage |
| `on_hit_dealt(target, amount)` | You deal damage |
| `on_low_health(hp)` | Health drops below a threshold you set |
| `on_kill(target)` | You kill something |
| `on_scanned(by)` | Someone scans you |
| `on_session_opened(who)` | Someone opens a session on your terminal |
| `on_program_crash(name)` | One of your programs crashes |

### Kernel hooks

Hooks work like cancellable events: the game stops at that moment, asks the kernel's Lua function what should happen, and uses the answer. Program events only tell programs that something happened. If a kernel doesn't define a hook, its default runs.

| Hook | Fires when | Can do | Default if not defined |
| --- | --- | --- | --- |
| `on_access_request(req)` | Something tries to open a session on you | `allow()`, `deny()`, `delay(seconds)`, `resist(tops)` | The attempt runs at normal speed |
| `on_session_opened(who)` | Someone gets in | Raise an alarm, reboot, or start a defence program | Nothing |
| `on_low_ram(free)` | Free RAM runs low | Stop programs in the order you choose | The newest program stops |
| `on_panic(reason)` | The kernel crashes | Choose what restarts after the reboot | Nothing restarts |

Hook points are how many of these hooks a kernel may take over; the rest run their defaults.

| Gateway | Hook points |
| --- | --- |
| LV0–2 | 2 |
| LV3–5 | 3 |
| LV6 and above | 4 (all) |

- **Hooks must answer immediately.** They can't call `sys.sleep`, and each call has a small instruction cap. A hook that runs over gets its default.
- **Hook work comes from a kernel reserve** of 2 TOPS that is always held, so a busy fight never starves the kernel.
- `on_program_start` is left out of the first release. It only matters once intruders can leave programs behind.

### Kernel settings

Settings go in the `kernel { … }` header. They are where the flaw trade-offs come from.

| Setting | Options | Trade-off |
| --- | --- | --- |
| `verify` | `full`, `fast`, `skip` | Faster outgoing access, weaker checks on incoming (MEMORY flaw) |
| `gc` | `lazy`, `normal`, `aggressive` | Faster Leak clearing costs more TOPS |
| `schedule` | `fair`, `priority` | `priority` lets you rank programs, but starved programs can crash |
| `rate_limit` | Access requests per second | Lower is safer, but slows your own outgoing scans |

## APIs

### `me`: your own body

Every write needs a standard slot, has a cooldown and has a cap. More enhancements unlock at higher levels over time.

| API | Does | Level |
| --- | --- | --- |
| `me.health()`, `me.max_health()`, `me.hunger()`, `me.pos()`, `me.looking_at()` | Read your state | 0 |
| `me.speed_boost(amount, seconds)` | Faster movement | 1 |
| `me.jump_boost(amount, seconds)`, `me.fall_guard(seconds)` | Jump higher; ignore fall damage | 2 |
| `me.regen_burst(amount)` | Heal now, paid in hunger | 3 |
| `me.max_health_boost(amount, seconds)` | Temporary extra hearts | 5 |
| `me.damage_boost(amount, seconds)`, `me.knockback_resist(seconds)` | Hit harder; stand firm | 6 |

### `sys`: your terminal

| API | Does |
| --- | --- |
| `sys.ram()`, `sys.tops()`, `sys.heat()`, `sys.leak()` | Read your resources |
| `sys.programs()` | List running programs |
| `sys.start(name, target)`, `sys.stop(name)` | Start or stop a program |
| `sys.sleep(seconds)` | Pause this program |
| `sys.every(seconds)` | Set the `on_timer` interval |
| `sys.notify(text)` | Show a message on your HUD |
| `sys.log(text)` | Write to the terminal log |

### `remote`: other entities

| API | Does |
| --- | --- |
| `remote.target()` | The entity under your crosshair |
| `remote.scan(radius)` | Nearby gateways, up to 8 |
| `remote.inspect(e)` | A target's gateway level and kernel profile (flaw classes) |
| `remote.access(e)` | Try to open a session; returns it or `nil`. **See Access calls below****.** |

Entity handles have readable fields: `e.address`, `e.kind`, `e.level`, `e.distance`, `e.is_player`. A handle goes stale when its entity leaves range; calls on it then return `nil`.

### Session: actions once you are in

The caps are the same at every level.

| API | Does |
| --- | --- |
| `s.motor.neutralize(seconds)` | Heavy slowness and no jumping, at most 4 s |
| `s.motor.look(dir)`, `s.motor.jump()` | One forced movement |
| `s.memory.leak(mb)` | Add Leak, at most 50% of the target's RAM |
| `s.thermal.heat(amount)` | Raise the target's Heat |
| `s.storage.peek()` | See the target's inventory |
| `s.ps()` | See the target's running programs |
| `s.kill(name)`, `s.core.drop()` | Kill one program; force-drop the held item (root only) |
| `s.close()` | End the session |

### Access calls

The rules behind these calls are in the Cyber system tab, under Access and sessions.

| Call | Where | Does |
| --- | --- | --- |
| `remote.access(e, method)` | Programs | Starts an attempt. `method` is `"force"` (default) or a flaw class. The program pauses until it finishes, then gets the session, or `nil` and a reason: `"denied"`, `"no_flaw"`, `"out_of_range"`, `"cooldown"` |
| `allow()`, `deny()`, `delay(seconds)`, `resist(tops)` | `on_access_request` | The kernel's decision on an incoming attempt |
| `req.from`, `req.method`, `req.tops` | `on_access_request`, `on_access_attempt` | Who is trying, how, and with how much TOPS |
| `on_access_attempt(req)` | Program event | Lets a program react to an attempt; it can't decide it |
| `sys.buffer()` | Programs, kernel | Read your Buffer |
| `sys.end_session(who)` | Programs, kernel | Close a session on you; costs Buffer |
| `s.escalate()` | Session | Try for root; a shorter attempt under the same rules |
| `s.time_left()` | Session | Seconds left in the session |

### Flaw calls

| Call | Where | Does |
| --- | --- | --- |
| `sys.flaws()` | Programs, kernel | Your current flaws: class, severity and cause |
| `on_flaw_changed(class, severity)` | Program event | Fires when a flaw appears, changes or disappears |
| `remote.inspect(e).flaws` | Programs | A target's current flaws, exactly as the target sees them |

### Helpers

Lua's `math`, `string` and `table` libraries, plus `chance(percent)`, which returns `true` that percent of the time.

## API schema

Every API is one entry in a registry. Game code implements what it does; the entry describes everything else. The install checks, cost calculation and VS Code definition file all read the same entry, and the numbers can live in datapack files for balancing.

```json
{
  "id": "me.speed_boost",
  "layer": "program",
  "contexts": ["resident", "transient"],
  "params": {
    "amount":  { "type": "number", "min": 0.05, "max": 0.5 },
    "seconds": { "type": "number", "min": 1, "max": 10 }
  },
  "returns": "bool",
  "min_level": 1,
  "steps": 2,
  "ram_mb": 4,
  "cooldown": 8,
  "needs_slot": true,
  "tags": ["body", "write"]
}
```

| Field | Meaning |
| --- | --- |
| `id` | Namespace and name |
| `layer` | Usable from `kernel`, `program`, or both |
| `contexts` | Which program types or kernel hooks may call it |
| `params`, `returns` | Types and allowed ranges |
| `min_level` | Gateway level needed; preset code may skip it |
| `steps` | Charged against the TOPS budget per call |
| `ram_mb` | Added to code RAM when a script uses this API |
| `cooldown` | Seconds between uses |
| `needs_slot` | Changes your body, so needs a standard slot |
| `tags` | Read or write, body or target; later also flaw effects |

## Full example

```lua
program { name = "Counterpunch", type = "resident", tops = 6 }

function on_hit_taken(attacker, amount)
  if amount < 4 or not attacker then return end
  me.speed_boost(0.3, 2)
  local s = remote.access(attacker)
  if s then
    s.motor.neutralize(1.5)
    sys.sleep(0.5)
    s.motor.look("up")
    s.close()
  end
end
```

After a big hit, it speeds you away, tries to get into the attacker, freezes them briefly, then makes them look at the sky. Every step is a small, general verb.
