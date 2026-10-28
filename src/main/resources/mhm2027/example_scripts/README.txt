MoreHarderMinecraft: Edge - terminal scripts
=============================================

Every .lua file in this folder is a script. Edit them in any editor (VS Code works well);
the terminal reads the file fresh each time you start it.

Commands
  /edge scripts                 list scripts here
  /edge run <name>              start a program
  /edge stop <name>             stop a program
  /edge ps                      what's running and what it costs
  /edge log                     the terminal log (errors, print, sys.log)
  /edge kernel                  show the kernel
  /edge kernel install <name>   swap kernels (Baseline is built in)
  /edge reboot                  stop everything, with a short debuff

Examples
  Monitor.lua     LV0  resident: warns when your health is low
  Status.lua      LV0  query: prints RAM, TOPS and programs to the log
  Spinner.lua     LV0  resident: an endless loop that only slows itself
  Adrenaline.lua  LV1  resident: speed burst after a big hit
  Bulwark.lua     kernel with on_panic and on_low_ram hooks

Programs start with a header:
  program { name = "Name", type = "resident" | "transient" | "query", tops = 4 }
Kernels start with:
  kernel { name = "Name", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

Program events: on_start, on_stop, on_run, on_timer, on_hit_taken(attacker, amount),
                on_hit_dealt(target, amount), on_kill(target)
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
  sys.notify(text) sys.log(text) print(...) chance(percent)
