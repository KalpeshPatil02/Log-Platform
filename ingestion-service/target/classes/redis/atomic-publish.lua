-- KEYS[1] = stream; KEYS[2..n+1] = dedup keys
-- ARGV[1] = ttl seconds; ARGV[2] = number of records; ARGV[3..] = JSON record maps
-- Lua scripts are atomic with respect to other clients, but Redis does not roll back
-- writes after a runtime error. pcall + compensating XDEL/DEL handles command errors.
local n = tonumber(ARGV[2])
local ttl = tonumber(ARGV[1])
local streamType = redis.call('TYPE', KEYS[1]).ok
if streamType ~= 'none' and streamType ~= 'stream' then
  return redis.error_reply('RAW_STREAM_WRONG_TYPE')
end
for i = 1, n do
  local t = redis.call('TYPE', KEYS[i + 1]).ok
  if t ~= 'none' and t ~= 'string' then
    return redis.error_reply('DEDUP_KEY_WRONG_TYPE')
  end
end

local ids = {}
local appended = {}
local deduped = {}
local accepted = 0
local duplicates = 0

for i = 1, n do
  local dk = KEYS[i + 1]
  if redis.call('EXISTS', dk) == 1 then
    duplicates = duplicates + 1
  else
    local record = cjson.decode(ARGV[i + 2])
    local fields = {}
    for k, v in pairs(record) do
      table.insert(fields, k)
      table.insert(fields, tostring(v))
    end
    local result = redis.pcall('XADD', KEYS[1], '*', unpack(fields))
    if type(result) == 'table' and result.err then
      for _, streamId in ipairs(appended) do redis.pcall('XDEL', KEYS[1], streamId) end
      for _, key in ipairs(deduped) do redis.pcall('DEL', key) end
      return redis.error_reply('ATOMIC_PUBLISH_FAILED')
    end
    local setResult = redis.pcall('SET', dk, '1', 'EX', ttl)
    if type(setResult) == 'table' and setResult.err then
      redis.pcall('XDEL', KEYS[1], result)
      for _, streamId in ipairs(appended) do redis.pcall('XDEL', KEYS[1], streamId) end
      for _, key in ipairs(deduped) do redis.pcall('DEL', key) end
      return redis.error_reply('ATOMIC_PUBLISH_FAILED')
    end
    table.insert(appended, result)
    table.insert(deduped, dk)
    accepted = accepted + 1
    table.insert(ids, record.eventId)
  end
end
return {accepted, duplicates}
