#!/bin/bash
# 秒杀库存验证脚本 — 跑完压测后执行
# 用法: verify-stock.sh <activityId> <initialStock>
# 输出: PASS/FAIL + 详细数据

ACTIVITY_ID=${1:?需传入 activityId}
INITIAL=${2:?需传入初始库存}
REDIS_CLI="redis-cli -h 192.168.147.132"
MYSQL_CMD="mysql -h 192.168.147.132 -ulivemall -plivemall2026 livemall_seckill -N"

echo "============================================="
echo "秒杀最终一致性验证 — activityId=${ACTIVITY_ID}"
echo "============================================="

# 1. DB 订单总量
DB_ORDERS=$($MYSQL_CMD -e "SELECT COUNT(*) FROM t_seckill_order WHERE activity_id=${ACTIVITY_ID};")
echo "DB 订单数:      ${DB_ORDERS}"

# 2. Redis 各分片库存
SHARD_SUM=0
for i in 0 1 2 3; do
  val=$($REDIS_CLI GET "stock:shard:${ACTIVITY_ID}:${i}")
  val=${val:-0}
  SHARD_SUM=$((SHARD_SUM + val))
  echo "  Redis shard ${i}:  ${val}"
done
echo "Redis 剩余库存: ${SHARD_SUM}"

# 3. Redis ordered 用户数
ORDERED_CNT=$($REDIS_CLI EVAL "local k=redis.call('KEYS','ordered:${ACTIVITY_ID}:*'); return #k" 0)
echo "Redis 已扣人数:  ${ORDERED_CNT}"

# 4. 断言 1: DB订单数 == Redis已扣人数
if [ "$DB_ORDERS" -eq "$ORDERED_CNT" ]; then
  echo "[PASS] DB订单数 == Redis已扣人数 (${DB_ORDERS})"
else
  echo "[FAIL] DB订单数 (${DB_ORDERS}) != Redis已扣人数 (${ORDERED_CNT})"
fi

# 5. 断言 2: DB订单 + Redis剩余库存 == 初始库存
TOTAL=$((DB_ORDERS + SHARD_SUM))
if [ "$TOTAL" -eq "$INITIAL" ]; then
  echo "[PASS] 库存守恒: ${INITIAL} = ${DB_ORDERS}(订单) + ${SHARD_SUM}(剩余) = ${TOTAL}"
else
  echo "[FAIL] 库存不守恒: 初始${INITIAL}, 订单${DB_ORDERS}+剩余${SHARD_SUM}=${TOTAL}, 相差$((INITIAL - TOTAL))"
fi

# 6. 断言 3: 重复用户检查
DUP=$($MYSQL_CMD -e "SELECT COUNT(*) FROM (SELECT user_id FROM t_seckill_order WHERE activity_id=${ACTIVITY_ID} GROUP BY user_id HAVING COUNT(*)>1) t;")
if [ "$DUP" -eq 0 ]; then
  echo "[PASS] 无重复用户下单"
else
  echo "[FAIL] 存在 ${DUP} 个用户重复下单!"
fi

echo "============================================="
