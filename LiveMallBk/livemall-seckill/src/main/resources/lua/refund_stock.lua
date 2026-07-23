-- 回补库存（幂等：ordered key 存在才 INCR，防并发重复回补）
-- KEYS[1] = stock:shard:{activityId}:{shard}
-- KEYS[2] = ordered:{activityId}:{userId}

if redis.call("EXISTS", KEYS[2]) == 1 then
    redis.call("INCR", KEYS[1])
    redis.call("DEL", KEYS[2])
end
return 1
