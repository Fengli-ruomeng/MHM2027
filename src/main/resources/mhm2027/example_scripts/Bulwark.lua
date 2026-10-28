-- Bulwark: an example kernel. Install it with /edge kernel install Bulwark
-- (/edge kernel install Baseline goes back to the built-in one).
-- It uses 2 hooks, which fits the 2 hook points of an LV0 gateway.
kernel { name = "Bulwark", schedule = "priority", gc = "aggressive", verify = "full", rate_limit = 4 }

-- After a kernel panic, bring the health monitor back up.
function on_panic(reason)
  sys.start("Monitor")
end

-- Out of memory: stop the Spinner first instead of the newest program.
function on_low_ram(free)
  sys.stop("Spinner")
end
