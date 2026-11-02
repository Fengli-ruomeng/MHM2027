# Reading Your Lens

*Beginner's Guide, page 2 of 13.*

The **Lens** is the HUD your gateway draws over your view. It looks busy at first, but there are only four numbers that matter, and a few places where news appears.

## The two bars on the left

| Bar | Short for | Think of it as | Goes down when | Goes up when |
| --- | --- | --- | --- | --- |
| **B** (cyan) | Buffer | Your **shield against hacking** | Your firewall turns an attack away (256 MB), or you kick an intruder out (512 MB) | Only when you drink a Cyber Potion. It **never refills by itself** |
| **R** | RAM | Your gateway's **memory**, filling up | Programs stop, or Leak gets cleaned up | You run programs (white), or junk called Leak piles up (violet) |

A third bar, **H** for Heat, appears beside them only when you're running warm.

The numbers flash up for a moment whenever they change. Hold **Left Alt** to see everything at once: level, kernel, Buffer, RAM, TOPS and Heat.

## What each number means for you

### Buffer: your hacking shield

Buffer isn't your health. Nothing hurts you when it's low. But your firewall pays for every attack it **denies** out of Buffer. At LV0 you have 1 GB, which is four denies. When it's empty, your firewall can't deny anything, and attacks get through.

So Buffer is the thing to save, and Cyber Potions (see [Items](Items#cyber-potions)) are the only way to refill it.

### RAM: memory, and the junk called Leak

Every running program takes a little RAM, and so does your kernel. **Leak** is junk memory left behind when programs crash, and attackers can pump it into you on purpose.

- Your gateway cleans Leak up by itself, **faster when you're well fed**. At 6 food points or less it stops cleaning altogether.
- If programs plus Leak fill your RAM, you're **out of memory**: the bar turns magenta and you get **Wither** until it clears.

Early on, your own programs barely dent your RAM. What fills it is Leak: from crashes, and from attackers. More RAM (a higher level) mostly means it takes a much bigger attack to Wither you.

### TOPS: computing power

TOPS isn't a bar; you see it with **Left Alt**, or as a warning chip when it runs low. Each program **reserves** some TOPS for as long as it runs, like booking seats. LV0 has 25, and your kernel books 3 of them.

- A program that can't get its seats won't start: "Not enough TOPS".
- If you somehow run completely out, your kernel **panics**: every program crashes and you're stunned for a few seconds.

TOPS is the budget you'll feel first. It decides how many programs you can run at once, and in a hacking fight more TOPS means faster attacks and stronger defence.

### Heat

Heat climbs when your firewall **resists** an attack, and when you run **Overload** programs. It cools by 2 a second. Above **70**, you get the `thermal` flaw: a weak spot attackers can use. Keep it low unless you need it.

## The rest of the screen

| Where | What | Why you care |
| --- | --- | --- |
| Under the bars | **Flaw chips**, such as `thermal major` | A flaw is a weak spot. Attackers who know it get in faster |
| Bottom left | **Sockets**: S1, S2, O1... | Programs that change your body (speed, healing...) only run from a socket |
| Top right | **Event cards** | Everything Edge tells you. **Magenta means someone is acting against you** |
| Middle, in big letters | **Banners**: SYSTEM BREACH, KERNEL PANIC... | The moments that matter most |
| Around a mob you look at | **Brackets and a label** | Its name, level and distance; after you inspect it, its flaws |
| Bottom right | **Tasks** | Anything that takes time: attacks on you (blinking), your programs at work, cooldowns |

## What to do when...

| You see | It means | Do this |
| --- | --- | --- |
| A magenta card `... // ACCESS` | Something is trying to hack you | If it keeps happening, you need a firewall: [page 6](Why-You-Need-A-Firewall) |
| SYSTEM BREACH, and you can't move | Something got in | Kill the skeleton, or reboot (`reboot` in Edge) to kick everyone out |
| The R bar turning violet | Leak is piling up | Eat something. Cleaning stops at 6 food |
| R turns magenta and you wither | Out of memory | Stop a program, eat, wait it out |
| Buffer low | Your firewall can't deny much longer | Drink a Cyber Potion, or make your firewall resist instead of deny |
| `thermal` under the bars | Heat above 70 | Stop resisting or overloading for a moment and let it cool |

**Next: [The Starter Programs](The-Starter-Programs)**
