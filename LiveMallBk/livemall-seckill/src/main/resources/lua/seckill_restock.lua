-- seckill_restock.lua — 3.4.7 / 3.4.8 / 3.4.9 库存回补
--
-- KEYS[1] = order:lock:{type}:{orderNo}        防重锁（type=cancel/refund）
-- KEYS[2] = stock:shard:{activityId}:{shard}   库存分片
-- KEYS[3] = ordered:{activityId}:{userId}      防重 key（解除）
-- ARGV[1] = 防重锁 TTL（秒，默认 10）
--
-- 返回值：
--   200 = 回补成功
--   -3  = 已回补过（防重锁 SETNX 失败）

local lockKey = KEYS[1]
local stockKey = KEYS[2]
local orderedKey = KEYS[3]
local lockTtl = tonumber(ARGV[1])

-- 1. 防重锁 SETNX（保证同一订单只回补一次）
if redis.call('SETNX', lockKey, '1') == 0 then
    return -3
end
redis.call('EXPIRE', lockKey, lockTtl)

-- 2. 库存回补
redis.call('INCR', stockKey)

-- 3. 解除防重 key（允许用户再次下单，但通常活动已结束）
redis.call('DEL', orderedKey)

return 200
