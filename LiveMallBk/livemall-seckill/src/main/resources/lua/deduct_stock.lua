-- 原子扣库存（与设计文档 2.3 一致）
-- KEYS[1] = stock:total:{activityId}
-- KEYS[2] = ordered:{activityId}:{userId}
-- ARGV[1] = userId
-- ARGV[2] = shardCount
-- 返回: 200=成功, -1=重复下单, -2=库存不足

local ordered = redis.call("GET", KEYS[2])
if ordered then return -1 end

local shard = tonumber(ARGV[1]) % tonumber(ARGV[2])
local stockKey = "stock:shard:" .. string.sub(KEYS[1], 12) .. ":" .. shard

local stock = redis.call("DECR", stockKey)
if stock >= 0 then
    redis.call("SETEX", KEYS[2], 86400, "1")
    return 200
end
return -2
