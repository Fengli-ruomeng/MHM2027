-- Featherfall: requires LV1; uses me.fall_guard (LV2).
program {
  name = "Featherfall", type = "transient", tops = 2, level = 1,
  description = "No fall damage for 8 seconds",
}

function on_run()
  if me.fall_guard(8) then
    sys.notify("Featherfall active for 8 s")
  else
    sys.notify("Featherfall is cooling down")
  end
end
