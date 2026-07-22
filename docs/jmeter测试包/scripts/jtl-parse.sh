#!/bin/bash
# jtl-parse.sh - JMeter JTL 结果解析脚本
# 用法: ./jtl-parse.sh [gc-type]
# 解析 reports/ 目录下所有 JTL 文件，生成对比报告

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPORT_DIR="$SCRIPT_DIR/../reports"
GC_TYPE="${1:-all}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  JMeter JTL 结果解析${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# 查找所有 JTL 文件
if [ "$GC_TYPE" = "all" ]; then
    JTL_FILES=$(find "$REPORT_DIR" -name "*.jtl" -type f 2>/dev/null | sort)
else
    JTL_FILES=$(find "$REPORT_DIR" -name "*-${GC_TYPE}-*.jtl" -type f 2>/dev/null | sort)
fi

if [ -z "$JTL_FILES" ]; then
    echo -e "${RED}未找到 JTL 文件${NC}"
    echo "请先运行压测: ./run-all.sh [g1|zgc]"
    exit 1
fi

echo -e "${YELLOW}找到以下 JTL 文件:${NC}"
echo "$JTL_FILES" | while read f; do
    echo "  $(basename $f)"
done
echo ""

# 解析每个 JTL 文件
echo -e "${YELLOW}=== 各场景性能汇总 ===${NC}"
echo ""
printf "%-35s %8s %8s %8s %8s %8s %8s\n" "场景" "请求数" "成功率" "平均(ms)" "P50(ms)" "P99(ms)" "QPS"
echo "----------------------------------------------------------------------------------------------------"

echo "$JTL_FILES" | while read jtl_file; do
    if [ -z "$jtl_file" ] || [ ! -f "$jtl_file" ]; then
        continue
    fi

    filename=$(basename "$jtl_file" .jtl)
    scenario_name=$(echo "$filename" | sed 's/-[gz][g1]*-[0-9]*.*//')

    # 解析 CSV（跳过头部注释行）
    tail -n +2 "$jtl_file" | awk -F, '
    NR>0 && $2+0 > 0 {
        total++
        sum+=$2
        times[NR]=$2
        if($3=="true" || $3==1) success++
        else errors++
        if($2>max) max=$2
        if(min==0 || $2<min) min=$2
    }
    END {
        if(total>0) {
            # 计算 P50 和 P99
            n=asort(times)
            p50_idx=int(n*0.5)
            p99_idx=int(n*0.99)
            if(p50_idx<1) p50_idx=1
            if(p99_idx<1) p99_idx=1

            # 计算 QPS（基于时间跨度）
            if(NR>1) {
                # 使用第一个和最后一个时间戳
                split($1, first, ",")
                split($NR, last, ",")
            }

            printf "%-35s %8d %7.1f%% %8.0f %8.0f %8.0f %8.1f\n",
                "'"$scenario_name"'",
                total,
                (success/total)*100,
                sum/total,
                times[p50_idx],
                times[p99_idx],
                total/120  # 假设 120s 测试时长
        }
    }'
done

echo ""
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  解析完成${NC}"
echo -e "${CYAN}============================================${NC}"

# G1 vs ZGC 对比
if [ "$GC_TYPE" = "all" ]; then
    echo ""
    echo -e "${YELLOW}=== G1 vs ZGC 对比 ===${NC}"
    echo ""

    GTL_G1=$(find "$REPORT_DIR" -name "*-g1-*.jtl" -type f 2>/dev/null | head -1)
    JTL_ZGC=$(find "$REPORT_DIR" -name "*-zgc-*.jtl" -type f 2>/dev/null | head -1)

    if [ -n "$JTL_G1" ] && [ -n "$JTL_ZGC" ]; then
        echo "  G1:  $(basename $JTL_G1)"
        echo "  ZGC: $(basename $JTL_ZGC)"
        echo ""
        echo "  请分别查看两个 HTML 报告进行对比"
        echo "  报告目录: ${REPORT_DIR}/"
    else
        echo "  需要同时运行 G1 和 ZGC 测试才能对比"
        echo "  用法: ./run-all.sh g1 && ./run-all.sh zgc"
    fi
fi
