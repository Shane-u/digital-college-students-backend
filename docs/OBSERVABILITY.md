# 监控（Actuator + Prometheus + Grafana）

已集成 Spring Boot Actuator，并新增 Prometheus 指标导出，方便 Prometheus 抓取后用 Grafana 展示。

## 1. 启动后端并验证 Actuator

## 2. 启动 Prometheus + Grafana

启动：

```bash
docker compose -f docker-compose.observability.yml up -d
```

访问：

- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`（默认账号密码：admin / admin）

## 3. Prometheus 抓取配置说明

Prometheus 默认抓取：

- `metrics_path: /api/actuator/prometheus`
- `targets: host.docker.internal:8121`

## 4. Grafana 面板

Grafana 添加 Prometheus 数据源（URL 填 `http://prometheus:9090`），然后可以在 dashboard 市场导入 Spring Boot / JVM 面板。

