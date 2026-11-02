# Your First Program

*Beginner's Guide, page 4 of 13.*

No programming experience needed. By the end of this page you'll have written a **swarm alarm**: a program that notices when you're being mobbed and tells you to back off.

## What is code?

A program is a list of instructions your gateway follows. Edge programs are mostly made of **"when this happens, do that"**:

- *when* I get hit, *if* the hit was big, *show* a warning;
- *every* 5 seconds, *check* my health.

The "when" parts are called **events**. Edge has a list of them: you got hit, you killed something, a timer went off, someone scanned you... (all in the [API Reference](API-Reference#program-events)). You write a **function** named after the event, and Edge runs it every time that event happens.

The language is **Lua**. It reads almost like English, and you only need a little of it.

## Making the file

In Edge, type `edit Hello`. The editor asks for the new file's name with Hello filled in: press **Enter**. Replace what's in the file with the code below, press **Ctrl+S** to save, click the terminal on the left, and type `run Hello`.

After every change: save, then `stop Hello` and `run Hello` again. A program reads its file when it starts.

## Step 1: say hello

```lua
-- Needs LV0.
program { name = "Hello", type = "resident", tops = 2 }

function on_start()
  sys.notify("Hello from my gateway")
end
```

- The first line starts with `--`: that's a **comment**, a note for humans. Edge ignores it.
- `program { ... }` is the **header**. Every program has one. `name` is what Edge calls it; `type = "resident"` means it keeps running until you stop it; `tops = 2` books 2 TOPS of computing power.
- `function on_start() ... end` runs once when the program starts.
- `sys.notify(...)` puts a card on your screen. Things like `sys.notify` and `me.health` are Edge's **APIs**: the commands your gateway understands. `man sys.notify` in the terminal explains any of them.

Run it. A card says hello.

## Step 2: react to something

```lua
-- Needs LV0.
program { name = "Hello", type = "resident", tops = 2 }

function on_hit_taken(attacker, amount)
  print("ouch: " .. amount)
end
```

`on_hit_taken` is an event: Edge runs it every time you take damage, and hands it two **values**: who hit you (`attacker`) and how much (`amount`). `print` writes a line in the program's window. `..` joins text together.

Run it and get hit by something. Each hit appears in Hello's window.

## Step 3: decide

```lua
-- Needs LV0.
program { name = "Hello", type = "resident", tops = 2 }

function on_hit_taken(attacker, amount)
  if amount >= 4 then
    sys.notify("Big hit: " .. amount)
  end
end
```

`if ... then ... end` only runs the middle when the condition is true. Now small scratches are ignored and big hits get a card. You can compare with `==` (equal), `~=` (not equal), `<`, `<=`, `>` and `>=`.

## Step 4: remember

```lua
-- Needs LV0.
program { name = "Hello", type = "resident", tops = 2 }

local hits = 0

function on_hit_taken(attacker, amount)
  hits = hits + 1
  print("hits so far: " .. hits)
end
```

`local hits = 0` makes a **variable**: a named box holding a value. Because it's outside the function, it keeps its value between hits, so the count goes up and up. A variable made *inside* a function is forgotten when the function ends.

## Step 5: check every few seconds

```lua
-- Needs LV0.
program { name = "Hello", type = "resident", tops = 2 }

local hits = 0

function on_start()
  sys.every(5)
end

function on_hit_taken(attacker, amount)
  hits = hits + 1
end

function on_timer()
  if hits >= 4 then
    sys.notify(hits .. " hits in 5 seconds!")
  end
  hits = 0
end
```

`sys.every(5)` starts a timer: from now on, `on_timer` runs every 5 seconds. Each time it checks how many hits you took, warns you if it was a lot, and sets the count back to 0.

## Step 6: the swarm alarm

One more idea: a **table**, a box that holds many values. Here it remembers *who* hit you, so the program can tell one angry zombie from four.

```lua
-- Needs LV0.
program { name = "Swarm", type = "resident", tops = 2 }

local hits = 0          -- hits taken in the last 5 seconds
local attackers = {}    -- who hit you, by name

function on_start()
  sys.every(5)
  sys.notify("Swarm alarm on")
end

function on_hit_taken(attacker, amount)
  hits = hits + 1
  if attacker then                    -- falls and fire have no attacker
    attackers[attacker.name] = true
  end
end

function on_timer()
  local count = 0
  for name in pairs(attackers) do     -- go through everyone in the table
    count = count + 1
  end
  local hp, max = me.health(), me.max_health()
  if count >= 3 then
    sys.notify(count .. " attackers! Back off.")
  elseif hits >= 4 and hp < max / 2 then
    sys.notify("Taking a beating: " .. math.floor(hp) .. " health left")
  end
  hits = 0
  attackers = {}
end
```

New things:

- `attackers[attacker.name] = true` puts an entry in the table under the attacker's name. Three zombies make three entries; the same zombie hitting twice is still one.
- `for name in pairs(attackers) do ... end` is a **loop**: it runs the middle once for every entry in the table.
- `me.health()` and `me.max_health()` read your body. `local hp, max = ...` takes both answers at once.
- `elseif` is a second chance: checked only if the first `if` was false. `and` needs both sides true.
- `math.floor` rounds down, so 7.5 health shows as 7.

Save it as **Swarm** (`edit Swarm`), run it, and keep it running. It costs 2 of your 25 TOPS.

## When something goes wrong

- **The editor checks as you type.** The bar at the bottom shows the first error and its line. Fix that before saving.
- **A crash** stops the program. Its window turns pink and shows the error and the line, with **edit line N**: click it to jump straight there.
- `attempt to index a nil value` means you used something that doesn't exist, often a missing `if attacker then`.
- `attempt to call a nil value` usually means a typo in an API name, such as `sys.notfy`.
- **Program doesn't seem to do anything?** Add `print(...)` lines and watch its window.

## Lua on one page

| Write | Means |
| --- | --- |
| `-- text` | A comment |
| `local x = 5` | A variable called x holding 5 |
| `x = x + 1` | Add one to x |
| `"a" .. "b"` | Join text: `"ab"` |
| `nil` | Nothing; also what a missing value reads as |
| `if a then ... elseif b then ... else ... end` | Choose |
| `==  ~=  <  <=  >  >=` | Compare |
| `and  or  not` | Combine conditions |
| `function name(a, b) ... end` | A function; `return x` hands a value back |
| `local t = {}` | An empty table |
| `t.key = v` or `t["key"] = v` | Put a value in a table |
| `local list = {"a", "b"}`, `list[1]`, `#list` | A list, its first item, its length |
| `for i = 1, 10 do ... end` | Count from 1 to 10 |
| `for i, v in ipairs(list) do ... end` | Every item of a list, in order |
| `for k, v in pairs(t) do ... end` | Every entry of a table |

That's all the Lua the rest of this guide uses.

**Next: [Why Level Up?](Why-Level-Up)**
