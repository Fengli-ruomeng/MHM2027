# Building a Firewall

*Beginner's Guide, page 7 of 13.*

On this page you'll write a kernel with a real firewall, install it, and watch it turn a skeleton away. We'll build it in three versions, each a little smarter.

## How a kernel file looks

A kernel is a file like a program, with a `kernel` header instead of a `program` one:

```lua
kernel { name = "Shield", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }
```

Those four settings are the safe defaults: they give you **no flaws**. [Tuning Your Kernel](Tuning-Your-Kernel) explains what changing them buys and costs. Leave them for now.

Under the header go **hooks**: functions the gateway calls when something happens to it. Your firewall is the hook `on_access_request`. Edge calls it every time something tries to break in, and gives it `req`, the request:

| Field | What it is |
| --- | --- |
| `req.from` | Who's attacking: `req.from.name`, `req.from.address`, `req.from.is_player`, `req.from.level` |
| `req.method` | `"force"`, or the name of the flaw they're using, such as `"memory"` |
| `req.tops` | How many TOPS they're attacking with |
| `req.escalate` | `true` if they're already in and going for root |

The function **returns** one answer: `allow()`, `deny()`, `delay(seconds)` or `resist(tops)`. If it returns nothing, the attempt is allowed.

## Making the file

Open the editor with `edit`, press **+ Kernel** at the bottom of the file list, and call it **Shield**. You get a template; replace it with the version below and save.

## Version 1: deny the fast attacks

```lua
-- Needs LV0.
kernel { name = "Shield", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.method ~= "force" then
    return deny()        -- they found a flaw: that's the fast kind, so turn it away
  end
  return allow()         -- forcing takes about 3 seconds; let it run for now
end
```

An attacker who uses one of your flaws gets in within a second, so that's worth 256 MB of Buffer to stop. A forced attempt takes about 3 seconds, long enough to shoot back.

### Installing it

1. Stand within 6 blocks of a **Flash Station**.
2. In Edge, type `kernel install Shield`.
3. Your gateway reboots with the new kernel. That takes 10 seconds and leaves you weak for a moment, so do it somewhere safe. You can only install once a minute.

`kernel` on its own shows what's installed. `kernel install Baseline` goes back to the built-in one.

### Testing it

Let a wired skeleton shoot you, then type `log` in Edge. **kernel.log** opens, and every decision is in it: `attempt from 10.x.x.x (memory)`, then `denied 10.x.x.x`.

## Version 2: spend wisely

Version 1 denies every flaw attack until your Buffer runs dry. Version 2 checks what it can afford first:

```lua
-- Needs LV0.
kernel { name = "Shield", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  local buffer = sys.buffer()     -- how much Buffer is left, in MB
  local heat = sys.heat()

  -- A fast attack, and we can still afford to stop it.
  if req.method ~= "force" and buffer >= 512 then
    return deny()
  end

  -- Otherwise slow them down with spare TOPS, if we're not running hot.
  if heat < 50 then
    return resist(6)
  end

  -- Too hot to resist: buy two seconds for free.
  return delay(2)
end
```

- `sys.buffer()` and `sys.heat()` read your own gateway, just like in a program.
- It stops denying when less than 512 MB is left, so your Buffer never runs completely dry.
- Each TOPS you resist with adds 1.5 Heat, so `resist(6)` adds 9. Stopping at 50 keeps you well clear of the `thermal` flaw at 70.

## Version 3: raise the alarm

A kernel can have more than one hook. At LV0 you get **two**. The second is `on_session_opened`, called when someone gets in anyway:

```lua
-- Needs LV0.
kernel { name = "Shield", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.method ~= "force" and sys.buffer() >= 512 then
    return deny()
  end
  if sys.heat() < 50 then
    return resist(6)
  end
  return delay(2)
end

-- Someone got in anyway. Players get a reboot in the face; mobs just get noted.
function on_session_opened(who)
  if who.is_player then
    sys.notify(who.name .. " broke in. Rebooting!")
    sys.reboot()
  else
    sys.notify(who.name .. " broke in")
  end
end
```

Rebooting throws everyone out at once. It also stops your programs, so it's kept for players, who can do real damage. A skeleton's 1.5 seconds of slowness isn't worth losing your programs over.

One thing to know: two hooks at LV0 use both of your hook points, and a kernel that uses every hook point gets a small flaw called `core`. [Tuning Your Kernel](Tuning-Your-Kernel) explains flaws, and why this one is a fair price here.

## Different rules for different attackers

`req.from` tells you who it is, so a firewall can play favourites:

```lua
-- Needs LV0.
kernel { name = "Picky", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.from.is_player then
    return deny()                    -- players never get a free try
  end
  if req.escalate then
    return resist(8)                 -- already in and going for root: fight hardest here
  end
  if req.method ~= "force" then
    return delay(2)
  end
  return allow()
end
```

## What a firewall can't do

- **It must answer at once.** Hooks can't `sys.sleep`, and one that runs too long gets the default answer (allow).
- **It can't touch your body or other gateways.** `me.*` and `remote.*` aren't allowed in a kernel. Programs can do that, and they hear about every attempt too, through `on_access_attempt(req)`. A program can't change the answer, but it can fight back (see [Hacking Mobs and Players](Hacking-Mobs-And-Players)).
- **It isn't free.** Every deny costs Buffer, every resist costs TOPS and Heat.

## Common mistakes

| Mistake | What happens | Fix |
| --- | --- | --- |
| `return deny()` for everything | Buffer is gone in a minute, then everything gets through | Deny only the fast attacks, as in version 2 |
| `resist(30)` | You overheat and get the `thermal` flaw, so you're *easier* to hack | Resist a little, and stop when Heat is high |
| Forgetting `return` | The attempt is allowed | Every path should end in `return ...` |
| Testing at a station without a target | Nothing happens | Let a wired skeleton shoot you, then check `log` |

**Next: [Tuning Your Kernel](Tuning-Your-Kernel)**
