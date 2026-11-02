# Cool Programs

*Beginner's Guide, page 11 of 13.*

Most of what you've seen so far is a few lines long. This page shows what a program can do when it has memory, makes decisions and follows a plan. Each one uses something new: functions of your own, tables of tables, counters, functions stored in a list, and a program that switches between moods.

All of them run at LV0 except the last.

## Hunter: a bot that hunts on its own

Every 2 seconds it finds the nearest wired mob, inspects it (once; it remembers what it learned for 30 seconds), breaks in through its worst flaw and pins it. It leaves players alone, and it doesn't waste time on mobs that just got hit and are locked out.

```lua
-- Needs LV0.
program { name = "Hunter", type = "resident", tops = 8 }

local RANK = { minor = 1, major = 2, critical = 3 }
local clock = 0          -- counts up 2 every timer: roughly, seconds since it started
local known = {}         -- address -> { method = ..., at = clock }: what an inspection taught us
local resting = {}       -- address -> when we may try it again

local function best_method(info)
  local method, best = "force", 0
  for class, severity in pairs(info.flaws) do
    if (RANK[severity] or 0) > best then
      method, best = class, RANK[severity]
    end
  end
  return method
end

-- The nearest wired mob that isn't resting. Players are left alone.
local function pick()
  for _, t in ipairs(remote.scan(12)) do
    if not t.is_player and (resting[t.address] or 0) <= clock then
      return t
    end
  end
  return nil
end

function on_start()
  sys.every(2)
end

function on_timer()
  clock = clock + 2
  local t = pick()
  if not t then
    return
  end
  -- Inspect once, and trust what we learned for 30 s.
  local memo = known[t.address]
  if not memo or clock - memo.at > 30 then
    local info = remote.inspect(t)
    if not info then
      return
    end
    memo = { method = best_method(info), at = clock }
    known[t.address] = memo
  end
  local s, why = remote.access(t, memo.method)
  if s then
    s.motor.neutralize(4)
    s.thermal.heat(25)
    s.close()
    sys.log("pinned " .. t.name .. " via " .. memo.method)
  else
    sys.log(t.name .. ": " .. tostring(why))
  end
  resting[t.address] = clock + 10    -- after a session, it's locked out for 10 s anyway
end
```

**What's new:** a table of tables (`known[t.address] = { method = ..., at = ... }`) acts as the program's memory of every mob it has met. `pick` is a function of your own that returns a mob or `nil`. The clock is only approximate, because the program pauses while it inspects and hacks; for remembering things for "about 30 seconds", that's plenty.

## DPS: a damage meter

Shows how much damage you deal per second, averaged over the last 10 seconds, and tells you when you beat your record.

```lua
-- Needs LV0.
program { name = "DPS", type = "resident", tops = 2 }

local window = {}        -- damage dealt in each of the last 10 seconds
for i = 1, 10 do
  window[i] = 0
end
local slot = 1           -- which second we're filling now
local best = 0

function on_start()
  sys.every(1)
end

function on_hit_dealt(target, amount)
  window[slot] = window[slot] + amount
end

function on_timer()
  local total = 0
  for i = 1, 10 do
    total = total + window[i]
  end
  local dps = total / 10
  if dps > best and dps >= 1 then
    best = dps
    sys.notify("New best: " .. string.format("%.1f", best) .. " damage a second")
  end
  slot = slot % 10 + 1   -- the next second: 1, 2 ... 10, then back to 1
  window[slot] = 0
end
```

**What's new:** a **ring**: ten boxes reused round and round, so the program always has the last 10 seconds and never grows. `%` is "remainder after dividing", which is what wraps 10 back to 1. `string.format("%.1f", x)` writes a number with one decimal.

## Threats: who's coming for you

Keeps a file on everyone who inspects or attacks you. It warns when someone first reads your flaws, when one attacker keeps trying, and when two are at it at once.

