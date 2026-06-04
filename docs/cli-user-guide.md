# CBOMkit CLI 使用指南

本文面向二进制包使用者，说明如何使用 CBOMkit CLI 生成 CBOM 文件。

## 运行环境

必须安装：

- Java 21 或更高版本。

可选安装：

- `cbomkit-theia`：用于目录资产扫描和容器镜像扫描。
- Docker 或兼容 OCI 的镜像访问环境：用于 `image` 扫描。
- `jq`：用于命令行检查 JSON 输出。

确认 Java 版本：

```shell
java -version
```

## 发布包目录

完整模式发布包解压后包含：

```text
cbomkit-cli-<version>/
  bin/cbomkit
  bin/cbomkit.cmd
  bin/cbomkit-theia
  lib/cbomkit-cli.jar
  docs/cli-user-guide.md
```

使用完整模式发布包时，`bin/cbomkit` 会自动使用包内的 `bin/cbomkit-theia` 执行目录资产和容器镜像扫描。如果运维侧希望使用外部 Theia 二进制，可以设置 `CBOMKIT_THEIA=/path/to/cbomkit-theia` 或在命令中传入 `--theia /path/to/cbomkit-theia`。

## 查看帮助

如果使用发布包脚本：

```shell
bin/cbomkit --help
```

如果直接使用 jar：

```shell
java -jar lib/cbomkit-cli.jar --help
```

命令分为三类：

```text
source      扫描源码目录，生成源码侧 CBOM
dir-assets  扫描目录中的证书、密钥、secrets、OpenSSL/Java 安全配置等资产
image       扫描容器镜像中的证书、密钥、secrets、OpenSSL/Java 安全配置等资产
```

## 源码目录扫描

最简单用法：

```shell
bin/cbomkit source /path/to/project --output project.cbom.json
```

默认会尝试扫描所有支持语言：Java、Python、Go、C/C++。

如果明确知道项目语言，建议指定语言：

```shell
bin/cbomkit source /path/to/keycloak \
  --language java \
  --output keycloak.cbom.json
```

多语言项目：

```shell
bin/cbomkit source /path/to/repo \
  --language java,python,go,cxx \
  --output repo.cbom.json
```

常用元数据：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --output repo.cbom.json \
  --git-url https://github.com/example/repo.git \
  --revision main \
  --commit abc123
```

扫描子目录：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --subfolder services \
  --output services.cbom.json
```

排除目录或文件：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --exclude '(^|/)src/test/' \
  --exclude '(^|/)testsuite/' \
  --exclude '(^|/)target/' \
  --exclude '/package-info\.java$' \
  --exclude '/module-info\.java$' \
  --output repo.cbom.json
```

## Java 扫描说明

默认 Java 扫描可以直接扫描源码，不要求先构建项目。大型 Java 仓库建议分配更多内存：

```shell
export CBOMKIT_JAVA_OPTS="-Xmx6g"
bin/cbomkit source /path/to/java-repo --language java --output java-repo.cbom.json
```

如果直接运行 jar：

```shell
java -Xmx6g -jar lib/cbomkit-cli.jar source /path/to/java-repo \
  --language java \
  --output java-repo.cbom.json
```

如果希望强制使用 Java 构建产物提升符号解析准确性，可以使用：

```shell
bin/cbomkit source /path/to/java-repo \
  --language java \
  --require-java-build \
  --java-class-dir '/path/to/java-repo/**/target/classes' \
  --java-jar '/path/to/java-repo/**/target/dependency/*.jar' \
  --output java-repo.cbom.json
```

## C/C++ 扫描说明

C/C++ 源码扫描使用 CBOMkit CLI 内置的 `sonar-cryptography` C/C++ 规则能力，目前覆盖 OpenSSL 和 mbedTLS/PSA Crypto。语言参数使用 `cxx`：

```shell
bin/cbomkit source /path/to/cxx-repo \
  --language cxx \
  --output cxx-repo.cbom.json
```

默认索引 `.c`、`.cc`、`.cpp`、`.cxx`、`.h`、`.hh`、`.hpp`、`.hxx` 文件。

对于通过编译宏启用密码库代码的项目，可以重复传入 `--cxx-define`：

```shell
bin/cbomkit source /path/to/cxx-repo \
  --language cxx \
  --cxx-define LWIP_ALTCP=1 \
  --cxx-define LWIP_ALTCP_TLS=1 \
  --output cxx-repo.cbom.json
