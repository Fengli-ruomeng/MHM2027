# Why You Need a Firewall

*Beginner's Guide, page 6 of 13.*

If wired skeletons keep freezing you in place, this page is why, and the next one is the fix.

## What a hack looks like from the inside

Here's a wired skeleton taking you apart, second by second:

1. **It sees you and inspects you.** About a second later it knows your **flaws**, the weak spots in your gateway. (Your programs hear `on_scanned`.)
2. **Its arrow hits.** That starts an **access attempt**: a race to break into your gateway. A magenta card appears: `10.x.x.x // ACCESS`.
3. **It picks your worst flaw.** Through a critical flaw it gets in in about half a second. With no flaw to use, it has to **force** its way in: about 3 seconds.
4. **Your kernel is asked what to do.** With the built-in kernel, **Baseline**, the answer is always "let it through".
5. **It's in.** SYSTEM BREACH. You're slowed so hard you can barely walk and you can't jump, for 1.5 seconds, while it keeps shooting.

Then it waits 10 seconds and does it again.

## Players can do far worse

A skeleton only freezes you. A player who gets in has a 6-second **session** with a whole toolbox:

| They can... | What it does to you |
| --- | --- |
| Neutralize you | Slowed to a crawl and no jumping, up to 4 s |
| Turn your view | Snap your head up, down or behind you |
| Leak your memory | Pump junk into your RAM until you're out of memory and withering |
| Overheat you | Up to 25 Heat a second; above 70 you have the `thermal` flaw, so the next hack is faster |
| Read your inventory | Every item you carry |
| List your programs | And, with root access, **stop them one by one** |
| Make you drop your weapon | With root access: the sword in your hand hits the floor |

Gateway mobs follow exactly the same rules, so once you learn to hack ([page 9](Hacking-Mobs-And-Players)), all of this works for you too.

## What your firewall is

Your **kernel** is the software that runs your gateway. One of its jobs is to answer every access attempt. That answer is your **firewall**: a short function called `on_access_request`. Baseline doesn't have one, so it lets everyone in.

Your firewall can give four answers:

| Answer | What happens | What it costs you |
| --- | --- | --- |
| `allow()` | They carry on at normal speed | Nothing |
| `deny()` | They're turned away and can't retry for 2 s | **256 MB of Buffer**. If you can't pay, they're let in |
| `delay(seconds)` | Up to 2 s added to their attempt | Nothing, but it's small |
| `resist(tops)` | You throw spare TOPS into the race, so they're slower | Those TOPS for the length of the attempt, and **1.5 Heat each** |

## Why not just deny everything?

Because there's no free defence. At LV0 your Buffer pays for **four** denies, and it never refills by itself. A skeleton that shoots every few seconds would drain it in a minute, and then your firewall can't deny anything at all.

A good firewall is **choosy**:

- **Attacks through a flaw are the dangerous ones**: half a second to a second. Those are worth paying Buffer to deny.
- **Forced attacks are slow** (about 3 seconds). Resisting with a few spare TOPS can stretch them out long enough to kill the attacker or walk away.
- **When you're low on Buffer**, stop denying and resist or delay instead.
- **When you're hot**, stop resisting, or you'll give yourself the `thermal` flaw.

## Other ways out

- **Reboot** (`reboot` in Edge, or `sys.reboot()` in a program): every attack and session on you ends at once, and nobody can hack you while you boot. But all your programs stop, and you're slowed for 3 seconds.
- **Kick them out**: `sys.end_session(who)` closes a session that's already open, for 512 MB of Buffer.
- **Fewer flaws**: your flaws come from your kernel's settings and from what you're doing (Heat, body powers running). Fewer flaws means attackers have to force their way in. See [Tuning Your Kernel](Tuning-Your-Kernel).
- **Being off is no hiding place.** A gateway that's off has no firewall at all.

**Next: [Building a Firewall](Building-A-Firewall)**
