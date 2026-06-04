#!/bin/bash
# HA Cluster Deployment Verification Script
# Usage: ./verify-ha-cluster.sh

echo "========================================="
echo "  配置中心 HA 集群部署验证"
echo "========================================="
echo ""

PASS=0
FAIL=0

check() {
    local desc="$1"
    local cmd="$2"
    local expected="$3"

    result=$(eval "$cmd" 2>/dev/null)
    if echo "$result" | grep -q "$expected"; then
        echo "[PASS] $desc"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $desc (got: $result)"
        FAIL=$((FAIL + 1))
    fi
}

echo "--- 1. 节点健康检查 ---"
check "Server Node-1 健康" "curl -s http://localhost:8081/actuator/health | head -1" "UP"
check "Server Node-2 健康" "curl -s http://localhost:8082/actuator/health | head -1" "UP"
check "Server Node-3 健康" "curl -s http://localhost:8083/actuator/health | head -1" "UP"
check "Nginx LB 响应" "curl -s -o /dev/null -w '%{http_code}' http://localhost/nginx-health" "200"

echo ""
echo "--- 2. 数据库连接验证 ---"
check "MySQL Primary 连接" "docker exec config-mysql-primary mysqladmin ping -h localhost -uroot -proot123 2>/dev/null" "alive"
check "MySQL Replica 连接" "docker exec config-mysql-replica mysqladmin ping -h localhost -uroot -proot123 2>/dev/null" "alive"

echo ""
echo "--- 3. RabbitMQ 集群验证 ---"
check "RabbitMQ-1 运行" "docker exec config-rabbitmq-1 rabbitmq-diagnostics check_port_connectivity 2>/dev/null | head -1" "ok"
check "RabbitMQ-2 运行" "docker exec config-rabbitmq-2 rabbitmq-diagnostics check_port_connectivity 2>/dev/null | head -1" "ok"

echo ""
echo "--- 4. 负载均衡分发验证 ---"
check "LB API 转发" "curl -s -o /dev/null -w '%{http_code}' http://localhost/api/version?env=dev&ns=default" "200"
check "LB Polling 转发" "curl -s -o /dev/null -w '%{http_code}' 'http://localhost/api/polling?env=dev&ns=default&clientVersion=0'" "200"

echo ""
echo "--- 5. Prometheus 监控验证 ---"
check "Prometheus 运行" "curl -s -o /dev/null -w '%{http_code}' http://localhost:9090/-/ready" "200"
check "Node-1 指标暴露" "curl -s http://localhost:8081/actuator/prometheus | head -5" "jvm"
check "Node-2 指标暴露" "curl -s http://localhost:8082/actuator/prometheus | head -5" "jvm"
check "Node-3 指标暴露" "curl -s http://localhost:8083/actuator/prometheus | head -5" "jvm"

echo ""
echo "--- 6. 故障转移验证 ---"
echo "[INFO] 停止 Node-1..."
docker stop config-server-1 > /dev/null 2>&1
sleep 5
check "Node-1 停止后 LB 仍可用" "curl -s -o /dev/null -w '%{http_code}' http://localhost/api/version?env=dev&ns=default" "200"
echo "[INFO] 恢复 Node-1..."
docker start config-server-1 > /dev/null 2>&1
sleep 10
check "Node-1 恢复后健康" "curl -s http://localhost:8081/actuator/health | head -1" "UP"

echo ""
echo "========================================="
echo "  验证结果: PASS=$PASS, FAIL=$FAIL"
echo "========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
exit 0
