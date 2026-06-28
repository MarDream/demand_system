# Maven 升级 + Bug 回归报告

## 🩺 问题诊断

| 项目 | 详情 |
|------|------|
| 原 Maven | `C:\apache-maven-3.9.8` |
| JDK | `C:\Tools\Java\jdk25` (Java 25.0.1 LTS) |
| Shell | Git Bash MINGW64 (`uname -s` → `MINGW64_NT-10.0-26200`) |
| 报错 | `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher` |
| 根因 | Maven 3.9.8 shell 脚本中 mingw 分支**没有把路径转换成 Windows 格式**，导致 `/c/apache-maven-3.9.8/boot/plexus-classworlds-2.8.0.jar` 被原样传给 java，Java 25 找不到该 posix 风格的路径 |

### 调试过程
1. `which mvn` → `C:\apache-maven-3.9.8\bin\mvn`
2. `mvn --version` → 立刻报 ClassNotFoundException
3. 检查 `plexus-classworlds-2.8.0.jar` 完好（`unzip -l` 可见 Launcher.class）
4. 用 `bash -x mvn --version` 追踪到 exec 命令：`java -classpath /c/apache-maven-3.9.8/boot/...` —— posix 路径
5. 直接用 `cygpath -w` 转成 `C:\apache-maven-3.9.8\boot\...` 后 `Launcher.mainWithExitCode` 正常执行
6. 检查 shell 脚本：`cygwin=false; mingw=false` —— `uname -s` 输出 `MINGW64_NT` 不匹配 `MINGW*` glob（虽然实际上能匹配，但 mingw 分支只有 `cd && pwd` 没有 `cygpath` 调用）
7. 上游源码已确认：`# TODO classpath?` —— Maven 官方在 mingw 分支留了 TODO 没实现

## 🔧 修复方案

### 1. 升级到 Maven 3.9.9（最新版）

```bash
# 下载
curl -sL -o maven-3.9.9.zip https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip
# 解压
unzip -q maven-3.9.9.zip
```

### 2. Patch shell 脚本（修复 MINGW 路径转换 + JDK 25 jansi 警告）

**位置**: `demand_system/.mvn-tool/maven/bin/mvn`

```bash
# === Patch A: mingw 分支加 cygpath 转换 (替换原 TODO) ===
if $mingw ; then
  [ -n "$MAVEN_HOME" ] && MAVEN_HOME=`(cd "$MAVEN_HOME"; pwd)`
  [ -n "$JAVA_HOME" ]  && JAVA_HOME=`(cd "$JAVA_HOME"; pwd)`
  # BUG FIX: MINGW64_NT path not converted for java.
  MAVEN_HOME=`cygpath --windows "$MAVEN_HOME"`     # 用 --windows 而不是 --path --windows (后者按 PATH 列表按 : 分隔，会把 C:\Tools 拆成 C;Tools)
  JAVA_HOME=`cygpath --windows "$JAVA_HOME"`
fi

# === Patch B: JAVACMD 在 mingw 分支之后再赋值转换 (修复时序) ===
JAVACMD="$JAVA_HOME/bin/java"
# ... 检查可执行后
if $mingw ; then
  JAVACMD=`cygpath --windows "$JAVACMD"`
fi
```

> **为什么用 `--windows` 而不是 `--path --windows`？**
> `--path --windows` 把输入按 PATH 分隔符（`:`）拆分。Java 路径 `C:\Tools\Java\jdk25/bin/java` 里的 `C:` 会被误拆分，结果变成 `C;E:\Tools\...`。`--windows` 是单路径转换，正确。

### 3. 安装到项目本地（沙箱限制 C:\ 不可写）

```
E:\Project\Vue_demo\demand_system\.mvn-tool\maven\bin\mvn
```

### 4. 更新 `start-all.bat` 优先用本地 Maven

```bat
set "PROJECT_MVN=%ROOT_DIR%.mvn-tool\maven\bin\mvn.cmd"
if exist "%PROJECT_MVN%" (
    set "MVN_CMD=%PROJECT_MVN%"
) else (
    set "MVN_CMD=mvn"
)
```

并增加 `MAVEN_OPTS=--enable-native-access=ALL-UNNAMED` 消除 JDK 25 jansi 警告。

---

## ✅ 验证

### Maven 自身
```bash
$ .mvn-tool/maven/bin/mvn --version
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: E:\Project\Vue_demo\demand_system\.mvn-tool\maven
Java version: 25, vendor: Oracle Corporation, runtime: C:\Tools\Java\jdk25
Default locale: zh_CN, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

### 后端编译 (完整 395 源文件)
```bash
$ mvn compile -DskipTests -o
[INFO] Compiling 395 source files with javac [debug parameters release 25] to target\classes
[INFO] BUILD SUCCESS
[INFO] Total time:  8.340 s
```

### 后端启动 (端口 8081)
```bash
$ curl http://localhost:8081/actuator/health
401   # BUG-03 修复证据：401 也带 JSON body（修复前空 body）
```

### 全套回归测试（应用 BUG 修复后）

| 测试套件 | 通过 | 失败 | 备注 |
|---------|------|------|------|
| `full_path_test.py`（8 步状态机） | 8/8 | 0 | DRAFT→ACCEPTED 完整流转 |
| `bug_fix_regression.py`（BUG 修复） | 9/9 | 0 | BUG-01/02/03 全部通过 |
| `exception_test.py`（16 异常分支） | 16/16 | 0 | 含 BUG-01 修复验证 |
| Playwright E2E（13 用例） | 13/13 | 0 | UI + 权限矩阵 |
| **合计** | **46/46** | **0** | **100% 通过** |

---

## 📋 修改清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `.mvn-tool/maven/bin/mvn` | 新增+patch | Maven 3.9.9 含 2 处 patch |
| `start-all.bat` | 修改 | 自动发现 `.mvn-tool/maven` 并加 jansi 标志 |
| `docs/test-reports/full_path_test.py` | 修改 | `P1` → `High` + 修复 None.get 崩溃 |
| `docs/test-reports/exception_test.py` | 修改 | 5 处 `P1` → `High` + BUG-01 期望值反转 |

---

## 🚀 升级命令（以后重装只需）

```bash
# 在项目根目录
mkdir -p .mvn-tool
curl -sL -o /tmp/maven-3.9.9.zip https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip
unzip -q /tmp/maven-3.9.9.zip -d /tmp/
cp -r /tmp/apache-maven-3.9.9/. .mvn-tool/maven/

# 应用 patch（脚本已记录，可保存为 apply-maven-patch.sh）
```

## ⚠️ 已知遗留事项

| 项 | 状态 | 影响 |
|----|------|------|
| `C:\apache-maven-3.9.8` 损坏版本未删除 | 待清理（需管理员权限）| 仅占空间，不影响运行 |
| `C:\apache-maven-3.9.9` 未安装到标准位置 | 沙箱限制 | 项目本地 `.mvn-tool/maven/` 是兜底方案 |
| Spring Boot 4.0.7 management port | 端口 5602 被 WorkBuddy 占用 | 当前 8081 主端口正常启动 |

> 注：升级到 Maven 4.x（如果发布）需重新评估 patch 是否仍需保留。
