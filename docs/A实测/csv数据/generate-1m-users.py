#!/usr/bin/env python3
"""
生成 100 万秒杀压测用户数据
格式: userId,jwtToken,deviceId
"""

import jwt
import base64
from datetime import datetime, timedelta

# JWT 配置（从 application.yml 复制）
SECRET_B64 = "bGl2ZW1hbGwtZGV2LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLTEyMzQ1Njc4OTA="
SECRET = base64.b64decode(SECRET_B64).decode('utf-8')
ALGORITHM = "HS384"  # 从现有 token 的 header 看是 HS384

# 生成 100 万用户
TOTAL_USERS = 10_000_000
OUTPUT_FILE = "D:/workspace/PROJECTS/LiveMall/docs/A实测/csv数据/seckill-users-1m.csv"

# Token 有效期（和现有 token 一致）
TOKEN_TTL_DAYS = 30

def generate_jwt(user_id: int) -> str:
    """生成 JWT token"""
    now = datetime.utcnow()
    payload = {
        "sub": str(user_id),
        "role": 1,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=TOKEN_TTL_DAYS)).timestamp())
    }
    return jwt.encode(payload, SECRET, algorithm=ALGORITHM)

def main():
    print(f"开始生成 {TOTAL_USERS} 个用户...")
    
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        for user_id in range(1, TOTAL_USERS + 1):
            token = generate_jwt(user_id)
            device_id = f"dev-{user_id:05d}"
            f.write(f"{user_id},{token},{device_id}\n")
            
            # 每 10 万用户打印进度
            if user_id % 100_000 == 0:
                print(f"已生成 {user_id:,} 个用户 ({user_id / TOTAL_USERS * 100:.1f}%)")
    
    print(f"✅ 完成！输出文件: {OUTPUT_FILE}")
    print(f"   总用户数: {TOTAL_USERS:,}")

if __name__ == "__main__":
    main()
