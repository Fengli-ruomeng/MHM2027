<!-- Paste the whole "Cyber system" tab from the Claude Doc below this line, then save. -->

Cyber system
This tab records the cyber system as agreed in discussion. It replaces the earlier guard-chain draft, which played too much like tower defence. The language, runtime and first API set are in their own tab: Scripting and API
Goals
Hacking supports physical combat; it never replaces it. Every rule below follows from that.
• Mid-fight duel. Hacking happens in seconds, alongside melee, not as a separate minigame.
• Planning is the core. Players design their own defences and attacks and improve their physical combat ability.
• No perfect script. Every advantage is paid for somewhere, and the best setup depends on what you are fighting.
• General solutions work; specialised ones work better. A player with only general-purpose code is never helpless.
• Levels give flexibility, not one-click wins. No LV10 player has a button that shuts down an LV0 player.
• One loosely coupled framework. Players and mobs run the same system; entity-specific features are data, not special cases.
• The first version can be small. APIs and hooks are added over time.
Architecture
The terminal works like a standard Linux system: hardware at the bottom, a kernel in the middle, programs on top.
Layer
Role
Locked variant
Gateway
Hardware: RAM, TOPS, Buffer, slots, and how many kernel hook points exist
—
Kernel
The rules and hooks every program runs under. Without one, nothing cyber works: no commands, attacks or modifications, and the entity can't be hacked
Preset kernel
Program
Runs on the kernel: resident, transient, or query
Preset program
• A kernel is not a program.
• A preset is the same thing as its normal version, with its code locked. Locked code keeps the privilege bypass: it may use hooks and APIs above the user's gateway level. This is what makes preset kernels feel like swapping cyberdecks.
• Following the Linux model, getting into a terminal means getting a shell, and root is privilege escalation. An attacker could leave a resident program behind as a backdoor, so defenders check their own process list (proposed).
Kernel
A kernel is settings plus hooks. The set of available hooks is the complexity dial: adding or removing hook types changes how deep the kernel game goes. Kernels are written in Lua, and loops are allowed because the runtime caps how much work any code does per tick.
Kernel job
In Edge
Scheduler
Which programs get TOPS first when you run short
Memory manager
How fast GC clears Leak, and what that costs
Permissions
Which programs may call which APIs
Network stack
How incoming and outgoing connections are checked and limited
Interrupts
What happens on a crash, out-of-memory or alarm
kernel { name = "Bulwark", schedule = "priority", gc = "aggressive", verify = "full", rate_limit = 4 }

function on_panic(reason)
  sys.start("Guards")