```lua
-- Needs LV0.
program { name = "Threats", type = "resident", tops = 2 }

local clock = 0
local seen = {}          -- address -> { name, scans, attempts, last }

-- Find someone's file, or start one.
local function record(who)
  local entry = seen[who.address]
  if not entry then
    entry = { name = who.name, scans = 0, attempts = 0, last = 0 }
    seen[who.address] = entry
  end
  entry.last = clock
  return entry
end

function on_start()
  sys.every(1)
end

function on_timer()
  clock = clock + 1
  for address, entry in pairs(seen) do
    if clock - entry.last > 60 then
      seen[address] = nil            -- quiet for a minute: forget them
    end
  end
end

function on_scanned(by)
  local entry = record(by)
  entry.scans = entry.scans + 1
  if entry.scans == 1 then
    sys.notify(by.name .. " is reading your flaws")
  end
end

function on_access_attempt(req)
  local entry = record(req.from)
  entry.attempts = entry.attempts + 1
  local active = 0
  for _, other in pairs(seen) do
    if clock - other.last <= 5 and other.attempts > 0 then
      active = active + 1
    end
  end
  if active >= 2 then
    sys.notify(active .. " attackers at once. Reboot if it gets bad.")
  elseif entry.attempts >= 3 then
    sys.notify(entry.name .. " has tried " .. entry.attempts .. " times")
  end
end
```

**What's new:** `record` either finds or creates an entry, so the event functions stay short. Setting an entry to `nil` deletes it, which is how the program forgets.

## Combo: a hack that follows a script

The plan is data: a list of steps, each with a wait and an action. Change the list and you've changed the combo, without touching the code that runs it.

```lua
-- Needs LV0.
program { name = "Combo", type = "transient", tops = 10 }

-- Each step: how long to wait first, and what to do.
local PLAN = {
  { wait = 0,   act = function(s) s.motor.neutralize(4) end },
  { wait = 0,   act = function(s) s.thermal.heat(25) end },
  { wait = 1,   act = function(s) s.motor.look("back") end },
  { wait = 0.1, act = function(s) s.thermal.heat(25) end },
  { wait = 1,   act = function(s) s.motor.jump() end },
  { wait = 1,   act = function(s) s.thermal.heat(25) end },
}

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
  for _, step in ipairs(PLAN) do
    if step.wait > 0 then
      sys.sleep(step.wait)
    end
    step.act(s)
  end
  s.close()
  sys.notify("Combo done: " .. t.name .. " is overheating")
end
```

**What's new:** functions stored in a table (`act = function(s) ... end`) and called later (`step.act(s)`). The target ends at 75 Heat, past 70, so it has the `thermal` flaw: your next hack on it, through `"thermal"`, takes about a second.

## Bodyguard: a program with moods (LV1)

Bodyguard is **calm**, **fighting** or **retreating**, and does different things in each. When you're hit it goes to fighting and freezes anything with a gateway that hit you; when your health drops it switches to retreating and gets you out; after 8 quiet seconds it calms down.

```lua
-- Needs LV1 and a socket.
program { name = "Bodyguard", type = "resident", tops = 6 }

local mode = "calm"      -- "calm", "fighting" or "retreating"
local quiet = 0          -- seconds since the last hit

function on_start()
  sys.every(1)
end

function on_hit_taken(attacker, amount)
  quiet = 0
  local hp, max = me.health(), me.max_health()
  if hp < max * 0.35 and mode ~= "retreating" then
    mode = "retreating"
    me.speed_boost(0.5, 6)
    sys.notify("Retreat!")
  elseif mode == "calm" then
    mode = "fighting"
    sys.notify("Contact")
  end
  -- While fighting, anything with a gateway that hits us gets frozen.
  if mode == "fighting" and attacker and attacker.level and attacker.level >= 0 then
    local s = remote.access(attacker)
    if s then
      s.motor.neutralize(2)
      s.close()
    end
  end
end

function on_timer()
  quiet = quiet + 1
  if mode ~= "calm" and quiet >= 8 then
    mode = "calm"
    sys.notify("All clear")
  end
end
```

**What's new:** a **state machine**: one variable (`mode`) remembers which situation you're in, and every event checks it before acting. Most "smart" programs are built this way.

**Next: [Quality-of-Life Programs](Quality-Of-Life-Programs)**
