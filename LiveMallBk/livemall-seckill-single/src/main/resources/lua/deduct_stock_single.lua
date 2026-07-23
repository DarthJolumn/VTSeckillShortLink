-- 原子扣库存
-- KEYS[1] = ordered:{activityId}:{userId}
-- ARGV[1] = userId
-- ARGV[2] = shardCount
-- ARGV[3] = activityId
-- 返回: 200=成功, -1=重复下单, -2=库存不足

local ordered = redis.call("GET", KEYS[1])
if ordered then return -1 end

local shard = tonumber(ARGV[1]) % tonumber(ARGV[2])
local stockKey = "stock:shard:" .. ARGV[3] .. ":" .. shard

local stock = redis.call("DECR", stockKey)
if stock >= 0 then
    redis.call("SETEX", KEYS[1], 86400, "1")
    return 200
end
redis.call("INCR", stockKey)
return -2
