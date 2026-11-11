#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p out
javac -encoding UTF-8 -d out \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java" \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/TimeServiceClient_CONSOLE_uses_library.java"
# 明确移除 Java 隐式启动选项；不设置任何代理参数。
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
stamp=$(date +%Y%m%d-%H%M%S)
java -cp out TimeServiceClient_CONSOLE_uses_library.TimeServiceClient_CONSOLE_uses_library \
  ntp.aliyun.com 10 5000 123 2000 "实验1/重新实验/直连-${stamp}.csv" \
  | tee "实验1/重新实验/直连-${stamp}.txt"

python3 "实验1/tests/audit_ntp_log.py" "实验1/重新实验/直连-${stamp}.csv"