end
• Kernels are edited only at a terminal or Flash Station, with a cooldown.
• The gateway sets how many hook points a kernel may use: 2 at LV0–2, 3 at LV3–5, and all 4 at LV6 and above in the first release. The hook list is in the Scripting and API tab.
Flaws come from choices
Players' flaws are never rolled. They come from what the kernel does, because in real systems speed-ups create vulnerabilities: Spectre and Meltdown came from CPUs guessing ahead to run faster.
• The analyser that computes RAM also computes the kernel's flaw profile, live in the editor.
• Example: setting verify = "skip" doubles connection speed but adds a critical MEMORY flaw.
• Flaws use a small fixed set of classes, about 6 to 8. Programs check classes, never specific kernels, so they stay general.
• No perfect kernel exists: every gain is paid for with exposure, and the editor shows the price as you type.
Programs
Programs are grouped by how long they live, not by purpose.
• Resident programs run until stopped: defences, monitors, passive enhancements.
• Transient programs run once and exit. In combat they sit in quick slots, like quickhacks in Cyberpunk 2077, with a TOPS cost and a cooldown.
• Queries read data cheaply, such as scanning a target or listing processes.
A firewall is a resident program plus the kernel's network rules. An attack can be a single transient hit or a resident, sustained intrusion.
The mod ships template code for players to copy and adapt. There are no hidden, hard-coded behaviours; preset programs exist, but they are ordinary programs with locked code.
Without a kernel
LV-1 is a special configuration, not a missing kernel. An LV-1 player has no side effects, can't launch cyber attacks and can't be hacked. Ordinary vanilla mobs with no gateway count as LV-1.
For everyone else, the kernel is part of the body. Losing it is like losing an organ, and the debuff grows the longer it is gone:
Situation
Typical downtime
Debuff
Reboot, voluntary or forced by a crash
A few seconds
Mild: slowness, weaker hits
Swapping kernels
10–20 s
Moderate: lower max health, faster hunger drain
No kernel
Indefinite
Severe: much lower max health, damage and speed; disrupted hunger and saturation
• Rebooting is a real escape. It kicks out an attacker, but stops every resident program and applies the debuff.
• A crash attack forces a reboot. It is the strongest result, but it ends the hack, because a rebooting target can't be hacked.
• Safe, permanent kernel removal will happen at a Workshop. This is not in the first release.
Access and sessions
An access attempt is a short contest fought entirely in the cyber layer, settled in 0.5 to 3 seconds. Hits, cover and shields don't affect it; later Yate gear may add specific counters as items.
• Starting. The target must have a kernel and be in range (about 12 blocks, more at higher gateway levels). Range only matters at the start.
• Methods. force works on anyone in about 3 s. A method matching one of the target's flaw classes is faster: about 2 s for a minor flaw, 1 s for major, 0.5 s for critical. A wrong guess fails at once and warns the target.
• Speed. The attacker's committed TOPS against the defender's committed TOPS, capped between half and double normal speed, and never under 0.5 s. Only committed TOPS count; a large TOPS total does nothing on its own.
• The attacker commits the TOPS reserved by the program that started the attempt.
Defence
Pays with
Effect
deny()
Buffer
Blocks the attempt; the attacker can't retry on you for about 2 s
resist(tops)
TOPS taken from your other programs, plus Heat
Slows the attempt
delay(seconds)
Little
Buys time for your programs to act
Access the attacker back
Your own TOPS
Forces them to defend too
Reboot
Debuff; all programs stop
Ends attempts and open sessions
Resisting is deliberate, never automatic, so stockpiling TOPS can't replace real defences. Resisting hard also raises your Heat, which makes THERMAL actions hurt more if the attacker gets in anyway.
Sessions last about 6 s at user level. Root actions need s.escalate(), a second, shorter attempt under the same rules. The defender can close a session by spending Buffer, or by rebooting. When a session ends, the same attacker can't access that target again for about 10 s.
Why no setup is perfect: always denying runs out of Buffer; always forcing is slow; flaw-matching needs an inspect first and a wrong guess warns the target; resisting starves your own programs and heats you up; a high level is faster, but capped.
Flaw classes
A flaw is a weak spot: if a target has one and the attacker's method matches it, access is faster. Every flaw comes from the entity's own settings or current state, never from luck, and the rules are public.
Class
Comes from
Severity
memory
verify = "fast" or "skip"
Minor with fast, critical with skip
intake
A high rate_limit
Minor to major, depending on how high
scheduler
schedule = "priority"
Minor; major when running many programs
thermal
Heat above about 70
Major while hot; gone once you cool down
motor
Running me.* body enhancements
Minor with 1–2 running, major with 3 or more
core
Using all your available hook points
Minor
Target's flaw
Matching attempt time
None (use force)
About 3 s
Minor
About 2 s
Major
About 1 s
Critical
About 0.5 s
• Static flaws (memory, intake, scheduler) come from kernel settings and change only when the kernel is edited.
• Dynamic flaws (thermal, motor, core) come from what you are doing right now, and appear and disappear mid-fight. Powering up and resisting hard both raise your exposure.
Seeing your flaws
You always know your own flaws; the skill is managing them, not discovering them.
• On install, the terminal lists the static flaws your settings create, with the cause.
• The flaws command lists static and dynamic flaws, including ones you are close to triggering.
• A HUD icon shows while a dynamic flaw is active.
• sys.flaws() and the on_flaw_changed(class, severity) event let programs react.
• Attackers see the same list through remote.inspect(e).
How flaws are defined
Each class is one data file of rules. A rule says when a setting or state matches, the flaw has a given severity; if several match, the highest wins. Static rules are checked when a kernel is installed, dynamic rules a few times per second. Players can't change the rules; datapack and server authors can. Mobs use the same files.
{
  "id": "edge:memory",
  "rules": [
    { "when": { "setting": "verify", "equals": "fast" }, "severity": "minor" },
    { "when": { "setting": "verify", "equals": "skip" }, "severity": "critical" }
  ]
}
Mobs, randomness and scanning
Mobs run the same kernel model as players; only how their kernel is chosen differs.
• Random mob kernels are a mod option. When it is on, a mob's kernel settings are rolled when it spawns, seeded from a hash of the world seed and the entity's UUID, so any bug can be reproduced from the UUID. Its flaws come from the same analyser as players'.
• When the option is off, every mob runs the Baseline kernel.
• Players never get random weaknesses.
• Scanning takes about 1 second and reads the target's kernel profile, so general programs can branch on it mid-fight.
• Addresses are derived from UUIDs and shown in an IP-like form, such as 10.47.203.9. In combat, players target by crosshair or from a scan list; addresses are for planned actions.
Still open
[ ] How a player becomes LV-1 (proposed: a one-time choice on first joining a world, or a server setting)
[ ] Whether health lost to a lower max health returns when the kernel comes back
[x] The flaw class list
[x] The hook list for the first release
Discussed but not adopted yet: noise and alarm thresholds, attack surface from running services, burned attack signatures, and mobs adapting to overused exploits.
What this replaces
The earlier guard-chain draft in this tab (ports, integrity, rounds) is gone. In the main tab, the Null Gateway, Programs, Script language and Cyber combat sections are out of date wherever they disagree with this tab.