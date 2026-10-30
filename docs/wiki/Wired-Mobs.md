# Wired Mobs

Some zombies and skeletons spawn with a **gateway**. They're tougher, some of them hack you, and they drop **Data Shards**. They give off small electric sparks every couple of seconds, which is how you tell them apart.

## When and where

- They start appearing on **day 2**. The chance rises every day until **day 30**, when about **1 in 4** naturally spawned zombies and skeletons is wired.
- Spawners and spawn eggs never make wired mobs.
- **Deeper is stronger**: their level depends on height. Anything below y = 0, or outside the Overworld, is at the top of its range.
- Each one rolls its own **kernel** (settings, so its own flaws). The roll comes from the world seed and the mob, so the same mob always has the same kernel.

## The mobs

### Wired Zombie (LV1–3)

- +2 hearts of max health.
- When hurt, it gets a burst of speed for 3 seconds, at most once every 10 seconds.

### Wired Skeleton (LV3–4)

- Faster, and wears 2 points of armour.
- When it has you in sight and in range, it **inspects** you, taking about a second depending on its kernel. Your programs hear `on_scanned`.
- When one of its arrows hits you, it starts an **access attempt**:
  - it uses your worst flaw if it has inspected you, otherwise `force`;
  - if it gets in, you're slowed hard and can't jump for 1.5 seconds;
  - then it closes the session and can't try you again for 10 seconds.

Your kernel defends against it exactly as against a player: deny, delay or resist (see [Cyber Combat](Cyber-Combat#defending)). Creative players and Null players are never targeted.

## Hacking them back

Wired mobs are valid targets for your programs and the Scanner:

- they have no kernel hooks, so they never deny you;
- they defend with their kernel's reserve TOPS;
- their flaws come from the kernel they rolled.

Inspect one before you hack it: a skeleton with `verify = "skip"` has a critical flaw and goes down in half a second.

## Drops

When a player had a hand in the kill, a wired mob drops **1 + half its level** Data Shards, plus one more half the time. An LV1 zombie drops 1–2, an LV4 skeleton 3–4.
