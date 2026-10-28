-- Adrenaline Shot: requires LV0, but its locked code uses me.speed_boost (LV1).
program {
  name = "Adrenaline Shot", type = "resident", tops = 3, level = 0,
  description = "A speed burst whenever one hit takes 3 or more health",
}

function on_hit_taken(attacker, amount)
  if amount >= 3 then
    me.speed_boost(0.25, 3)
  end
end
