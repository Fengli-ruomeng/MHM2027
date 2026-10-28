-- Field Medic: requires LV2; uses me.regen_burst (LV3).
program {
  name = "Field Medic", type = "resident", tops = 4, level = 2,
  description = "Heals in a burst when health drops below 40%, paid in hunger",
}

function on_start()
  sys.every(1)
end

function on_timer()
  if me.health() < me.max_health() * 0.4 and me.regen_burst(4) then
    sys.notify("Field Medic: +4 health")
  end
end
