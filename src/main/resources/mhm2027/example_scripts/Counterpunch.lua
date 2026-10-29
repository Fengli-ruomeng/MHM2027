-- Counterpunch: after a big hit, gets into whoever hit you and freezes them for a moment.
-- Works at LV0 against anything with a gateway. To test on a mob:
--   /edge set level @e[type=zombie,limit=1,sort=nearest] 1
program { name = "Counterpunch", type = "resident", tops = 6 }

function on_hit_taken(attacker, amount)
  if amount < 3 or not attacker or not attacker.level or attacker.level < 0 then
    return                                -- small hit, or no gateway to get into
  end
  local s, why = remote.access(attacker)  -- force: about 3 s, less with more TOPS
  if not s then
    sys.log("no way in: " .. tostring(why))
    return
  end
  s.motor.neutralize(1.5)
  sys.sleep(0.5)
  s.motor.look("up")
  s.close()
end