```

## 目录资产扫描

目录资产扫描依赖 `cbomkit-theia`。

如果 `cbomkit-theia` 已在 `PATH` 中：

```shell
bin/cbomkit dir-assets /path/to/root --output assets.cbom.json
```

如果需要显式指定：

```shell
bin/cbomkit dir-assets /path/to/root \
  --theia /opt/cbomkit/bin/cbomkit-theia \
  --output assets.cbom.json
```

指定插件：

```shell
bin/cbomkit dir-assets /path/to/root \
  --plugin certificates \
  --plugin secrets \
  --output assets.cbom.json
```

忽略路径：

```shell
bin/cbomkit dir-assets /path/to/root \
  --ignore 'testdata/' \
  --ignore '*.tmp' \
  --output assets.cbom.json
```

## 容器镜像扫描

镜像扫描依赖 `cbomkit-theia` 和本地镜像访问能力。

扫描 Docker Hub 镜像：

```shell
bin/cbomkit image nginx:latest --output nginx.cbom.json
```

指定 Docker host：

```shell
bin/cbomkit image nginx:latest \
  --docker-host unix:///var/run/docker.sock \
  --output nginx.cbom.json
```

指定 `cbomkit-theia` 路径：

```shell
bin/cbomkit image nginx:latest \
  --theia /opt/cbomkit/bin/cbomkit-theia \
  --output nginx.cbom.json
```

## 合并/增强已有 CBOM

`dir-assets` 和 `image` 可以基于已有 CBOM 做增强：

```shell
bin/cbomkit dir-assets /path/to/root \
  --bom source.cbom.json \
  --output enriched.cbom.json
```

```shell
bin/cbomkit image nginx:latest \
  --bom source.cbom.json \
  --output enriched-image.cbom.json
```

## 输出校验

检查是否为 CycloneDX CBOM 1.7：

```shell
jq -e '.bomFormat == "CycloneDX" and .specVersion == "1.7"' cbom.json
```

查看组件数量：

```shell
jq '.components | length' cbom.json
```

查看资产类型分布：

```shell
jq -r '.components[]?.cryptoProperties.assetType // "unknown"' cbom.json \
  | sort | uniq -c | sort -nr
```

查看证据位置：

```shell
jq -r '.components[]? | .evidence.occurrences[]? | .location' cbom.json \
  | sort | uniq -c | sort -nr | head
```

## 实际验证示例：Keycloak

示例命令：

```shell
java -Xmx6g -jar lib/cbomkit-cli.jar source /home/fly2x/cbomsdk/keycloak \
  --language java \
  --output /home/fly2x/cbomsdk/keycloak-java.cbom.json \
  --exclude '(^|/)src/test/' \
  --exclude '(^|/)testsuite/' \
  --exclude '(^|/)test-framework/' \
  --exclude '(^|/)target/' \
  --exclude '(^|/)build/' \
  --exclude '/package-info\.java$' \
  --exclude '/module-info\.java$' \
  --git-url https://github.com/keycloak/keycloak.git \
  --revision main \
  --commit 242b96cdc45e3673d09ce9d27ccd7ec827a07c05
```

本地验证结果摘要：

```text
Scanned 5264 files, 646570 lines in 54334 ms
bomFormat: CycloneDX
specVersion: 1.7
components: 58
dependencies: 38
```

## 常见问题

### 是否必须指定 --language

不是必须。默认会尝试扫描 Java、Python、Go。大型单语言仓库建议指定，例如 `--language java`，可以减少无意义扫描并让结果更明确。

### 生成文件为空或组件很少

可能原因：

- 仓库中没有当前语言源码。
- 排除规则过宽。
- Java 项目没有构建产物，部分符号无法解析。
- 目标目录不是预期源码根目录。

### 扫描大仓库内存不足

提高 JVM 内存：

```shell
export CBOMKIT_JAVA_OPTS="-Xmx8g"
```

或直接：

```shell
java -Xmx8g -jar lib/cbomkit-cli.jar source /path/to/repo --language java --output cbom.json
```

### image 或 dir-assets 命令失败

检查：

- `cbomkit-theia` 是否存在且可执行。
- 是否通过 `--theia` 或 `CBOMKIT_THEIA` 指定了路径。
- 扫描镜像时 Docker/OCI 访问是否可用。
- 是否有读取目标目录、Docker socket 或镜像源的权限。
