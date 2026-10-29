-- Gatekeeper: a defensive kernel. Install it from the panel's Kernel tab.
-- Turns away anyone matching a flaw, slows down forced attempts, and tells you when someone gets in.
-- Two hooks, which fits the 2 hook points of an LV0 gateway.
kernel { name = "Gatekeeper", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

function on_access_request(req)
  if req.method ~= "force" then
    return deny()        -- costs Buffer; they can't retry on you for about 2 s
  end
  return resist(4)       -- holds 4 TOPS and runs you hotter while the attempt lasts
end

function on_session_opened(who)
  sys.notify("Session opened by " .. who.address)
end
