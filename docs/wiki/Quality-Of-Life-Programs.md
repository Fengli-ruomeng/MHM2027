# Quality-of-Life Programs

*Beginner's Guide, page 12 of 13.*

Not everyone wants to fight. Edge is also a way to build your own little tools inside Minecraft and watch them work. This page is for builders and explorers: small programs that make everyday survival easier, and teach a bit of programming along the way.

Every program here is complete. Most run at LV0.

## What Edge can and can't see

Your programs can read **your body** (health, hunger, position, breath, XP, effects, what you carry, what's right in front of you), **your gateway**, **other gateways**, and **the world around you**: the time, the day, the weather, the light, the biome, nearby blocks and nearby creatures. `man world` lists the world calls.

It's all read-only. Edge can't place or break a block, change the weather or move a mob. And a few limits keep it fair:

- `world.block` (LV1) reaches 4 blocks from your feet, and only sees blocks you could see from close by. A block with rock on every side reads `"hidden"`, so it can't find ore buried in solid stone.
- `world.mobs` and `remote.scan` reach as far as your scan range (16 blocks, plus one per level) and list 8 at most. `world.mobs` lists creatures without a gateway; `remote.scan` lists the ones with one.
- The time and the day are always the Overworld's, even in the Nether.
- A kernel may read the time, the day and the weather, and nothing else around you.

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

## Dusk: get home before dark

Warns you a minute before sunset, and tells you when you've made it through the night.

```lua
-- Needs LV0.
program { name = "Dusk", type = "resident", tops = 2 }

local warned = false     -- already warned today?
local wasNight = false

function on_start()
  sys.every(10)
end

function on_timer()
  if world.dimension() ~= "minecraft:overworld" then
    return               -- no sunset in the Nether or the End
  end
  local time, night = world.time()
  if time >= 11800 and time < 13000 and not warned then
    sys.notify("Sunset in about a minute. Head home?")
    warned = true
  elseif time < 11800 then
    warned = false
  end
  if wasNight and not night then
    sys.notify("Morning of day " .. world.day() .. ". You made it")
  end
  wasNight = night
end
```

**The trick:** a day is 24000 ticks, and 20 ticks make a second, so a minute is 1200 ticks: sunset at 13000 means warning from 11800. The timer checks every 10 seconds (200 ticks), so it can't miss that minute.

## Spot: can monsters spawn here?

A wheel key for builders and cave explorers: is the spot you're standing on lit well enough?

```lua
-- Needs LV0.
program { name = "Spot", type = "query", tops = 2 }

function on_run()
  local light, fromBlocks = world.light()
  -- "minecraft:dark_forest" becomes "dark forest"
  local biome = world.biome():gsub("minecraft:", ""):gsub("_", " ")
  local verdict
  if fromBlocks > 0 then
    verdict = "lit, nothing spawns here"
  elseif light <= 7 then
    verdict = "dark, monsters can spawn here"
  else
    verdict = "no torchlight, monsters spawn here at night"
  end
  sys.notify(biome .. ", " .. world.weather() .. ". Light " .. light .. ": " .. verdict)
end
```

**The trick:** `world.light()` gives two numbers: all the light, and the part that comes from blocks such as torches. In the Overworld a monster can only spawn where that second number is 0, and only when it's dark. `gsub` swaps one piece of text for another; calling it twice cleans up the biome's name.

## Pack: ready to go mining?

Checks your inventory against a list before you head out, and tells you what's missing.

```lua
-- Needs LV0.
program { name = "Pack", type = "query", tops = 4 }

-- What you want on you, and how many.
local NEED = {
  ["minecraft:torch"] = 32,
  ["minecraft:bread"] = 8,
  ["minecraft:water_bucket"] = 1,
}

function on_run()
  local have = {}        -- item -> how many, across all your slots
  local free = 36        -- slots 0 to 35 are your bag; the rest is armour and your off hand
  for _, stack in ipairs(me.inventory()) do
    have[stack.item] = (have[stack.item] or 0) + stack.count
    if stack.slot <= 35 then
      free = free - 1
    end
  end
  local missing = {}
  for item, count in pairs(NEED) do
    local got = have[item] or 0
    if got < count then
      missing[#missing + 1] = item:gsub("minecraft:", "") .. " " .. got .. "/" .. count
    end
  end
  if #missing == 0 then
    sys.notify("Packed. " .. free .. " free slots")
  else
    sys.notify("Missing: " .. table.concat(missing, ", "))
  end
end
```

**The trick:** the same item can sit in several slots, so it adds them up by name first. `table.concat` joins a list into one line, with `", "` between the entries.

## Radar: what's that noise?

Warns you when a monster comes within 8 blocks, even behind a wall, and names it.

```lua
-- Needs LV0.
program { name = "Radar", type = "resident", tops = 4 }

local CLOSE = 8          -- blocks
local alarmed = false

function on_start()
  sys.every(1)
end

function on_timer()
  local nearest = nil
  for _, mob in ipairs(world.mobs()) do      -- nearest first
    if mob.hostile then
      nearest = mob
      break
    end
  end
  if nearest and nearest.distance <= CLOSE then
    if not alarmed then
      sys.notify(nearest.name .. ", " .. math.floor(nearest.distance) .. " m away")
      alarmed = true
    end
  else
    alarmed = false
  end
end
```

**The trick:** the list comes nearest first, so the first monster in it is the closest one, and `break` stops looking. Wired mobs have a gateway, so they're not in `world.mobs()`: add `remote.scan()` to catch those too.

## Lava: dig carefully (LV1)

Every 2 seconds, looks at the 100 blocks around and below you, and warns you once when there's lava among them.

```lua
-- Needs LV1.
program { name = "Lava", type = "resident", tops = 6 }

local warned = false

function on_start()
  sys.every(2)
end

-- 2 blocks out each way, from 2 below your feet to 1 above: 5 x 4 x 5 blocks.
local function findLava()
  for dx = -2, 2 do
    for dz = -2, 2 do
      for dy = -2, 1 do
        if world.block(dx, dy, dz) == "minecraft:lava" then
          return math.max(math.abs(dx), math.abs(dy), math.abs(dz))
        end
      end
    end
  end
  return nil
end

function on_timer()
  local distance = findLava()
  if distance and not warned then
    sys.notify("Lava within " .. distance .. " m. Dig carefully")
    warned = true
  elseif not distance then
    warned = false
  end
end
```

**The trick:** three loops inside each other visit every block in a box. Lava counts as see-through, so a lava pool shows up even behind a wall of stone; only a single lava block sealed in rock reads `"hidden"`. It books 6 TOPS because 100 calls take a while: about a second each time.

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
- **Tool saver:** check `me.held()` every few seconds and warn when `max_damage - damage` drops below 10, before your pickaxe breaks.
- **Breath:** under water, `me.air()` counts down from 15 seconds. Warn at 5.
- **Potion timer:** list `me.effects()` on a wheel key, or warn when one has less than 10 seconds left.
- **Storm warning:** thunderstorms let monsters spawn in daylight. Watch `world.weather()` and say when it turns to `"thunder"`.
- **Status on a key:** a query for the wheel that shows health, hunger, free TOPS and Buffer on one card.
- **Auto-bandage** (LV3): a gentler Combat Medic that heals 2 whenever you're below half and your hunger is above 14, so it never starves you.
- **Kill streak:** count kills since you last got hit (`on_kill` and `on_hit_taken`), and celebrate every 5.

**Next: [FAQ](FAQ)**
