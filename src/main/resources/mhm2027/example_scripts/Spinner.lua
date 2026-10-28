-- Spinner: a loop that never ends, on purpose.
-- It can't freeze the server: every tick it uses its 3 TOPS of budget, gets paused,
-- and carries on next tick. /edge ps shows it as "paused (over budget)".
program { name = "Spinner", type = "resident", tops = 3 }

function on_start()
  local n = 0
  while true do
    n = n + 1
    if n % 20000 == 0 then
      sys.log("still spinning: " .. n)
    end
  end
end
