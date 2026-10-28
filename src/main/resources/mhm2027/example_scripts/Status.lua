-- Status: a query program. It reads your terminal into the log and exits.
-- Run it, then check /edge log.
program { name = "Status", type = "query", tops = 2 }

function on_run()
  local free, total = sys.ram()
  local topsFree, topsTotal = sys.tops()
  print("RAM free:", math.floor(free), "of", total, "MB")
  print("TOPS free:", topsFree, "of", topsTotal)
  for _, p in ipairs(sys.programs()) do
    print(p.name, p.type, p.ram .. " MB", p.tops .. " TOPS", p.state)
  end
end
