# Quality-of-Life Programs

*Beginner's Guide, page 12 of 13.*

Not everyone wants to fight. Edge is also a way to build your own little tools inside Minecraft and watch them work. This page is for builders and explorers: small programs that make everyday survival easier, and teach a bit of programming along the way.

Every program here is complete. Most run at LV0.

## What Edge can and can't see

Your programs can read **your body** (health, hunger, position, what's right in front of you), **your gateway**, and **other gateways**. They can't read the wider world: blocks out of reach, the time of day, the weather or your inventory. Everything below works with what's there.

## Hunger Watch: eat before it matters

Your gateway stops cleaning up Leak when you're at 6 food or less (see [Reading Your Lens](Reading-Your-Lens#ram-memory-and-the-junk-called-leak)). This warns you at 8, once, and again only after you've eaten.

```lua
-- Needs LV0.
program { name = "Hunger", type = "resident", tops = 2 }

local warned = false

function on_start()
  sys.every(3)
end

function on_timer()
  local food = me.hunger()
  if food <= 8 and not warned then
    sys.notify("Eat soon: at 6 food your gateway stops cleaning up Leak")
    warned = true
  elseif food > 10 then
    warned = false
  end
end
```

**The trick:** `warned` remembers that it already told you, so you get one card, not one every 3 seconds. The gap between 8 and 10 stops it flickering back and forth.

## Compass: which way is home?

Put it on the quick wheel (`wheel set 5 run Compass`), and one key tells you how far home is and which way.

```lua
-- Needs LV0.
program { name = "Compass", type = "query", tops = 2 }

-- Your base. Press F3 at home to read your x and z.
local HOME_X, HOME_Z = 120, -340

function on_run()
  local x, y, z = me.pos()
  local dx, dz = HOME_X - x, HOME_Z - z
  local distance = math.floor(math.sqrt(dx * dx + dz * dz))
  if distance < 5 then
    sys.notify("You're home")
    return
  end
  -- In Minecraft, north is -z and east is +x.
  local ns, ew = "", ""
  if dz < -math.abs(dx) / 2 then ns = "north" elseif dz > math.abs(dx) / 2 then ns = "south" end
  if dx > math.abs(dz) / 2 then ew = "east" elseif dx < -math.abs(dz) / 2 then ew = "west" end
  local direction = ns .. ((ns ~= "" and ew ~= "") and "-" or "") .. ew
  sys.notify("Home: " .. distance .. " m " .. direction)
end
```

**The trick:** distance is Pythagoras (`sqrt(dx² + dz²)`). For the direction, it only says "north" if you're mostly going north; when both matter, you get "north-east". Values in CAPITALS at the top are a convention for "settings you edit".

## Look: what's that?

What's the block or creature right in front of you, and does it have a gateway?

```lua
-- Needs LV0.
program { name = "Look", type = "query", tops = 2 }

function on_run()
  local thing = me.looking_at()
  if not thing then
    sys.notify("Nothing in reach")
  elseif thing.type == "block" then
    sys.notify(thing.id .. " at " .. thing.x .. " " .. thing.y .. " " .. thing.z)
  else
    local level = thing.entity.level
    if level and level >= 0 then
      sys.notify(thing.id .. " with an LV" .. level .. " gateway")
    else
      sys.notify(thing.id)
    end
  end
end
```

## Tally: keep score

Counts everything you kill, by kind, and every two minutes writes your top three into its window.

```lua
-- Needs LV0.
program { name = "Tally", type = "resident", tops = 2 }

local kills = {}         -- name -> how many

function on_start()
  sys.every(120)
end

function on_kill(target)
  kills[target.name] = (kills[target.name] or 0) + 1
end

function on_timer()
  local list = {}
  for name, count in pairs(kills) do
    list[#list + 1] = { name = name, count = count }
  end
  table.sort(list, function(a, b) return a.count > b.count end)
  for i = 1, math.min(3, #list) do
    sys.log(i .. ". " .. list[i].name .. " x" .. list[i].count)
  end
end
```

**The trick:** a table can't be sorted by its keys, so it's copied into a list first. `table.sort` takes a function that says which of two entries comes first: here, the bigger count.

## Rewind: where was I safe?

Remembers where you've been over the last minute. When your health drops below 4 hearts, it tells you where you were a minute ago, before the trouble started.

```lua
-- Needs LV0.
program { name = "Rewind", type = "resident", tops = 2 }

local trail = {}         -- your position every 5 s, oldest first
local KEEP = 12          -- 12 points, 5 s apart: one minute

function on_start()
  sys.every(5)
  sys.low_health(8)
end

function on_timer()
  local x, y, z = me.pos()
  trail[#trail + 1] = { x = math.floor(x), y = math.floor(y), z = math.floor(z) }
  if #trail > KEEP then
    table.remove(trail, 1)           -- forget the oldest
  end
end

function on_low_health(hp)
  local safe = trail[1]
  if safe then
    sys.notify("A minute ago you were at " .. safe.x .. " " .. safe.y .. " " .. safe.z)
  end
end
```

## Feather: catch a fall (LV2)

Checks four times a second whether you're dropping fast, and turns off fall damage when you are. It catches falls of about five blocks or more. For no fall damage at all, [Mountain Goat](Why-Level-Up#mountain-goat-lv2) keeps it on all the time.

```lua
-- Needs LV2 and a socket.
program { name = "Feather", type = "resident", tops = 2 }

local lastY = nil

function on_start()
  sys.every(0.25)
end

function on_timer()
  local x, y, z = me.pos()
  if lastY and lastY - y > 1.5 then    -- 1.5 blocks down in a quarter second: falling
    if me.fall_guard(3) then
      sys.log("caught a fall at y " .. math.floor(y))
    end
  end
  lastY = y
end
```

## Ideas to try yourself

- **Mining partner:** a resident that, every 30 seconds, reminds you how deep you are (`me.pos()`) and how many hearts you have.
- **Status on a key:** a query for the wheel that shows health, hunger, free TOPS and Buffer on one card.
- **Auto-bandage** (LV3): a gentler Combat Medic that heals 2 whenever you're below half and your hunger is above 14, so it never starves you.
- **Kill streak:** count kills since you last got hit (`on_kill` and `on_hit_taken`), and celebrate every 5.

**Next: [FAQ](FAQ)**
