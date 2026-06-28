# 🪛 Apache Maven 3.9.9 部署到 C 盘 — 实施指南

> 编制: Senior Developer (高级开发工程师) | 适用: Windows 10/11 + Git Bash MINGW + JDK 17~25

## 🎯 目标

| 项 | 值 |
|---|---|
| **Maven 版本** | Apache Maven 3.9.9（最新版，2024-10-04 发布） |
| **MAVEN_HOME** | `C:\Program Files\Maven\apache-maven-3.9.9` |
| **本地仓库** | `C:\repository`（用户指定，非默认 `~/.m2/repository`） |
| **环境变量** | `MAVEN_HOME`、`M2_HOME`、`MAVEN_OPTS`、PATH 增量 |
| **应用 patch** | MinGW classpath 路径转换 BUG（与项目内的 `.mvn-tool/maven` 一致） |

## 📋 一次性操作清单

### 1. 以管理员身份打开 cmd 或 PowerShell

> 写 `C:\Program Files\` 路径需要管理员权限；`C:\repository` 可以在脚本里直接创建。

### 2. 执行部署脚本

```bat
cd E:\Project\Vue_demo\demand_system
scripts\deploy-maven.bat
```

> 脚本幂等：可重复运行，不会破坏已部署的版本。

### 3. 重新打开终端，使环境变量生效

新开一个 cmd / PowerShell 窗口，验证：

```bat
mvn --version
```

期望输出（关键行）：

```
Apache Maven 3.9.9 (...)
Maven home: C:\Program Files\Maven\apache-maven-3.9.9
Java version: 25, vendor: ...
```

### 4. 配置 `start-all.bat` 优先用系统 Maven

将项目内的 `start-all.bat` 改为：

```bat
REM 优先用 C:\Program Files\Maven\apache-maven-3.9.9
if exist "C:\Program Files\Maven\apache-maven-3.9.9\bin\mvn.cmd" (
    set "MVN_CMD=C:\Program Files\Maven\apache-maven-3.9.9\bin\mvn.cmd"
) else if exist "%~dp0.mvn-tool\maven\bin\mvn.cmd" (
    set "MVN_CMD=%~dp0.mvn-tool\maven\bin\mvn.cmd"
) else (
    set "MVN_CMD=mvn"
)
```

并在所有 `mvn spring-boot:run` 行改用 `%MVN_CMD%`。

### 5. 把项目内嵌 Maven 设为可选（保留作 fallback）

> `E:\Project\Vue_demo\demand_system\.mvn-tool\maven\` 是上一轮的内嵌 Maven，安装完成后不再需要，但建议保留作离线应急。

### 6. 验证一次完整构建

```bat
cd E:\Project\Vue_demo\demand_system\demand_backend
mvn -o compile -DskipTests
```

期望：`BUILD SUCCESS`（首次需要下载依赖到 `C:\repository`，需联网；`-o` 仅在仓库已填充时用）。

## 🔧 关键技术细节

### MinGW classpath BUG（必须 patch）

**症状**：`ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`

**根因**：Maven 3.9.9 的 `bin/mvn` 脚本在 MinGW 分支用 `cygpath --path --windows` 转换路径，但 `--path` 选项把输入当作 PATH 列表（按 `:` 切分），而 `C:\Program Files\Java\jdk25` 包含 `:` 导致切错。

**patch**：

```bash
sed -i 's/cygpath --path --windows/cygpath --windows/g' "$MAVEN_HOME/bin/mvn"
```

部署脚本 `deploy-maven.bat` 第 5 步自动应用此 patch。

### MAVEN_OPTS 解决 jansi 警告

JDK 24+ 的 native access 警告（`Restricted methods ... will be blocked in a future release`）可通过：

```bat
setx MAVEN_OPTS "--enable-native-access=ALL-UNNAMED"
```

消除。**功能不受影响**，只是不雅。

## ❓ FAQ

**Q: 为什么不装到 D 盘或项目内？**

A: 你明确要求 `C:\repository`。`C:\Program Files\Maven\apache-maven-3.9.9` 是 Maven 官方推荐的安装位置（PATH 全局可达，多项目共享）。脚本里已写死，可改 `MAVEN_HOME` 环境变量调整。

**Q: 现有 `C:\apache-maven-3.9.8`（损坏）怎么办？**

A: 旧版位于不同目录，互不影响。后续可手动删除：
```bat
rmdir /s /q "C:\apache-maven-3.9.8"
```

**Q: 装完之后项目内嵌的 `.mvn-tool/maven` 还要保留吗？**

A: 推荐保留作离线 fallback。两套共存：`%MAVEN_HOME%` 优先，`.mvn-tool/maven` 是项目本地副本。

**Q: `mvn` 命令找不到？**

A: PATH 没生效。重新打开 cmd 窗口；或手动：
```bat
set PATH=%PATH%;C:\Program Files\Maven\apache-maven-3.9.9\bin
```

## 📊 部署前后对比

| 维度 | 部署前 | 部署后 |
|------|--------|--------|
| Maven 版本 | 3.9.8（损坏） | 3.9.9（最新） |
| 安装位置 | C:\apache-maven-3.9.8 | C:\Program Files\Maven\apache-maven-3.9.9 |
| 仓库位置 | C:\Users\mervy\.m2\repository | **C:\repository**（用户指定） |
| MinGW patch | 无 | 已应用 |
| 项目内嵌 | .mvn-tool/maven（3.9.9 旧） | 可选保留 |

## 📞 故障排除

如果 `deploy-maven.bat` 失败：

1. **权限不足**：以管理员身份运行
2. **下载失败**：检查网络；可手动下载放到 `%TEMP%`
3. **PATH 没生效**：新开终端；运行 `echo %PATH%` 检查
4. **patch 后仍报错**：检查 `mvn` 脚本里是否残留 `--path --windows`

## 📂 交付物

- `scripts/deploy-maven.bat` — 一键部署脚本（幂等）
- `docs/test-reports/MAVEN_UPGRADE.md` — 上一轮升级报告（参考）
