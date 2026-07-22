#!/bin/bash
# run-all.sh - LiveMall JMeter 全量压测脚本
# 用法: ./run-all.sh [g1|zgc]
# 示例: ./run-all.sh g1   # 使用 G1 GC 运行全部测试
#       ./run-all.sh zgc  # 使用 ZGC 运行全部测试

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JMX_DIR="$SCRIPT_DIR/../jmx"
REPORT_DIR="$SCRIPT_DIR/../reports"
GC_TYPE="${1:-g1}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  LiveMall JMeter 全量压测${NC}"
echo -e "${CYAN}  GC 类型: ${GC_TYPE^^}${NC}"
echo -e "${CYAN}============================================${NC}"

# 创建报告目录
mkdir -p "$REPORT_DIR"

# 测试场景列表（按顺序执行）
SCENARIOS=(
    "00-smoke-test:冒烟测试:60"
    "01-auth-register:注册极限:180"
    "02-auth-login:登录极限:180"
    "03-auth-refresh:Token刷新:180"
    "04-user-profile:用户信息:180"
    "05-user-devices:设备管理:180"
    "06-live-rooms:房间列表:180"
    "07-live-room-start:开播极限:180"
    "08-seckill-order:秒杀极限:120"
    "09-seckill-activity:活动查询:180"
    "10-leaderboard-top:排行榜查询:180"
    "13-mixed-load:混合负载:1920"
    "14-full-stress:全链路压力:600"
)

# 记录开始时间
START_TIME=$(date +%s)
LOG_FILE="$REPORT_DIR/run-all-${GC_TYPE}-$(date +%Y%m%d-%H%M%S).log"

echo -e "${YELLOW}开始时间: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${YELLOW}日志文件: ${LOG_FILE}${NC}"
echo ""

# 执行每个场景
for scenario in "${SCENARIOS[@]}"; do
    IFS=':' read -r jmx_name scenario_name duration <<< "$scenario"
    jmx_file="$JMX_DIR/${jmx_name}.jmx"
    jtl_file="$REPORT_DIR/${jmx_name}-${GC_TYPE}-$(date +%Y%m%d%H%M%S).jtl"
    report_dir="$REPORT_DIR/${jmx_name}-${GC_TYPE}-$(date +%Y%m%d%H%M%S)"

    if [ ! -f "$jmx_file" ]; then
        echo -e "${RED}[SKIP] ${scenario_name} - JMX 文件不存在: ${jmx_file}${NC}"
        continue
    fi

    echo -e "${GREEN}[START] ${scenario_name} (${jmx_name})${NC}"
    echo "  JMX: ${jmx_file}"
    echo "  JTL: ${jtl_file}"
    echo "  持续时间: ${duration}s"

    # 执行 JMeter
    jmeter -n \
        -t "$jmx_file" \
        -l "$jtl_file" \
        -j "$REPORT_DIR/${jmx_name}-${GC_TYPE}.log" \
        -e -o "$report_dir" \
        -Jjmeterengine.force.system.exit=true \
        2>&1 | tee -a "$LOG_FILE"

    echo -e "${GREEN}[DONE] ${scenario_name}${NC}"
    echo ""

    # 场景间冷却 30s（让 GC 稳定）
    if [ "$scenario_name" != "冒烟测试" ]; then
        echo -e "${YELLOW}冷却 30s...${NC}"
        sleep 30
    fi
done

# 记录结束时间
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  全部测试完成！${NC}"
echo -e "${CYAN}  总耗时: $((DURATION / 60))m $((DURATION % 60))s${NC}"
echo -e "${CYAN}  报告目录: ${REPORT_DIR}${NC}"
echo -e "${CYAN}============================================${NC}"

# 生成汇总
echo ""
echo -e "${YELLOW}生成 GC 分析...${NC}"
bash "$SCRIPT_DIR/gc-analyze.sh" "$GC_TYPE" 2>/dev/null || echo "GC 分析需要手动运行"

echo -e "${YELLOW}生成 JTL 解析...${NC}"
bash "$SCRIPT_DIR/jtl-parse.sh" "$GC_TYPE" 2>/dev/null || echo "JTL 解析需要手动运行"
