# CBOMkit CLI 二进制发布与部署指南

本文面向发布/运维人员，目标是在不交付源码的情况下发布 CBOMkit CLI 二进制包。

## 发布目标

发布物应允许使用者在目标机器上直接运行 CBOM 生成能力：

- Java/Python/Go/C/C++ 源码目录扫描：由 `cbomkit` CLI 内置的 `cbomkit-lib` SDK 完成。
- 目录资产扫描：由外部 `cbomkit-theia` 二进制完成。
- 容器镜像扫描：由外部 `cbomkit-theia` 二进制完成。

当前 Java CLI 构建产物为一个可执行 fat jar：

```text
cbomkit/target/cbomkit-2.0.0-SNAPSHOT-cli.jar
```

运行期要求：JRE/JDK 21 或更高版本。

## 快速打包当前二进制

在 `cbomkit` 仓库中可以直接使用发布脚本组装二进制包。默认采用完整模式，包含源码扫描 CLI 和 `cbomkit-theia`，可用于源码目录、目录资产和容器镜像扫描：

```shell
cd /home/fly2x/cbomsdk/cbomkit
scripts/package-cli.sh \
  --mode full \
  --theia /home/fly2x/cbomsdk/cbomkit-theia/cbomkit-theia
```

输出位置：

```text
dist/cbomkit-cli-<version>/
dist/cbomkit-cli-<version>-<platform>.tar.gz
dist/cbomkit-cli-<version>-<platform>.tar.gz.sha256
```

如果只发布源码扫描能力，可以使用最小模式：

```shell
scripts/package-cli.sh --mode minimal
```

完整模式发布包中的 `bin/cbomkit` 会自动设置 `CBOMKIT_THEIA` 指向包内的 `bin/cbomkit-theia`，使用者无需手动把 `cbomkit-theia` 加入 `PATH`。

## 发布形态

### 方案 A：最小发布包

适合只需要源码扫描的场景。

```text
cbomkit-cli-<version>/
  bin/
    cbomkit
    cbomkit.cmd
  lib/
    cbomkit-cli.jar
  docs/
    cli-user-guide.md
  LICENSE.txt
  NOTICE              # 如项目/依赖合规要求提供
  SHA256SUMS
```

能力范围：

- 支持 `cbomkit source <directory>`。
- 源码扫描支持 Java、Python、Go、C/C++；C/C++ 扫描包含 OpenSSL EVP、legacy API、SSL/TLS 和 PRNG 检测。
- 不包含 `cbomkit-theia`，所以 `dir-assets` 和 `image` 需要使用者额外安装并配置 `cbomkit-theia`。

### 方案 B：完整发布包

适合同时交付源码扫描、目录资产扫描、容器镜像扫描。

```text
cbomkit-cli-<version>/
  bin/
    cbomkit
    cbomkit.cmd
    cbomkit-theia
  lib/
    cbomkit-cli.jar
  docs/
    cli-user-guide.md
  LICENSE.txt
  NOTICE              # 如项目/依赖合规要求提供
  THIRD_PARTY_NOTICES # 如项目/依赖合规要求提供
  SHA256SUMS
```

能力范围：

- 支持 `source`。
- 源码扫描支持 Java、Python、Go、C/C++；C/C++ 扫描包含 OpenSSL EVP、legacy API、SSL/TLS 和 PRNG 检测。
- 支持 `dir-assets`。
- 支持 `image`，但目标机器仍需要 Docker/OCI 访问能力。

如果需要发布多平台包，建议按平台拆分：

```text
cbomkit-cli-<version>-linux-amd64.tar.gz
cbomkit-cli-<version>-linux-arm64.tar.gz
cbomkit-cli-<version>-darwin-arm64.tar.gz
cbomkit-cli-<version>-windows-amd64.zip
```

Java fat jar 跨平台复用，`cbomkit-theia` 需要按 OS/ARCH 分别编译。

## 构建顺序

在集成工作区按本地依赖链构建。以下命令面向发布人员，不需要放入使用者发布包。

```shell
cd /home/fly2x/cbomsdk/cyclonedx-core-java
mvn -DskipTests install

cd /home/fly2x/cbomsdk/sonar-cryptography
mvn -DskipTests install

cd /home/fly2x/cbomsdk/cbomkit-lib
mvn test
mvn -DskipTests install

cd /home/fly2x/cbomsdk/cbomkit
mvn -DskipTests package
```

产物：

```text
/home/fly2x/cbomsdk/cbomkit/target/cbomkit-2.0.0-SNAPSHOT-cli.jar
```

如果发布完整包，还需要在 `cbomkit-theia` 仓库构建平台二进制：

