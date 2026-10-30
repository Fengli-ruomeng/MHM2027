# Kernels and Flaws

Your **kernel** runs your gateway. Its settings decide how fast you are and where you're weak, and its **hooks** decide what happens when something goes wrong.

You start with **Baseline**: safe, with no hooks. You can write your own from LV0.

## Writing a kernel

A kernel is a file with a `kernel` header instead of a `program` header:

```lua
kernel { name = "Sprinter", schedule = "fair", gc = "normal", verify = "fast", rate_limit = 6 }

function on_access_request(req)
  if req.method ~= "force" then
    return deny()          -- they guessed a flaw: block it (costs 256 MB of Buffer)
  end
  return resist(4)         -- forced: make them work for it
end
```

Install it from the panel's **Kernel** tab:

- You must be within 6 blocks of a **Flash Station**, and you can only install once every 60 seconds.
- Installing stops every program and takes your gateway down for 10 seconds with a debuff, so do it somewhere safe.

## Settings

Every setting that makes you weaker also gives you something:

| Setting | Options (default first) | What you gain | What it costs |
| --- | --- | --- | --- |
| `verify` | `full`, `fast`, `skip` | Your own access attempts run 1.25× (fast) or 1.5× (skip) faster | `memory` flaw: minor (fast), critical (skip) |
| `rate_limit` | 1 to 20, default 4 | Your inspections are faster: 4 takes 1 s, 8 takes 0.5 s, 0.25 s at the fastest | `intake` flaw: minor from 8, major from 12 |
| `schedule` | `fair`, `priority` | Out of TOPS, your lowest-priority programs crash instead of the whole kernel panicking | `scheduler` flaw: minor, major with 3+ programs running |
| `gc` | `normal`, `lazy`, `aggressive` | aggressive clears Leak twice as fast; lazy saves a TOPS | aggressive reserves 2 more TOPS; lazy clears Leak at half speed |

A lower `rate_limit` makes your inspections slower, down to 2 seconds at 1.

## Hooks

Hooks let your kernel take over at key moments:

| Hook | When | Default without it |
| --- | --- | --- |
| `on_access_request(req)` | Someone tries to break in. Return `allow()`, `deny()`, `delay(seconds)` or `resist(tops)` | Allow |
| `on_session_opened(who)` | Someone got in | Nothing |
| `on_low_ram(free)` | You ran out of RAM | Your newest program stops |
| `on_panic(reason)` | Your kernel panicked | Nothing restarts |

How many hooks you may define depends on your level: **2** at LV0–2, **3** at LV3–5, **4** from LV6. Hooks must answer at once: they can't sleep, and a hook that runs too long gets the default instead.

What the answers to `on_access_request` cost is in [Cyber Combat](Cyber-Combat#defending).

## Flaws

A **flaw** is a weak spot. If an attacker's method matches one of your flaws, they get in faster (see [Cyber Combat](Cyber-Combat)). Flaws are never random: each comes from your settings or from what you're doing right now.

| Flaw | Comes from | Severity |
| --- | --- | --- |
| `memory` | `verify = "fast"` / `"skip"` | minor / critical |
| `intake` | `rate_limit` 8 or more / 12 or more | minor / major |
| `scheduler` | `schedule = "priority"` | minor; major with 3+ programs |
| `thermal` | Heat 70 or more | major, until you cool down |
| `motor` | Body enhancements running: 1–2 / 3+ | minor / major |
| `core` | Using every hook point you have | minor |

The first three come from your settings and only change when you install a kernel. The last three come and go during a fight.

### Seeing your flaws

- On install, the log lists the flaws your new settings create.
- The **Kernel** tab lists them all, plus the ones you're close to triggering, in grey.
- The HUD shows any flaw that comes and goes.
- `sys.flaws()` and `on_flaw_changed(class, severity)` let your programs react.
- Attackers see the same list with `remote.inspect`.

There's no perfect kernel. Speed always costs exposure, and the editor shows the price as you type.
