# Scripting

Programs are Lua. They don't loop: they **answer events** (you got hit, a timer fired, someone scanned you), do a little work, and wait for the next one. That keeps them cheap.

Every call your code can make is in the [API Reference](API-Reference).

## The header

Every program starts with a header:

```lua
program { name = "Guard", type = "resident", tops = 4 }
```

| Field | Meaning |
| --- | --- |
| `name` | What the panel and the log call it |
| `type` | `resident`, `transient` or `query` (below) |
| `tops` | TOPS it reserves while it runs. More TOPS means it can run more code per tick |
| `priority` | 1 to 9, default 5. Only matters under a `priority` kernel: higher runs first and is cut last |
| `overload` | `true` makes it an Overload program (see [Sockets and Overload](Sockets-and-Overload)) |

## Program types

| Type | Runs | Starts with |
| --- | --- | --- |
| `resident` | Until it's stopped | `on_start` |
| `transient` | Once, then ends when it has nothing left to do | `on_run` |
| `query` | Like transient, but it may not change your body | `on_run` |

Defences, monitors and anything that reacts to events are residents. Use transient and query programs for one-off actions such as "hack whatever I'm looking at".

## Events

Define a function with the event's name:

```lua
function on_hit_taken(attacker, amount)
  if amount >= 4 then
    me.speed_boost(0.3, 3)   -- run!
  end
end
```

The full list is in the [API Reference](API-Reference#program-events). The ones you'll use most:

- `on_timer`: fires every so often once you call `sys.every(seconds)`.
- `on_hit_taken`, `on_hit_dealt`, `on_kill`: the fight.
- `on_low_health(hp)`: below 30% of max health, or your own threshold set with `sys.low_health(hp)`.
- `on_scanned`, `on_access_attempt`, `on_session_opened`: someone is coming for you.
- `on_flaw_changed`, `on_program_crash`: your own state.

Each program queues up to 8 events. If it's busy for too long, new ones are dropped.

## Waiting

Some calls take time: `sys.sleep(seconds)`, `remote.inspect(target)`, `remote.access(target)` and `s.escalate()`. The program pauses at that line and carries on when the answer arrives. Other events wait in the queue until then.

## Other gateways

Other entities come to your code as **handles**. You get them from `remote.target()` (the gateway under your crosshair), `remote.scan()`, or from events such as `on_hit_taken(attacker)`. Read their fields:

```lua
local t = remote.target()
if t then
  sys.log(t.name .. " " .. t.address .. " LV" .. t.level .. ", " .. math.floor(t.distance) .. " m")
end
```

Fields: `address`, `kind`, `name`, `is_player`, `distance`, `level`. A handle goes stale when its entity dies or leaves range: then `distance` and `level` read nil, and calls that take it fail.

## Costs

The editor shows a program's cost while you write it:

- **RAM**: a base for the type, plus a little for each API it uses.
- **TOPS**: what you put in the header. It's reserved for as long as the program runs.
- **Level**: the highest API level it uses. You can write code above your level, but it won't run until you get there (except as Overload).

## When things go wrong

- A Lua error **crashes** the program: it stops, the log shows the line, and your other programs hear `on_program_crash(name)`.
- Calls with wrong arguments are errors. For example, `me.speed_boost()` with no amount crashes. Check the [API Reference](API-Reference) for each call's arguments.
- Calls on cooldown don't crash. They return `false`, so it's safe to retry them on a timer.

## Examples in your storage

| Script | What it shows |
| --- | --- |
| Monitor.lua | A resident program that warns when your health is low |
| Status.lua | A query that prints your resources to the log |
| Spinner.lua | An endless loop: it only slows itself, it can't freeze the server |
| Adrenaline.lua | A speed burst after a big hit (needs a socket) |
| Scanner.lua | Scans the nearest gateway and logs its level and flaws |
| Counterpunch.lua | After a big hit, breaks into the attacker and freezes them |
| Bulwark.lua | A kernel with `on_panic` and `on_low_ram` hooks |
| Gatekeeper.lua | A kernel that denies flaw guesses and resists forced attempts |

The README.txt in your storage is a one-page summary of all of this.
