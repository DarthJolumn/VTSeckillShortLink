-- seckill_deduct.lua — 3.4.3 用户抢购 / 3.4.4 库存扣减
--
-- KEYS[1] = ordered:{activityId}:{userId}      防重 key
-- KEYS[2] = stock:shard:{activityId}:{shard}   库存分片
-- ARGV[1] = ordered TTL（秒，默认 86400 = 24h）
--
-- 返回值：
--   200 = 扣减成功
--   -1  = 重复下单（ordered key 已存在）
--   -2  = 库存不足（DECR 后 < 0）

local orderedKey = KEYS[1]
local stockKey = KEYS[2]
local orderedTtl = tonumber(ARGV[1])

-- 1. 防重预判
if redis.call('EXISTS', orderedKey) == 1 then
    return -1
end

-- 2. 库存扣减
local remain = redis.call('DECR', stockKey)
if remain < 0 then
    -- 库存不足，回补（INCR 抵消 DECR）+ 返回
    redis.call('INCR', stockKey)
    return -2
end

-- 3. 设置防重 key（24h TTL）
redis.call('SETEX', orderedKey, orderedTtl, '1')

return 200
