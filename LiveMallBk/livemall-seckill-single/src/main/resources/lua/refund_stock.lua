-- 回补库存
-- KEYS[1] = stock:shard:{activityId}:{shard}
-- KEYS[2] = ordered:{activityId}:{userId}

redis.call("INCR", KEYS[1])
redis.call("DEL", KEYS[2])
return 1
