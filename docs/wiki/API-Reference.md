<!-- Generated from the mod's code by ApiReferenceTest. Don't edit by hand: run the tests with UPDATE_WIKI=1. -->

# API Reference

Every call, event and hook your scripts can use, taken from the mod's code.

- **Level**: the gateway level you need.
- **Kernel**: kernel hooks may call it too. Programs always can.
- **Socket**: it changes your body, so the program has to run from a socket (see [Sockets and Overload](Sockets-and-Overload)).
- **Cooldown**: seconds between uses, shared by all your programs.
- **RAM**: added to a program's code RAM when it uses the call.

## `me`: your own body

| Call | Level | Kernel | Socket | Cooldown | RAM | What it does |
| --- | --- | --- | --- | --- | --- | --- |
| `me.health()` | LV0 |  |  |  | 1 MB | Your current health. |
| `me.max_health()` | LV0 |  |  |  | 1 MB | Your maximum health. |
| `me.hunger()` | LV0 |  |  |  | 1 MB | Your food level, 0 to 20. |
| `me.pos()` | LV0 |  |  |  | 1 MB | Your position: x, y, z. |
| `me.looking_at()` | LV0 |  |  |  | 2 MB | What's under your crosshair within reach: `{type = "block", id, x, y, z}` or `{type = "entity", id, entity}`, or nil. |
| `me.speed_boost(amount, seconds)` | LV1 |  | yes | 8 s | 4 MB | Faster movement: amount 0.05 to 0.5, for 1 to 10 seconds. |
| `me.jump_boost(amount, seconds)` | LV2 |  | yes | 8 s | 4 MB | Higher jumps: amount 0.05 to 0.5, for 1 to 10 seconds. |
| `me.fall_guard(seconds)` | LV2 |  | yes | 10 s | 4 MB | No fall damage for 1 to 10 seconds. |
| `me.regen_burst(amount)` | LV3 |  | yes | 10 s | 6 MB | Heal 1 to 6 health now, paid in the same amount of hunger. |
| `me.max_health_boost(amount, seconds)` | LV5 |  | yes | 30 s | 8 MB | 1 to 10 extra max health for 1 to 30 seconds. |
| `me.damage_boost(amount, seconds)` | LV6 |  | yes | 12 s | 8 MB | Hit harder: amount 0.05 to 0.5, for 1 to 10 seconds. |
| `me.knockback_resist(seconds)` | LV6 |  | yes | 12 s | 6 MB | No knockback for 1 to 10 seconds. |

## `sys`: your terminal

