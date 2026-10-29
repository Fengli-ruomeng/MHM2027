MoreHarderMinecraft: Edge - terminal scripts
=============================================

This folder is your gateway's local storage. Every .lua file in it is a script you can run;
the terminal reads the file fresh each time you start it.

Press G (or the key set under Controls) to open the control panel:
  Status      RAM, TOPS, Buffer and Heat
  Processes   what's running and what it costs; run, stop and reboot
  Kernel      the installed kernel; swap kernels (Baseline is built in)
  Log         errors, print and sys.log output

Server operators also have /edge commands for testing and administration.

Examples
  Monitor.lua     LV0  resident: warns when your health is low
  Status.lua      LV0  query: prints RAM, TOPS and programs to the log
  Spinner.lua     LV0  resident: an endless loop that only slows itself
  Adrenaline.lua  LV1  resident: speed burst after a big hit
  Scanner.lua     LV0  resident: scans the nearest gateway and logs its level and flaws
  Bulwark.lua     kernel with on_panic and on_low_ram hooks

Programs start with a header:
  program { name = "Name", type = "resident" | "transient" | "query", tops = 4 }
Kernels start with:
  kernel { name = "Name", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

Program events: on_start, on_stop, on_run, on_timer, on_hit_taken(attacker, amount),
                on_hit_dealt(target, amount), on_kill(target),
                on_flaw_changed(class, severity), on_scanned(by)
Kernel hooks:   on_low_ram(free), on_panic(reason)
                (on_access_request and on_session_opened arrive with cyber combat)

APIs
  me.health() me.max_health() me.hunger() me.pos()                LV0
  me.speed_boost(amount, seconds)                                 LV1
  me.jump_boost(amount, seconds) me.fall_guard(seconds)           LV2
  me.regen_burst(amount)                                          LV3
  me.max_health_boost(amount, seconds)                            LV5
  me.damage_boost(amount, seconds) me.knockback_resist(seconds)   LV6
  sys.ram() sys.tops() sys.heat() sys.leak() sys.buffer() sys.programs()
  sys.start(name) sys.stop(name) sys.sleep(seconds) sys.every(seconds)
  sys.notify(text) sys.log(text) sys.flaws() print(...) chance(percent)
  remote.target() remote.scan(radius) remote.inspect(target)      LV0, programs only

Other entities arrive as handles (from remote.target, remote.scan, or events like
on_hit_taken). Read handle.address, .kind, .name, .is_player, .distance and .level.
