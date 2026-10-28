-- Monitor: checks your health every 2 seconds and warns you when it's low. Works at LV0.
program { name = "Monitor", type = "resident", tops = 2 }

local warned = false

function on_start()
  sys.every(2)
  sys.log("watching health")
end

function on_timer()
  local hp, max = me.health(), me.max_health()
  if hp <= max * 0.3 and not warned then
    sys.notify("Health low: " .. math.floor(hp) .. " / " .. max)
    warned = true
  elseif hp > max * 0.3 then
    warned = false
  end
end

function on_kill(target)
  sys.log("killed " .. target.name)
end