| Call | Level | Kernel | Socket | Cooldown | RAM | What it does |
| --- | --- | --- | --- | --- | --- | --- |
| `sys.ram()` | LV0 | yes |  |  | 0 MB | Free and total RAM, in MB. |
| `sys.tops()` | LV0 | yes |  |  | 0 MB | Free and total TOPS. |
| `sys.heat()` | LV0 | yes |  |  | 0 MB | Your Heat, 0 to 100. |
| `sys.leak()` | LV0 | yes |  |  | 0 MB | Your Leak, in MB. |
| `sys.buffer()` | LV0 | yes |  |  | 0 MB | Your Buffer and its maximum, in MB. |
| `sys.programs()` | LV0 | yes |  |  | 1 MB | Your running programs: a list of `{name, type, ram, tops, state}`. |
| `sys.start(name)` | LV0 | yes |  |  | 1 MB | Start a program from storage. Returns true, or false and the reason. |
| `sys.stop(name)` | LV0 | yes |  |  | 1 MB | Stop a running program by name. |
| `sys.sleep(seconds)` | LV0 |  |  |  | 0 MB | Pause this program for 0.05 to 60 seconds. Not in kernel hooks. |
| `sys.every(seconds)` | LV0 |  |  |  | 1 MB | Fire `on_timer` every so many seconds (at least 0.25). 0 turns it off. |
| `sys.notify(text)` | LV0 | yes |  |  | 1 MB | Show a short message above your hotbar. |
| `sys.log(text)` | LV0 | yes |  |  | 0 MB | Write a line to the terminal log (the panel's Log tab). |
| `sys.flaws()` | LV0 | yes |  |  | 0 MB | Your active flaws: a list of `{class, severity, cause}`. |
| `sys.low_health(hp)` | LV0 |  |  |  | 1 MB | Set the health below which `on_low_health` fires. nil goes back to 30% of max. |
| `sys.end_session(who)` | LV0 | yes |  |  | 1 MB | Close a session someone holds on you. Costs Buffer. |
| `sys.reboot()` | LV0 | yes |  |  | 1 MB | Reboot: every program stops and every session on you ends, with a short debuff. |

## `remote`: other gateways

| Call | Level | Kernel | Socket | Cooldown | RAM | What it does |
| --- | --- | --- | --- | --- | --- | --- |
| `remote.target()` | LV0 |  |  |  | 2 MB | The gateway under your crosshair, within your scan range, as a handle; or nil. |
| `remote.scan(radius)` | LV0 |  |  |  | 4 MB | Gateways around you, nearest first, up to 8 handles. The radius is capped at your scan range. |
| `remote.inspect(target)` | LV0 |  |  |  | 4 MB | Waits about 1 s (faster with a high rate_limit), then returns the target's address, name, level and flaws. They hear `on_scanned`. |
| `remote.access(target, method)` | LV0 |  |  |  | 6 MB | Waits for an access attempt, then returns a session, or nil and a reason. See [Cyber Combat](Cyber-Combat). |

## Program events

Define a function with the event's name and the program runs it when the event happens.

| Event | Fires when |
| --- | --- |
| `on_start` | A resident program starts. |
| `on_stop` | The program is stopped. It gets one short slice to tidy up. |
| `on_run` | A transient or query program is run. |
| `on_timer` | The interval set with `sys.every` passes. |
| `on_hit_taken` | You take damage. Gets `(attacker, amount)`. |
| `on_hit_dealt` | You deal damage. Gets `(target, amount)`. |
| `on_kill` | You kill something. Gets `(target)`. |
| `on_flaw_changed` | A flaw appears, changes or goes away. Gets `(class, severity)`; severity is "none" when it goes. |
| `on_scanned` | Someone finishes inspecting you. Gets `(by)`. |
| `on_access_attempt` | Someone starts an access attempt on you. Gets `(req)`; programs can't decide it, the kernel does. |
| `on_session_opened` | Someone got in. Gets `(who)`. |
| `on_low_health` | Your health drops below the `sys.low_health` threshold. Gets `(hp)`. Fires once per drop. |
| `on_program_crash` | Another of your programs crashed. Gets `(name)`. |

## Kernel hooks

A kernel may take over as many hooks as your gateway has hook points; the rest keep their default. Hooks must answer at once: they can't sleep, and one that runs too long gets its default.

| Hook | When, and what it can do |
| --- | --- |
| `on_access_request` | Something tries to open a session on you. Gets `(req)` with `req.from`, `req.method`, `req.tops`, `req.escalate`. Return `allow()`, `deny()` (costs Buffer), `delay(seconds)` (up to 2) or `resist(tops)` (costs TOPS and Heat). Default: allow. |
| `on_session_opened` | Someone got in. Gets `(who)`. Raise the alarm, reboot, start a defence. Default: nothing. |
| `on_low_ram` | Free RAM ran out. Gets `(free)`. Stop programs in the order you choose. Default: the newest program stops. |
| `on_panic` | The kernel panicked and rebooted. Gets `(reason)`. Choose what to start again. Default: nothing restarts. |

## Helpers

| Call | What it does |
| --- | --- |
| `print(...)` | Same as `sys.log`. |
| `chance(percent)` | true that percent of the time. |
| `math`, `string`, `table` | Lua's standard libraries. |
