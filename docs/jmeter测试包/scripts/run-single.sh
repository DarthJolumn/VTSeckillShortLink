#!/bin/bash
# run-single.sh - 执行单个 JMeter 测试场景
# 用法: ./run-single.sh <jmx-name> [gc-type]
# 示例: ./run-single.sh 08-seckill-order zgc

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JMX_DIR="$SCRIPT_DIR/../jmx"
REPORT_DIR="$SCRIPT_DIR/../reports"

JMX_NAME="${1:?用法: $0 <jmx-name> [gc-type]}"
GC_TYPE="${2:-g1}"

JMX_FILE="$JMX_DIR/${JMX_NAME}.jmx"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
JTL_FILE="$REPORT_DIR/${JMX_NAME}-${GC_TYPE}-${TIMESTAMP}.jtl"
REPORT_DIR="$REPORT_DIR/${JMX_NAME}-${GC_TYPE}-${TIMESTAMP}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

if [ ! -f "$JMX_FILE" ]; then
    echo -e "${RED}错误: JMX 文件不存在: ${JMX_FILE}${NC}"
    echo ""
    echo "可用的测试场景:"
    ls -1 "$JMX_DIR"/*.jmx | xargs -I {} basename {} .jmx | sort
    exit 1
fi

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  LiveMall JMeter 单场景压测${NC}"
echo -e "${CYAN}  场景: ${JMX_NAME}${NC}"
echo -e "${CYAN}  GC:   ${GC_TYPE^^}${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""
echo -e "  JMX:  ${JMX_FILE}"
echo -e "  JTL:  ${JTL_FILE}"
echo -e "  报告: ${REPORT_DIR}"
echo ""

# 执行 JMeter
echo -e "${GREEN}[START] 开始压测...${NC}"
jmeter -n \
    -t "$JMX_FILE" \
    -l "$JTL_FILE" \
    -j "$REPORT_DIR/${JMX_NAME}-${GC_TYPE}.log" \
    -e -o "$REPORT_DIR" \
    -Jjmeterengine.force.system.exit=true

echo ""
echo -e "${GREEN}[DONE] 压测完成！${NC}"
echo -e "  JTL 文件: ${JTL_FILE}"
echo -e "  HTML 报告: ${REPORT_DIR}/index.html"
echo ""

# 解析 JTL
echo -e "${YELLOW}解析结果...${NC}"
if [ -f "$JTL_FILE" ]; then
    echo ""
    echo "=== 聚合统计 ==="
    # 跳过头部注释行，解析 CSV
    tail -n +2 "$JTL_FILE" | awk -F, '
    NR>0 {
        total++
        sum+=$2
        if($2>max) max=$2
        if(min==0 || $2<min) min=$2
        if($3=="true") success++
        else errors++
        # P99 计算（简化）
        times[NR]=$2
    }
    END {
        if(total>0) {
            printf "  总请求数:   %d\n", total
            printf "  成功数:     %d\n", success
            printf "  失败数:     %d\n", errors
            printf "  错误率:     %.2f%%\n", (errors/total)*100
            printf "  平均延迟:   %.0f ms\n", sum/total
            printf "  最大延迟:   %.0f ms\n", max
            printf "  最小延迟:   %.0f ms\n", min
        }
    }'
fi
