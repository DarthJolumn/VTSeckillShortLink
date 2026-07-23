-- 原子扣库存（Redis Cluster 兼容：所有 key 通过 KEYS 数组传入）
-- KEYS[1] = stock:{activityId}
-- KEYS[2] = ordered:{activityId}:{userId}
-- 返回: 200=成功, -1=重复下单, -2=库存不足, -3=活动未初始化

-- 前置检查：库存 key 不存在说明活动未上架
if redis.call("EXISTS", KEYS[1]) == 0 then
    return -3
end

local ordered = redis.call("GET", KEYS[2])
if ordered then return -1 end

local stock = redis.call("DECR", KEYS[1])
if stock >= 0 then
    redis.call("SETEX", KEYS[2], 86400, "1")
    return 200
end
redis.call("INCR", KEYS[1])
return -2
