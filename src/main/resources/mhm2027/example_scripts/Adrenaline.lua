-- Adrenaline: after a big hit, run faster for a moment.
-- me.speed_boost needs gateway LV1, so at LV0 this is refused on install.
program { name = "Adrenaline", type = "resident", tops = 4 }

function on_hit_taken(attacker, amount)
  if amount >= 4 then
    me.speed_boost(0.3, 2)   -- 30% faster for 2 seconds
  end
end