```shell
cd /home/fly2x/cbomsdk/cbomkit-theia
go build -trimpath -ldflags "-s -w" -o cbomkit-theia
```

多平台构建示例：

```shell
GOOS=linux GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o cbomkit-theia-linux-amd64
GOOS=linux GOARCH=arm64 go build -trimpath -ldflags "-s -w" -o cbomkit-theia-linux-arm64
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o cbomkit-theia-windows-amd64.exe
```

## 包装脚本

Linux/macOS `bin/cbomkit`：

```shell
#!/usr/bin/env sh
set -eu

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="java"
fi

exec "$JAVA_BIN" ${CBOMKIT_JAVA_OPTS:-} -jar "$APP_HOME/lib/cbomkit-cli.jar" "$@"
```

Windows `bin/cbomkit.cmd`：

```bat
@echo off
set APP_HOME=%~dp0..
if defined JAVA_HOME (
  set JAVA_BIN=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_BIN=java
)
"%JAVA_BIN%" %CBOMKIT_JAVA_OPTS% -jar "%APP_HOME%\lib\cbomkit-cli.jar" %*
```

大仓库扫描建议给 JVM 分配更多内存：

```shell
export CBOMKIT_JAVA_OPTS="-Xmx6g"
```

## 组装发布包

示例：

```shell
VERSION=2.0.0-SNAPSHOT
DIST=/tmp/cbomkit-cli-${VERSION}

rm -rf "$DIST"
mkdir -p "$DIST/bin" "$DIST/lib" "$DIST/docs"

cp /home/fly2x/cbomsdk/cbomkit/target/cbomkit-${VERSION}-cli.jar "$DIST/lib/cbomkit-cli.jar"
cp /home/fly2x/cbomsdk/cbomkit/docs/cli-user-guide.md "$DIST/docs/"
cp /home/fly2x/cbomsdk/cbomkit/LICENSE.txt "$DIST/"
# 如发布流程要求 NOTICE/THIRD_PARTY_NOTICES，应从合规基线或依赖清单补齐后复制到发布包。

# 如果发布完整包，复制 cbomkit-theia 到 bin/
# cp /path/to/cbomkit-theia "$DIST/bin/cbomkit-theia"

chmod +x "$DIST/bin/cbomkit" 2>/dev/null || true

cd /tmp
find "cbomkit-cli-${VERSION}" -type f -print0 | xargs -0 sha256sum > "cbomkit-cli-${VERSION}/SHA256SUMS"
tar -czf "cbomkit-cli-${VERSION}-linux-amd64.tar.gz" "cbomkit-cli-${VERSION}"
sha256sum "cbomkit-cli-${VERSION}-linux-amd64.tar.gz"
```

## 发布前验收

至少完成以下验收：

```shell
java -version

java -jar lib/cbomkit-cli.jar --help

java -Xmx6g -jar lib/cbomkit-cli.jar source /path/to/java-repo \
  --language java \
  --output /tmp/java-repo.cbom.json

jq -e '.bomFormat == "CycloneDX" and .specVersion == "1.7"' /tmp/java-repo.cbom.json
```

如发布完整包，再验收：

```shell
bin/cbomkit dir-assets /path/to/directory --output /tmp/assets.cbom.json
bin/cbomkit image nginx:latest --output /tmp/image.cbom.json
```

## 发布渠道

推荐发布渠道：

- 内部制品库：Nexus、Artifactory、GitHub Releases、GitLab Package Registry。
- 每个版本保留不可变归档包和 `SHA256SUMS`。
- 重要版本建议增加签名文件，例如 `cosign`、GPG detached signature，或企业内部签名机制。

不要在发布包中包含：

- `.git/`
- `src/`
- Maven/Gradle 源码工程目录
- 本地 Maven 仓库
- 构建缓存、IDE 配置、测试数据中的敏感文件

## 安全与合规注意事项

- fat jar 是 Java 字节码，不是源码；如果有强防逆向要求，可以评估字节码混淆，但混淆可能影响反射、Quarkus、Sonar 分析依赖，必须做完整回归。
- 发布完整包时，`image` 扫描需要访问 Docker socket 或远端镜像源，应按最小权限部署。
- `cbomkit-theia` 会在 `$HOME/.cbomkit-theia` 下创建配置文件，运维侧应明确该目录权限和持久化策略。
- 大仓库扫描需要较高内存，建议文档中给出 `CBOMKIT_JAVA_OPTS="-Xmx6g"` 或更高配置建议。

## 版本记录建议

每个版本记录：

- Git commit 或 tag。
- 构建时间。
- 构建 JDK/Maven/Go 版本。
- 是否包含 `cbomkit-theia`。
- 验收命令和输出摘要。
- 产物 SHA256。
