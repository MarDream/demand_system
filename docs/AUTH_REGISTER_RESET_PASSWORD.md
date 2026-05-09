# 认证：注册与密码重置

本文档维护注册、邮箱验证码和密码重置功能的接口、配置与测试说明。

## 数据库

项目只维护一份全局 SQL：`database/init.sql`。

`verification_codes` 表必须在 `database/init.sql` 中维护，不要新增单独的验证码、迁移或临时 SQL 文件。

本地初始化：

```bash
mysql -u root -padmin123 demand_system < database/init.sql
```

验证表结构：

```sql
USE demand_system;
SHOW TABLES LIKE 'verification_codes';
DESC verification_codes;
```

## 邮件配置

开发配置位于 `demand_backend/src/main/resources/application-dev.yml`。

当前开发环境可使用占位配置。邮件发送失败会记录日志，但不阻断验证码生成与本地接口联调。

生产环境需要配置真实 SMTP 服务，密码应使用授权码或应用专用密码，不要提交真实密钥。

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: noreply@example.com
    password: your-mail-authorization-code
```

## 接口

### 发送验证码

`POST /api/v1/auth/send-verification-code`

```json
{
  "email": "user@example.com",
  "type": "register"
}
```

`type` 可选值：

- `register`
- `reset_password`

规则：

- 验证码为 6 位数字。
- 验证码有效期为 10 分钟。
- 注册验证码会校验邮箱未被注册。
- 密码重置验证码会校验邮箱已存在。

### 用户注册

`POST /api/v1/auth/register`

```json
{
  "username": "zhangsan",
  "password": "123456",
  "realName": "张三",
  "email": "zhangsan@example.com",
  "phone": "13800138000",
  "verificationCode": "123456"
}
```

规则：

- 用户名 3-50 个字符，只允许字母、数字和下划线。
- 密码 6-20 个字符。
- 注册成功后返回登录令牌。
- 新注册用户默认为 `inactive`，需要管理员激活。
- 验证码使用后会标记为已使用，不能重复使用。

### 请求密码重置

`POST /api/v1/auth/request-password-reset`

```json
{
  "email": "zhangsan@example.com"
}
```

### 确认密码重置

`POST /api/v1/auth/confirm-password-reset`

```json
{
  "email": "zhangsan@example.com",
  "verificationCode": "123456",
  "newPassword": "newpassword123"
}
```

规则：

- 验证码必须有效且未使用。
- 密码重置成功后，旧 refresh token 失效，用户需要重新登录。

## 快速测试

发送注册验证码：

```bash
curl -X POST http://localhost:8081/api/v1/auth/send-verification-code \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\",\"type\":\"register\"}"
```

注册：

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"testuser\",\"password\":\"123456\",\"realName\":\"测试用户\",\"email\":\"test@example.com\",\"phone\":\"13800138000\",\"verificationCode\":\"123456\"}"
```

请求密码重置：

```bash
curl -X POST http://localhost:8081/api/v1/auth/request-password-reset \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\"}"
```

确认密码重置：

```bash
curl -X POST http://localhost:8081/api/v1/auth/confirm-password-reset \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\",\"verificationCode\":\"123456\",\"newPassword\":\"newpass123\"}"
```

本地没有真实邮箱时，从后端日志或数据库查询验证码：

```sql
SELECT * FROM verification_codes
WHERE email = 'test@example.com'
ORDER BY created_at DESC;
```

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| 邮件发送失败 | 检查 SMTP 配置；本地可查看日志或数据库验证码继续测试 |
| 验证码错误或过期 | 重新发送验证码 |
| 验证码已使用 | 重新发送验证码 |
| 注册后无法登录 | 新用户默认为 `inactive`，需要管理员激活 |
| 邮箱已被注册 | 更换邮箱或走密码重置流程 |

激活测试用户：

```sql
UPDATE users SET status = 'active' WHERE username = 'testuser';
```
