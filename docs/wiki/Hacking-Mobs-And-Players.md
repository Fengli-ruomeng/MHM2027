# Hacking Mobs and Players

*Beginner's Guide, page 9 of 13.*

Everything a wired skeleton does to you, you can do back, and more. This page shows what hacking actually gets you, then builds four attack programs you can fire from the quick wheel.

## What's in it for you

| Hack | What it does to a wired mob | Why you'd want it |
| --- | --- | --- |
| **Neutralize** | Slowed to a crawl, can't jump, up to 4 s | Walk away from a zombie, or hit it while it can't reach you |
| **Turn its head** | Faces up, down or backwards | It loses track of you |
| **Drop** (root) | Drops what it's holding | **A skeleton drops its bow** and can't shoot you |
| **Leak** | Junk memory, up to half its RAM per dose | Overfill it and it **withers**, losing health for up to half a minute |
| **Heat** | Up to 25 Heat a second | Past 70 it has the `thermal` flaw, so your next hack takes about 1 s |

Against **players** you also get: read their inventory, list their running programs, and with root, **stop their programs one by one** (their healing, their firewall helpers...) or make them drop their sword. Players can turn hacking between players off in the server config.

And wired mobs drop **Data Shards** when you help kill them: the currency for [levelling up](Why-Level-Up). Hacking makes those kills a lot easier.

## How a hack goes

1. **Pick a target.** `remote.target()` gives you the gateway under your crosshair (up to 16 + your level blocks away). `remote.scan()` lists up to 8 around you.
2. **Find its weak spot.** `remote.inspect(t)` takes about a second and returns its flaws. (It notices: its programs hear `on_scanned`.)
3. **Break in.** `remote.access(t, method)`: use its worst flaw for a fast way in, or `"force"` (the default) when it has none. It must be within 12 + your level blocks when you start. Your program waits until the attempt ends.
4. **Use the session.** You're in for **6 seconds**. Each action has its own cooldown.
5. **Close it** with `s.close()`. You can't hack the same target again for 10 seconds.

**Your TOPS is your strength.** You attack with the TOPS in your program's header, against the defender's. More TOPS than them makes you faster, fewer makes you slower, up to twice either way. That's why the programs below book 8.

## Freeze: the basic hack

Look at a wired mob and run it. It forces its way in (about 3 seconds), then pins the mob in place, facing the wrong way.

```lua
-- Needs LV0.
program { name = "Freeze", type = "transient", tops = 8 }

function on_run()
  local t = remote.target()
  if not t then
    sys.notify("Look at a gateway first")
    return
  end
  local s, why = remote.access(t)    -- "force": works on anyone, about 3 s
  if not s then
    sys.notify("No way in: " .. tostring(why))
    return
  end
  s.motor.neutralize(4)              -- slowed to a crawl, no jumping, 4 s
  s.motor.look("back")               -- and facing the wrong way
  s.close()
end
```

`type = "transient"` means it runs once and ends: right for something you fire on demand. `remote.access` returns either a session or `nil` and the reason it failed (`denied`, `cooldown`, `out_of_range`...); always check before using it.

## Disarm: take the skeleton's bow

Two new tricks: **inspect first** and use the worst flaw, then **escalate to root**, which unlocks the strongest actions.

```lua
-- Needs LV0.
program { name = "Disarm", type = "transient", tops = 8 }

local RANK = { minor = 1, major = 2, critical = 3 }

-- The target's worst flaw, or "force" if it has none.
local function best_method(info)
  local method, best = "force", 0
  for class, severity in pairs(info and info.flaws or {}) do
    if (RANK[severity] or 0) > best then
      method, best = class, RANK[severity]
    end
  end
  return method
end

function on_run()
  local t = remote.target()
  if not t then
    sys.notify("Look at a gateway first")
    return
  end
  local s, why = remote.access(t, best_method(remote.inspect(t)))
  if not s then
    sys.notify("No way in: " .. tostring(why))
    return
  end
  s.motor.neutralize(4)              -- hold it still while we go for root
  local root, reason = s.escalate()  -- a second attempt, half as long
  if root then
    s.core.drop()                    -- the bow hits the floor
    sys.notify("Disarmed " .. t.name)
  else
    sys.notify("No root: " .. tostring(reason))
  end
  s.close()
end
```

- `local function best_method(info)` is your own function: write it once, call it by name. It goes through the flaws and keeps the worst one. `RANK` turns "minor/major/critical" into numbers so they can be compared.
- `remote.inspect(t)` can return `nil` (the target walked off), so `info and info.flaws or {}` falls back to an empty table.
- Against a skeleton with a critical flaw, the whole thing takes about two seconds: one to inspect, half a second to get in, half a second for root.

## Meltdown: burn out its gateway

A slow kill. Fill a wired mob's memory past full and it withers until its gateway cleans up; heat it at the same time so it has the `thermal` flaw for your next hack.

```lua
-- Needs LV0.
program { name = "Meltdown", type = "transient", tops = 8 }

function on_run()
  local t = remote.target()
  if not t then
    return
  end
  local s, why = remote.access(t)
  if not s then
    sys.notify("No way in: " .. tostring(why))
    return
  end
  for i = 1, 3 do
    s.memory.leak(100000)     -- asks for far too much: each dose is capped at half its RAM
    s.thermal.heat(25)
    if i < 3 then
      sys.sleep(2.05)         -- leaking cools down for 2 s
    end
  end
  s.close()
  sys.notify(t.name .. " is melting down")
end
```

Three doses of half its RAM is one and a half times its memory, so it's out of memory, and withering, until it has cleaned up the extra half. The session lasts 6 seconds, which fits three doses 2 seconds apart.

## On the wheel

These three are made for the [quick wheel](Control-Panel#the-quick-wheel). In Edge:

```
wheel set 1 run Freeze
wheel set 2 run Disarm
wheel set 3 run Meltdown
```

Now hold **V**, look at a skeleton, press **2**, and watch it drop its bow.

## Against players

Players fight back: their firewall may deny you (costing them Buffer), resist with TOPS, or reboot you out. Use the target's flaws, bring more TOPS, and hit them when they're busy, such as when they're running hot.

Once you have root, this stops everything they're running, one program a second:

```lua
-- Needs LV0.
program { name = "Shutdown", type = "transient", tops = 10 }

function on_run()
  local t = remote.target()
  if not t or not t.is_player then
    return
  end
  local s = remote.access(t)
  if not s or not s.escalate() then
    sys.notify("Couldn't get root")
    return
  end
  for _, p in ipairs(s.ps()) do      -- everything they're running
    if s.time_left() < 0.5 then
      break                          -- the session is about to end
    end
    s.kill(p.name)
    sys.log("stopped " .. p.name)
    sys.sleep(1)                     -- kill cools down for 1 s
  end
  s.close()
end
```

All the rules, timings and failure reasons are in [Cyber Combat](Cyber-Combat).

**Next: [Making Your Own Programs](Making-Your-Own-Programs)**
