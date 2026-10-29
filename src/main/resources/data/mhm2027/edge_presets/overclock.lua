-- Overclock: an Overload preset. Its code runs at LV8, so any gateway within the Overload
-- reach (6 levels) may run it from an Overload socket. The further below LV8, the hotter it runs:
-- an LV4 gateway gains 6 Heat a second and overheats in well under 20 seconds.
program {
  name = "Overclock", type = "resident", tops = 6, level = 8, overload = true,
  description = "Strength, speed and knockback resistance, for as long as the gateway can take the heat",
}

function on_start()
  sys.every(1)
  on_timer()
end

function on_timer()
  me.damage_boost(0.4, 10)
  me.speed_boost(0.3, 10)
  me.knockback_resist(10)
end
