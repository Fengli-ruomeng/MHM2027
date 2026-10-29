-- Scanner: every 3 seconds, finds the nearest gateway and reads its level and flaws. Works at LV0.
-- To get something to scan, give a mob a gateway:
--   /edge set level @e[type=zombie,limit=1,sort=nearest] 2
program { name = "Scanner", type = "resident", tops = 3 }

function on_start()
  sys.every(3)
end

function on_timer()
  local target = remote.scan()[1]
  if not target then
    return
  end
  local info = remote.inspect(target)   -- takes about a second
  if not info then
    return                              -- it moved out of range or is gone
  end
  local found = {}
  for class, severity in pairs(info.flaws) do
    found[#found + 1] = class .. " " .. severity
  end
  sys.log(info.name .. " " .. info.address .. " LV" .. info.level .. ": "
    .. (#found > 0 and table.concat(found, ", ") or "no flaws"))
end

function on_scanned(by)
  sys.notify("Scanned by " .. by.name)
end
