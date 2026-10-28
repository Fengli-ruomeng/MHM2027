-- Aegis: a preset kernel. Requires LV2, where a gateway has 2 hook points; as a preset it may use 3.
kernel {
  name = "Aegis", schedule = "fair", gc = "aggressive", verify = "full", rate_limit = 3, level = 2,
  description = "A hardened kernel with three hooks",
}

function on_panic(reason)
  sys.log("Aegis recovered from: " .. reason)
end

-- Out of memory: stop whatever uses the most RAM, not just the newest program.
function on_low_ram(free)
  local biggest, ram = nil, -1
  for _, p in ipairs(sys.programs()) do
    if p.ram > ram then
      biggest, ram = p.name, p.ram
    end
  end
  if biggest then
    sys.stop(biggest)
  end
end

function on_session_opened(who)
  sys.notify("Intrusion detected")
end
