# What Is Edge?

*Beginner's Guide, page 1 of 13.*

## The short version

Every player has a **gateway**: a small computer built into your body. **Edge** is its operating system. You talk to it through a terminal (press **G**), and you give it jobs by writing small programs in **Lua**, a simple programming language.

Those programs can watch your health, react when you're hit, make you faster, stop fall damage, heal you, break into enemies and turn them around, or just tell you where home is. You decide what they do.

The catch: **the world has gateways too.** From day 2, some zombies and skeletons spawn *wired*. They have gateways of their own, and the skeletons will try to hack you. A hacked player is slowed so hard they can barely move. Edge is how you fight back.

## What can Edge do?

| You want to... | Edge can... | Where to read |
| --- | --- | --- |
| Survive fights | Heal you in a burst, give extra hearts, speed you up after a big hit, cancel knockback | [Why Level Up?](Why-Level-Up) |
| Stop being hacked | Turn attempts away, slow them down, or reboot out of trouble, with your own rules | [Why You Need a Firewall](Why-You-Need-A-Firewall) |
| Fight back | Freeze a mob in place, spin it around, make a skeleton drop its bow, burn out its gateway | [Hacking Mobs and Players](Hacking-Mobs-And-Players) |
| Play smarter | Warn you before you starve, cancel fall damage when you fall, point you home, count your kills | [Quality-of-Life Programs](Quality-Of-Life-Programs) |
| Just program | Write anything from a two-line alarm to a hunting bot with memory and a plan | [Cool Programs](Cool-Programs) |

What Edge **can't** do: it can't fly, build, mine or edit the world. It works on **bodies** (yours, and anything with a gateway) and on **gateways** (yours and theirs).

## How it fits together

```
 you write a program  ──>  your gateway runs it  ──>  it acts on your body
        (Lua)                (RAM, TOPS, Heat)        or on another gateway
                                    ▲
                         your kernel guards it
                     (decides who gets in, and
                      what happens when it breaks)
```

- **Programs** are the jobs: one file each, in your storage.
- **The kernel** runs the gateway. Its most important job is your **firewall**: it decides what happens when something tries to hack you. You can write your own (pages 6 to 8).
- **Your level** (LV0 to LV10) decides how much your gateway can do: how many programs at once, which powers your programs may use, how much punishment you can take. You raise it at a Flash Station with Data Shards that wired mobs drop (page 5).

## Your first five minutes

1. **Join a world and press G.** Your gateway starts **off** every time you join or respawn. G boots it: watch the boot screen, then the terminal, **Edge**, opens.
2. **Type `ls`.** This lists your storage. You start with eight example programs.
3. **Type `run Monitor`.** A window opens on the right. Monitor now watches your health and warns you when it's low. It keeps running when you close Edge.
4. **Press Esc**, and look at the left edge of your screen: two thin bars. That's your gateway. [Reading Your Lens](Reading-Your-Lens) explains them.
5. **Day 2 onward:** watch for zombies and skeletons giving off small sparks. Those are wired.

## Don't want any of this?

Flash **Null Firmware** at a Flash Station and your gateway is gone for good (until you flash it back). You lose every power, and nothing can hack you. See [Progression](Progression#the-null-route).

**Next: [Reading Your Lens](Reading-Your-Lens)**
