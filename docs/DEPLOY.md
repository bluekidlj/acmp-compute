# 部署说明（Docker）

## 1. 构建镜像

```bash
docker build -t acmp-compute:latest .
```

## 2. 运行

```bash
docker run -d -p 8080:8080 \
  -e JWT_SECRET=your-jwt-secret-at-least-256bits \
  -e AES_KEY=acmp32byteskey!!!!!!!!!!!!!!!!! \
  --name acmp-compute \
  acmp-compute:latest
```

- **JWT_SECRET**：JWT 签名密钥，生产环境务必使用足够长的随机字符串。
- **AES_KEY**：用于加密 kubeconfig 的 AES 密钥，必须 **32 字节**（32 个字符）。

## 3. 数据持久化（可选）

当前使用 H2 文件数据库，数据写在容器内。若需持久化，可挂载卷：

```bash
docker run -d -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -e JWT_SECRET=... -e AES_KEY=... \
  acmp-compute:latest
```

需确保 `application.yml` 中 H2 的 `url` 指向 `/app/data`（或通过环境变量覆盖）。

## 4. 健康检查

```bash
curl -s http://localhost:8080/actuator/health
```

若未引入 actuator，可访问 `http://localhost:8080/api/v1/auth/login` 用 POST 测试服务是否可用。
