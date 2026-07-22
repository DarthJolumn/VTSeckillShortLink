#!/bin/bash
# gc-analyze.sh - GC 日志分析脚本
# 用法: ./gc-analyze.sh [g1|zgc]
# 前提: 服务启动时需加 -Xlog:gc*:file=logs/gc-xxx.log 参数

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPORT_DIR="$SCRIPT_DIR/../reports"
GC_TYPE="${1:-g1}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  GC 日志分析 - ${GC_TYPE^^}${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# 查找 GC 日志文件
GC_LOG=""
if [ -f "logs/gc-${GC_TYPE}.log" ]; then
    GC_LOG="logs/gc-${GC_TYPE}.log"
elif [ -f "../logs/gc-${GC_TYPE}.log" ]; then
    GC_LOG="../logs/gc-${GC_TYPE}.log"
elif [ -f "../../logs/gc-${GC_TYPE}.log" ]; then
    GC_LOG="../../logs/gc-${GC_TYPE}.log"
fi

if [ -z "$GC_LOG" ]; then
    echo -e "${RED}未找到 GC 日志文件${NC}"
    echo "请确保服务启动时加了 -Xlog:gc*:file=logs/gc-${GC_TYPE}.log 参数"
    echo ""
    echo "示例启动命令:"
    if [ "$GC_TYPE" = "zgc" ]; then
        echo "  java -XX:+UseZGC -XX:+ZGenerational -Xms4g -Xmx4g \\"
        echo "       -Xlog:gc*:file=logs/gc-zgc.log:time,uptime,level,tags:filecount=5,filesize=50m \\"
        echo "       -jar livemall-*.jar"
    else
        echo "  java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -Xms4g -Xmx4g \\"
        echo "       -Xlog:gc*:file=logs/gc-g1.log:time,uptime,level,tags:filecount=5,filesize=50m \\"
        echo "       -jar livemall-*.jar"
    fi
    exit 1
fi

echo -e "${GREEN}GC 日志: ${GC_LOG}${NC}"
echo ""

# ========== G1 GC 分析 ==========
if [ "$GC_TYPE" = "g1" ]; then
    echo -e "${YELLOW}=== G1 GC 统计 ===${NC}"
    echo ""

    # Young GC 统计
    YOUNG_COUNT=$(grep -c "Pause Young" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  Young GC 次数:    ${YOUNG_COUNT}"

    # Young GC 总耗时
    YOUNG_TIME=$(grep "Pause Young" "$GC_LOG" | awk '{for(i=1;i<=NF;i++) if($i ~ /ms$/) print $i}' | sed 's/ms//' | awk '{sum+=$1} END {printf "%.1f", sum}')
    echo "  Young GC 总耗时:  ${YOUNG_TIME} ms"

    # Full GC 统计
    FULL_COUNT=$(grep -c "Full GC" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  Full GC 次数:     ${FULL_COUNT}"

    # Full GC 总耗时
    FULL_TIME=$(grep "Full GC" "$GC_LOG" | awk '{for(i=1;i<=NF;i++) if($i ~ /ms$/) print $i}' | sed 's/ms//' | awk '{sum+=$1} END {printf "%.1f", sum}')
    echo "  Full GC 总耗时:   ${FULL_TIME} ms"

    # 并发标记触发次数
    IHOP_COUNT=$(grep -c "Initiating Heap Occupancy" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  并发标记触发:     ${IHOP_COUNT}"

    echo ""
    echo -e "${YELLOW}=== Top 5 最长停顿 ===${NC}"
    grep "Pause Young" "$GC_LOG" | awk '{
        for(i=1;i<=NF;i++) {
            if($i ~ /ms$/) {
                gsub(/ms/, "", $i)
                if($i+0 > 0) print $i+0
            }
        }
    }' | sort -rn | head -5 | awk '{printf "  %d. %.1f ms\n", NR, $1}'

    echo ""
    echo -e "${YELLOW}=== 堆内存趋势 ===${NC}"
    grep "Heap" "$GC_LOG" | tail -5 | awk '{
        for(i=1;i<=NF;i++) {
            if($i ~ /Heap/) {
                # 提取堆使用信息
                print "  " $0
                break
            }
        }
    }'

# ========== ZGC 分析 ==========
elif [ "$GC_TYPE" = "zgc" ]; then
    echo -e "${YELLOW}=== ZGC 统计 ===${NC}"
    echo ""

    # GC 次数
    GC_COUNT=$(grep -c "GC" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  GC 总次数:        ${GC_COUNT}"

    # Full GC 次数
    FULL_COUNT=$(grep -c "Full GC" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  Full GC 次数:     ${FULL_COUNT}"

    # Max Pause
    echo ""
    echo -e "${YELLOW}=== Top 5 最长停顿 ===${NC}"
    grep "Max Pause" "$GC_LOG" | awk '{
        for(i=1;i<=NF;i++) {
            if($i ~ /ms$/) {
                gsub(/ms/, "", $i)
                if($i+0 > 0) print $i+0
            }
        }
    }' | sort -rn | head -5 | awk '{printf "  %d. %.2f ms\n", NR, $1}'

    # Heap Used 趋势
    echo ""
    echo -e "${YELLOW}=== 堆内存趋势（最后5条）===${NC}"
    grep "Heap Used" "$GC_LOG" | tail -5 | awk '{
        for(i=1;i<=NF;i++) {
            if($i ~ /Heap/) {
                print "  " $0
                break
            }
        }
    }'

    # Allocation Rate
    echo ""
    echo -e "${YELLOW}=== 分配速率 ===${NC}"
    grep "Allocation Rate" "$GC_LOG" | tail -3 | awk '{
        print "  " $0
    }'

else
    echo -e "${RED}未知 GC 类型: ${GC_TYPE}${NC}"
    echo "支持的类型: g1, zgc"
    exit 1
fi

echo ""
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  分析完成${NC}"
echo -e "${CYAN}============================================${NC}"
