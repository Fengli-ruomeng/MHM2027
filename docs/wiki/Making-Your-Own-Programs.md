# Making Your Own Programs

*Beginner's Guide, page 10 of 13.*

You've seen a lot of programs. This page is about the process: how to go from "I wish my gateway would..." to a program that works, the same way you built a firewall on [page 7](Building-A-Firewall).

## 1. Say it in one sentence

Almost every program fits one of three shapes:

| Shape | Example | Type |
| --- | --- | --- |
| **When** something happens, do something | When I take a big hit, run faster | `resident` |
| **Every** few seconds, check something | Every 2 s, warn me if I'm starving | `resident` |
| **When I ask**, do something once | Freeze whatever I'm looking at | `transient`, or `query` if it only reads |

- **resident**: keeps running until you stop it. Starts with `on_start`.
- **transient**: runs once when you start it, then ends. Starts with `on_run`. Perfect for the quick wheel.
- **query**: like transient, but it may only read, never change your body. It's a little cheaper.

## 2. Find the pieces

- **The events** (the "when"): `man events` in the terminal lists them. On page 4 you used `on_hit_taken` and `on_timer`.
- **The APIs** (the "do"): `man me`, `man sys`, `man remote` and `man world` list each group. `man me.speed_boost` explains one, including the level it needs: red if you're not there yet.
- **In the editor**, **Ctrl+Space** suggests APIs and events as you type.

Two questions to ask about each API you pick:

- **Does it change my body?** (`me.speed_boost`, `me.regen_burst`...) Then the program needs a **socket**, filled at a Flash Station.
- **What level does it need?** The editor's bottom bar shows the highest level your program uses.

## 3. Book the right TOPS

`tops` in the header is how fast your program can think: each TOPS lets it run 20 Lua instructions per tick, and an API call costs 10 to 80 of those. A program that runs out of budget isn't broken, it just finishes next tick.

| The program... | Book |
| --- | --- |
| Checks something on a timer | 2 |
| Reacts to events, with a few `if`s | 2 to 4 |
| Loops over lists (scans, programs) | 4 to 6 |
| Hacks things | 8 or more: TOPS is attack strength |

Then check: `top` in Edge shows **RES** (what you booked) against **USE** (what it really uses). USE in orange means it's hitting the limit and waiting a lot; book a little more. USE far below RES means you're wasting TOPS.

## 4. Build it in small steps

Don't write it all at once. Start with the "when" part and a `print`:

```lua
function on_hit_taken(attacker, amount)
  print("hit for " .. amount)
end
```

Run it and see the lines arrive in its window. Then add the `if`. Then the action. After each step: save, `stop`, `run`, look at the window.

## 5. Fix what breaks

- **Before it runs:** the editor's bottom bar shows syntax errors and level problems with their line. Saving still works, but running won't.
- **While it runs:** a crash turns its window pink with the error and the line. **edit line N** takes you there; **restart** runs it again.
- **It runs but does nothing:** your "when" probably never happens. Add `print` at the top of the event function and check.

## 6. Make it easy to use

- Fire transients from the **quick wheel**: `wheel set 4 run MyProgram`, then hold V and press 4.
- Put body-changing programs in a **socket** at a Flash Station: `socket add MyProgram`.
- If it matters in a fight, give it `priority = 9` and add it to your kernel's restart list ([page 8](Tuning-Your-Kernel)).

## Start to finish: a watchdog

**The sentence:** *when one of my programs crashes, start it again, but give up if it keeps crashing.*

- **Shape:** "when something happens". **Type:** resident.
- **Events:** `on_program_crash(name)` for the "when", and a timer to forget old crashes.
- **APIs:** `sys.start` to restart it, `sys.notify` to tell me, `sys.every` for the timer.
- **TOPS:** 2. It reacts to rare events.

```lua
-- Needs LV0.
program { name = "Watchdog", type = "resident", tops = 2 }

local restarts = {}      -- program name -> how often it was restarted this minute

function on_start()
  sys.every(60)          -- start each minute with a clean slate
end

function on_program_crash(name)
  local count = (restarts[name] or 0) + 1
  restarts[name] = count
  if count > 3 then
    sys.notify(name .. " keeps crashing. Leaving it off.")
    return
  end
  local ok, why = sys.start(name)
  if ok then
    sys.log("restarted " .. name .. " (" .. count .. " of 3)")
  else
    sys.log("couldn't restart " .. name .. ": " .. tostring(why))
  end
end

function on_timer()
  restarts = {}
end
```

- `restarts[name] or 0` reads "the count so far, or 0 if there isn't one yet". A neat trick for counting.
- `sys.start` returns `true`, or `false` and the reason ("Not enough TOPS"...). Checking it means the watchdog tells you *why* instead of failing silently.
- `sys.start` takes a file name; it works here because the starter programs, like most, are named after their file.

Test it with something that crashes on purpose, such as `local x = nil; x.y = 1` in an `on_start`.

**Next: [Cool Programs](Cool-Programs)**
