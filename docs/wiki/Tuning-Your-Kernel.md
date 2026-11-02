# Tuning Your Kernel

*Beginner's Guide, page 8 of 13.*

The firewall is only one of your kernel's jobs. This page covers the rest: the four **settings** in its header, the **flaws** they create, and the other **hooks**.

## The kernel's four jobs

| Hook | When it's called | What you'd use it for |
| --- | --- | --- |
| `on_access_request(req)` | Something tries to break in | Your firewall ([page 7](Building-A-Firewall)) |
| `on_session_opened(who)` | Something got in | Raise the alarm, reboot |
| `on_low_ram(free)` | You've run out of memory | Choose which programs to stop. Without it, your newest program goes |
| `on_panic(reason)` | Your kernel crashed from running out of TOPS | Restart what matters. Without it, nothing restarts |

You can't use all four at once early on. Your level gives you **hook points**: 2 at LV0 to LV2, 3 from LV3, 4 from LV6. And there's a catch: a kernel that uses **every** hook point it has gets the `core` flaw (minor). Leaving one spare is safer.

## Settings: speed for safety

Every setting that makes you weaker also gives you something. The defaults (`verify = "full"`, `rate_limit = 4`, `schedule = "fair"`, `gc = "normal"`) give no flaws and no bonuses.

| Setting | Change it to | You gain | You pay |
| --- | --- | --- | --- |
| `verify` | `"fast"` / `"skip"` | **Your own hacks** run 1.25× / 1.5× faster | The `memory` flaw: minor / **critical** |
| `rate_limit` | 8 / 12 (up to 20) | **Your inspections** take 0.5 s instead of 1 s, and faster still | The `intake` flaw: minor / major |
| `schedule` | `"priority"` | Out of TOPS, only your lowest-priority programs crash, not everything | The `scheduler` flaw: minor, major with 3+ programs running |
| `gc` | `"aggressive"` | Leak is cleaned up twice as fast | 2 more TOPS for the kernel |
| `gc` | `"lazy"` | 1 TOPS back | Leak is cleaned up at half speed |

A lower `rate_limit` (down to 1) makes your inspections *slower*, up to 2 seconds, with no benefit. Don't.

## What flaws mean in a fight

A flaw is a door. Attackers who know about it get in through it fast; anyone else has to force their way in:

| How they get in | Time (before TOPS) |
| --- | --- |
| Force | About 3 s |
| A minor flaw | About 2 s |
| A major flaw | About 1 s |
| A critical flaw | About 0.5 s |

Some flaws come and go during a fight, whatever your settings:

| Flaw | When |
| --- | --- |
| `thermal` | Heat 70 or more (major) |
| `motor` | Body powers running: 1–2 minor, 3 or more major |
| `core` | Your kernel uses every hook point (minor) |

Type `flaws` in Edge to see yours, and the ones you're close to. The editor shows the flaws a kernel will give you while you're writing it, before you install anything.

## Three kernels to start from

### Phoenix: never stay down

For players who run a lot of programs. When a panic wipes everything, the important ones come back; when memory runs out, the biggest program goes, not the newest.

```lua
-- Needs LV0.
kernel { name = "Phoenix", schedule = "fair", gc = "aggressive", verify = "full", rate_limit = 4 }

local ESSENTIALS = { "Swarm", "Scanner" }

function on_panic(reason)
  for _, name in ipairs(ESSENTIALS) do
    sys.start(name)
  end
  sys.notify("Back up after: " .. reason)
end

function on_low_ram(free)
  local biggest, most = nil, -1
  for _, p in ipairs(sys.programs()) do
    if p.ram > most then
      biggest, most = p.name, p.ram
    end
  end
  if biggest then
    sys.stop(biggest)
  end
end
```

`gc = "aggressive"` cleans Leak twice as fast, which also makes memory attacks wear off sooner. No firewall here, though, and two hooks give it the `core` flaw until LV3: pick Phoenix when crashes hurt you more than hackers do.

### Duelist: a hacker's kernel

For players who attack. Faster hacks and faster inspections, paid for with two minor flaws, which its firewall then guards.

```lua
-- Needs LV0.
kernel { name = "Duelist", schedule = "fair", gc = "normal", verify = "fast", rate_limit = 8 }

-- Our settings open the memory and intake flaws. Those are the doors
-- attackers will try, so those are the ones worth paying to close.
function on_access_request(req)
  if (req.method == "memory" or req.method == "intake") and sys.buffer() >= 512 then
    return deny()
  end
  return allow()
end
```

Your attacks are a quarter faster and your inspections take half a second. One hook out of two keeps you clear of the `core` flaw.

### Sentinel: all-round defence

Shield from [page 7](Building-A-Firewall), with one change: aggressive garbage collection to shrug off memory attacks.

```lua
-- Needs LV0.
kernel { name = "Sentinel", schedule = "fair", gc = "aggressive", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.method ~= "force" and sys.buffer() >= 512 then
    return deny()
  end
  if sys.heat() < 50 then
    return resist(6)
  end
  return delay(2)
end

function on_session_opened(who)
  sys.notify(who.name .. " broke in")
  if who.is_player then
    sys.reboot()
  end
end
```

At LV0 to LV2 it uses both hook points, so it has the `core` flaw (minor): attackers who find it get in in 2 seconds instead of 3. The firewall denies them anyway while you have Buffer. From LV3 there's a spare hook point and the flaw is gone.

## Priority: deciding what dies first

With `schedule = "priority"`, running out of TOPS doesn't crash everything. Your kernel crashes your **least important** programs, one at a time, until the rest fit. You say what's important in each program's header, from 1 to 9 (5 if you don't say):

```lua
program { name = "Medic", type = "resident", tops = 3, priority = 9 }    -- keep this alive
program { name = "Scanner", type = "resident", tops = 3, priority = 1 }  -- drop this first
```

The price is the `scheduler` flaw, which turns major once you run 3 or more programs. Worth it if you run a lot of programs and a panic would kill your healing at the worst moment.

## Swapping kernels

Kernels install at a Flash Station, once a minute, with 10 seconds of downtime. So you can't swap mid-fight; pick the one that suits what you're about to do. `kernel ls` lists the kernels in your storage, `kernel install Baseline` goes back to the built-in one. All the rules are in [Kernels and Flaws](Kernels-and-Flaws).

**Next: [Hacking Mobs and Players](Hacking-Mobs-And-Players)**
