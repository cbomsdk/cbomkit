# CBOMkit CLI 使用指南

本文面向二进制包使用者，说明如何使用 CBOMkit CLI 从源码目录、普通目录和容器镜像生成 CycloneDX CBOM 1.7 JSON 文件。重点覆盖源码扫描，尤其是 C/C++ 扫描时如何配置宏、为什么需要配置宏、如何跳过文件，以及各参数的含义。

## 适用场景

CBOMkit CLI 适合以下场景：

- 本地或 CI 中已有源码目录，需要生成源码侧 CBOM。
- 需要扫描目录或容器镜像中的证书、密钥、secrets、OpenSSL/Java 安全配置等资产。
- 不想启动 CBOMkit Web 服务，只需要命令行输出 JSON。

源码扫描支持的语言参数为：

| 语言 | `--language` 值 | 扫描文件 | 主要覆盖范围 |
| --- | --- | --- | --- |
| Java | `java` | `.java` | JCA、BouncyCastle light-weight API |
| Python | `python` | `.py` | `pyca/cryptography` |
| Go | `go` | `.go`、`go.mod` | Go 标准库 `crypto`，以及部分 `golang.org/x/crypto` |
| C/C++ | `cxx` | `.c`、`.cc`、`.cpp`、`.cxx`、`.h`、`.hh`、`.hpp`、`.hxx` | OpenSSL、mbedTLS/PSA Crypto、OpenHiTLS |

注意：C/C++ 的语言值是 `cxx`，不是 `c`、`cpp` 或 `c++`。

## 运行环境

必须安装：

- Java 21 或更高版本。

可选安装：

- `cbomkit-theia`：用于 `dir-assets` 和 `image` 扫描。
- Docker 或兼容 OCI 的镜像访问环境：用于 `image` 扫描。
- `jq`：用于命令行检查 JSON 输出。

确认 Java 版本：

```shell
java -version
```

大型仓库建议给 CLI 分配更多 JVM 内存。如果使用发布包脚本：

```shell
export CBOMKIT_JAVA_OPTS="-Xmx6g"
```

如果直接运行 jar：

```shell
java -Xmx6g -jar lib/cbomkit-cli.jar --help
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

使用完整模式发布包时，`bin/cbomkit` 会自动使用包内的 `bin/cbomkit-theia` 执行目录资产和容器镜像扫描。如果运维侧希望使用外部 Theia 二进制，可以设置 `CBOMKIT_THEIA=/path/to/cbomkit-theia`，也可以在命令中传入 `--theia /path/to/cbomkit-theia`。

## 基本使用步骤

1. 确认 Java 版本和 CLI 文件。

```shell
java -version
bin/cbomkit --help
```

2. 选择扫描命令。

```text
source      扫描源码目录，生成源码侧 CBOM
dir-assets  扫描目录中的证书、密钥、secrets、OpenSSL/Java 安全配置等资产
image       扫描容器镜像中的证书、密钥、secrets、OpenSSL/Java 安全配置等资产
```

3. 源码扫描时先确定语言。

```shell
bin/cbomkit source /path/to/repo --language java --output repo.cbom.json
```

如果不传 `--language`，CLI 会尝试扫描所有支持语言：Java、Python、Go、C/C++。大型仓库建议显式指定语言，减少无意义索引和扫描。

4. 配置跳过文件。

源码扫描使用 `--exclude <regex>`，匹配源码目录下的相对路径。目录资产和镜像扫描使用 `--ignore <glob>`，该参数会转发给 `cbomkit-theia`。二者语义不同，不能混用。

5. C/C++ 项目先确认真实构建宏。

如果密码库代码被 `#if`、`#ifdef`、`#ifndef` 保护，需要用 `--cxx-define` 打开目标构建会启用的宏。只打开真实产品配置里的宏，不要为了“多扫一点”随意打开互斥宏。

6. 运行扫描并校验输出。

```shell
jq -e '.bomFormat == "CycloneDX" and .specVersion == "1.7"' repo.cbom.json
jq '.components | length' repo.cbom.json
```

## 查看帮助

如果使用发布包脚本：

```shell
bin/cbomkit --help
```

如果直接使用 jar：

```shell
java -jar lib/cbomkit-cli.jar --help
```

当前命令格式：

```text
cbomkit source <directory> [--output bom.json] [--language java,python,go,cxx]
    [--exclude <regex>] [--java-jar <path-or-glob>] [--java-class-dir <dir>]
    [--cxx-define <NAME[=VALUE]>] [--require-java-build] [--subfolder <path>]

cbomkit dir-assets <directory> [--output bom.json] [--theia /path/cbomkit-theia]
    [--bom source.cbom.json] [--plugin <name>] [--ignore <glob>]

cbomkit image <image-ref> [--output bom.json] [--theia /path/cbomkit-theia]
    [--bom source.cbom.json] [--plugin <name>] [--ignore <glob>] [--docker-host <uri>]
```

## 源码扫描

最简单用法：

```shell
bin/cbomkit source /path/to/project --output project.cbom.json
```

指定单一语言：

```shell
bin/cbomkit source /path/to/project \
  --language java \
  --output project.cbom.json
```

指定多种语言：

```shell
bin/cbomkit source /path/to/repo \
  --language java,python,go,cxx \
  --output repo.cbom.json
```

也可以重复传 `--language`：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --language cxx \
  --output repo.cbom.json
```

写入常用元数据：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --output repo.cbom.json \
  --git-url https://github.com/example/repo.git \
  --revision main \
  --commit abc123
```

只扫描仓库子目录：

```shell
bin/cbomkit source /path/to/repo \
  --language java \
  --subfolder services/payment \
  --output payment.cbom.json
```

`--subfolder` 会把实际扫描根目录限制到该子目录。`--exclude` 仍然匹配实际扫描根目录下的相对路径。

## 跳过文件和目录

源码扫描使用 `--exclude <regex>`，可以重复传入。CLI 会把这些正则传给源码索引器，只要相对路径匹配任意一个正则，该文件或目录就不会进入扫描。

常用规则：

```shell
bin/cbomkit source /path/to/repo \
  --language java,python,go,cxx \
  --exclude '(^|/)src/test/' \
  --exclude '(^|/)tests?/' \
  --exclude '(^|/)testdata/' \
  --exclude '(^|/)target/' \
  --exclude '(^|/)build/' \
  --exclude '(^|/)dist/' \
  --exclude '(^|/)vendor/' \
  --exclude '/package-info\.java$' \
  --exclude '/module-info\.java$' \
  --exclude '_test\.go$' \
  --output repo.cbom.json
```

建议：

- 跳过测试目录、构建产物目录、虚拟环境、下载缓存、第三方 vendor 目录，可以降低扫描时间和噪声。
- 不要跳过生产源码依赖的公共头文件目录，尤其是 C/C++ 项目中的 `include/`、`src/include/`。
- 正则过宽会直接造成漏报。先用较少规则扫描一次，再根据输出和扫描时间逐步收紧。
- 在 Linux/macOS shell 中用单引号包住正则，避免 `*`、`\` 被 shell 提前解释。

常见按语言排除示例：

```shell
# Java
--exclude '(^|/)src/test/' \
--exclude '(^|/)target/' \
--exclude '/package-info\.java$' \
--exclude '/module-info\.java$'
```

```shell
# Python
--exclude '(^|/)tests?/' \
--exclude '(^|/)\.venv/' \
--exclude '(^|/)venv/' \
--exclude '(^|/)build/' \
--exclude '(^|/)dist/'
```

```shell
# Go
--exclude '(^|/)testdata/' \
--exclude '_test\.go$' \
--exclude '(^|/)vendor/'
```

```shell
# C/C++
--exclude '(^|/)test/' \
--exclude '(^|/)tests/' \
--exclude '(^|/)build/' \
--exclude '(^|/)out/' \
--exclude '(^|/)third_party/' \
--exclude '.*_test\.(c|cc|cpp|cxx)$'
```

目录资产和镜像扫描不使用 `--exclude`，而是使用 `--ignore <glob>`：

```shell
bin/cbomkit dir-assets /path/to/root \
  --ignore 'testdata/' \
  --ignore '*.tmp' \
  --output assets.cbom.json
```

## Java 扫描

Java 扫描可直接扫描源码。默认不要求先构建项目，但没有 class 文件和依赖 jar 时，符号解析准确性会降低。大型 Java 仓库建议先增加 JVM 内存：

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

如果希望扫描必须使用构建产物，传 `--require-java-build`，并提供 class 目录和依赖 jar：

```shell
bin/cbomkit source /path/to/java-repo \
  --language java \
  --require-java-build \
  --java-class-dir /path/to/java-repo/service-a/target/classes \
  --java-class-dir /path/to/java-repo/service-b/target/classes \
  --java-jar '/path/to/java-repo/service-a/target/dependency/*.jar' \
  --java-jar '/path/to/java-repo/service-b/target/dependency/*.jar' \
  --output java-repo.cbom.json
```

说明：

- `--java-class-dir`：编译后的 `.class` 所在目录，可以重复传。建议传具体目录。
- `--java-jar`：依赖 jar 或 jar glob，可以重复传。
- `--require-java-build`：如果没有提供 class 目录或 jar，扫描会失败，适合 CI 中要求高准确性的场景。

Java 示例：

```shell
bin/cbomkit source /src/keycloak \
  --language java \
  --output keycloak-java.cbom.json \
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

## Python 扫描

Python 扫描索引 `.py` 文件，主要识别 `pyca/cryptography` 相关用法。通常不需要先构建项目。

Python 示例：

```shell
bin/cbomkit source /src/python-service \
  --language python \
  --output python-service.cbom.json \
  --exclude '(^|/)tests?/' \
  --exclude '(^|/)\.venv/' \
  --exclude '(^|/)venv/' \
  --exclude '(^|/)build/' \
  --exclude '(^|/)dist/' \
  --git-url https://github.com/example/python-service.git \
  --revision main \
  --commit abc123
```

如果同一个仓库里有多个 Python 包，可以直接扫描仓库根目录，也可以用 `--subfolder` 只扫描某个包：

```shell
bin/cbomkit source /src/mono-repo \
  --language python \
  --subfolder packages/crypto-client \
  --output crypto-client-python.cbom.json \
  --exclude '(^|/)tests?/'
```

## Go 扫描

Go 扫描索引 `.go` 和 `go.mod` 文件，主要识别 Go 标准库 `crypto`，以及部分 `golang.org/x/crypto` 用法。通常不需要先执行 `go build`。

Go 示例：

```shell
bin/cbomkit source /src/go-service \
  --language go \
  --output go-service.cbom.json \
  --exclude '(^|/)testdata/' \
  --exclude '_test\.go$' \
  --exclude '(^|/)vendor/' \
  --git-url https://github.com/example/go-service.git \
  --revision main \
  --commit abc123
```

如果项目把测试辅助代码放在普通目录中，需要额外排除：

```shell
bin/cbomkit source /src/go-service \
  --language go \
  --exclude '(^|/)internal/testutil/' \
  --exclude '_test\.go$' \
  --output go-service.cbom.json
```

## C/C++ 扫描

C/C++ 源码扫描使用 `cxx` 语言值：

```shell
bin/cbomkit source /path/to/cxx-repo \
  --language cxx \
  --output cxx-repo.cbom.json
```

默认索引以下扩展名：

```text
.c .cc .cpp .cxx .h .hh .hpp .hxx
```

当前 C/C++ 规则聚合了 OpenSSL、mbedTLS/PSA Crypto、OpenHiTLS 相关检测规则。覆盖的资产类型包括摘要、对称加密、MAC、KDF、密钥生成、密钥协商、签名、随机数、TLS/SSL 配置等。具体能否检出，取决于源码中相关调用是否在预处理后仍然可见。

### 为什么 C/C++ 需要打开宏

C/C++ 项目大量使用条件编译：

```c
#if USE_MBEDTLS
void guarded_crypto(void) {
    mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    mbedtls_ssl_setup(ssl, conf);
}
#endif
```

如果没有定义 `USE_MBEDTLS`，预处理后这段代码不可见，扫描器就不会检测到其中的 mbedTLS 调用。传入：

```shell
--cxx-define USE_MBEDTLS=1
```

等价于告诉扫描器按目标构建配置处理：

```c
#define USE_MBEDTLS 1
```

这样被 `#if USE_MBEDTLS` 保护的代码才会进入 C/C++ AST，相关密码资产才可能被检出。

宏的作用边界：

- `--cxx-define` 只影响扫描器的 C/C++ 预处理配置。
- 它不会编译项目，不会链接库，也不会自动读取 CMake 的所有编译参数。
- 它不会补全 include 路径或生成缺失头文件。
- 如果打开了目标产品不会启用的宏，可能扫描到未编译进产品的代码，导致过报。
- 如果漏掉了目标产品实际启用的宏，可能跳过真实代码路径，导致漏报。

### 怎么选择应该打开哪些宏

优先按真实构建配置取宏，顺序如下：

1. `compile_commands.json` 中的 `-DNAME`、`-DNAME=VALUE`。
2. CMake 中的 `target_compile_definitions()`、`add_definitions()`。
3. Makefile、Ninja、构建脚本中的 `CPPFLAGS`、`CFLAGS`、`CXXFLAGS`。
4. 项目配置头文件，例如 `config.h`、`lwipopts.h`、`mbedtls_config.h`。
5. 产品或板级配置文档。

不要把所有看起来相关的宏都打开。更稳妥的做法是按一个目标构建配置扫描一次；如果同一仓库有多个产品配置，就分别扫描：

```shell
bin/cbomkit source /src/firmware --language cxx \
  --cxx-define PRODUCT_A=1 \
  --cxx-define USE_MBEDTLS=1 \
  --output firmware-product-a.cbom.json

bin/cbomkit source /src/firmware --language cxx \
  --cxx-define PRODUCT_B=1 \
  --cxx-define USE_OPENSSL=1 \
  --output firmware-product-b.cbom.json
```

### `--cxx-define` 写法

可以重复传入：

```shell
bin/cbomkit source /path/to/cxx-repo \
  --language cxx \
  --cxx-define USE_MBEDTLS=1 \
  --cxx-define MBEDTLS_SSL_TLS_C \
  --cxx-define PSA_WANT_ALG_SHA_256=1 \
  --output cxx-repo.cbom.json
```

支持的形式：

| 写法 | 含义 |
| --- | --- |
| `--cxx-define NAME` | 等价于 `#define NAME` |
| `--cxx-define NAME=1` | 等价于 `#define NAME 1` |
| `--cxx-define NAME=VALUE` | 等价于 `#define NAME VALUE` |

如果值中包含 shell 特殊字符，请加引号：

```shell
--cxx-define 'OPENSSL_VERSION_NUMBER=0x30000000L'
```

### 常见 C/C++ 宏配置示例

lwIP 使用 mbedTLS 的 altcp TLS 路径时，相关代码通常受 `LWIP_ALTCP`、`LWIP_ALTCP_TLS`、`LWIP_ALTCP_TLS_MBEDTLS` 控制。扫描这类代码时可以按目标配置打开：

```shell
bin/cbomkit source /src/lwip \
  --language cxx \
  --cxx-define LWIP_TCP=1 \
  --cxx-define LWIP_ALTCP=1 \
  --cxx-define LWIP_ALTCP_TLS=1 \
  --cxx-define LWIP_ALTCP_TLS_MBEDTLS=1 \
  --output lwip-cxx.cbom.json
```

如果 HTTPS 示例还被项目自定义宏保护，例如 `LWIP_HTTPD_EXAMPLE_HTTPS`，也需要按真实配置打开：

```shell
--cxx-define LWIP_HTTPD_EXAMPLE_HTTPS=1
```

mbedTLS/PSA Crypto 项目中，很多功能由配置宏控制。只打开目标配置启用的宏，例如：

```shell
bin/cbomkit source /src/firmware \
  --language cxx \
  --cxx-define USE_MBEDTLS=1 \
  --cxx-define MBEDTLS_SSL_TLS_C \
  --cxx-define MBEDTLS_CIPHER_C \
  --cxx-define MBEDTLS_MD_C \
  --cxx-define MBEDTLS_PSA_CRYPTO_C \
  --cxx-define PSA_WANT_ALG_SHA_256=1 \
  --output firmware-mbedtls.cbom.json
```

OpenSSL 项目中，通常不需要打开 `OPENSSL_NO_*`。这些宏表示禁用某功能，除非目标构建确实禁用了该功能，否则不要传它们。涉及版本判断时，可以按目标 OpenSSL 版本传入：

```shell
bin/cbomkit source /src/openssl-user \
  --language cxx \
  --cxx-define 'OPENSSL_VERSION_NUMBER=0x30000000L' \
  --output openssl-user.cbom.json
```

OpenHiTLS 项目中，按项目实际配置打开相关 `HITLS_*` 或产品宏即可：

```shell
bin/cbomkit source /src/openhitls-user \
  --language cxx \
  --cxx-define USE_OPENHITLS=1 \
  --output openhitls-user.cbom.json
```

### C/C++ 示例

扫描一个包含 lwIP 和 mbedTLS 的 C 项目：

```shell
bin/cbomkit source /src/embedded-net \
  --language cxx \
  --output embedded-net-cxx.cbom.json \
  --exclude '(^|/)test/' \
  --exclude '(^|/)tests/' \
  --exclude '(^|/)build/' \
  --exclude '(^|/)out/' \
  --exclude '(^|/)third_party/unrelated-lib/' \
  --cxx-define LWIP_TCP=1 \
  --cxx-define LWIP_ALTCP=1 \
  --cxx-define LWIP_ALTCP_TLS=1 \
  --cxx-define LWIP_ALTCP_TLS_MBEDTLS=1 \
  --cxx-define MBEDTLS_SSL_TLS_C \
  --cxx-define MBEDTLS_CIPHER_C \
  --cxx-define MBEDTLS_MD_C
```

如果源码中有多套互斥 TLS 后端，例如 OpenSSL 和 mbedTLS，不建议一次同时打开：

```shell
# 推荐：按实际产品配置分别扫描
bin/cbomkit source /src/product --language cxx \
  --cxx-define USE_MBEDTLS=1 \
  --output product-mbedtls.cbom.json

bin/cbomkit source /src/product --language cxx \
  --cxx-define USE_OPENSSL=1 \
  --output product-openssl.cbom.json
```

## 目录资产扫描

目录资产扫描依赖 `cbomkit-theia`。它扫描的是文件系统资产，不是源码语言规则。

如果 `cbomkit-theia` 已在 `PATH` 中，或使用完整模式发布包：

```shell
bin/cbomkit dir-assets /path/to/root --output assets.cbom.json
```

显式指定 Theia 二进制：

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

指定 Theia 二进制：

```shell
bin/cbomkit image nginx:latest \
  --theia /opt/cbomkit/bin/cbomkit-theia \
  --output nginx.cbom.json
```

忽略镜像内路径：

```shell
bin/cbomkit image nginx:latest \
  --ignore '/var/cache/*' \
  --ignore '*.tmp' \
  --output nginx.cbom.json
```

## 合并或增强已有 CBOM

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

## 参数含义

### `source` 参数

| 参数 | 是否必需 | 含义 |
| --- | --- | --- |
| `<directory>` | 是 | 要扫描的源码目录。必须存在且是目录。 |
| `--output <file>` | 否 | 输出 CBOM JSON 文件。不传时输出到标准输出。 |
| `--language <values>` | 否 | 指定语言，支持 `java`、`python`、`go`、`cxx`，可用逗号分隔或重复传。 |
| `--exclude <regex>` | 否 | 跳过匹配相对路径的源码文件或目录，可重复传。 |
| `--subfolder <path>` | 否 | 只扫描 `<directory>` 下的某个子目录。 |
| `--git-url <url>` | 否 | 写入 CBOM metadata 的源码仓库 URL。 |
| `--revision <name>` | 否 | 写入 CBOM metadata 的分支、tag 或 revision 名称。 |
| `--commit <sha>` | 否 | 写入 CBOM metadata 的 commit hash。 |
| `--java-class-dir <dir>` | 否 | Java 编译 class 目录，可重复传。 |
| `--java-jar <path-or-glob>` | 否 | Java 依赖 jar 或 jar glob，可重复传。 |
| `--require-java-build` | 否 | Java 扫描时要求提供构建产物，否则失败。 |
| `--cxx-define <NAME[=VALUE]>` | 否 | C/C++ 预处理宏定义，可重复传。 |

### `dir-assets` 参数

| 参数 | 是否必需 | 含义 |
| --- | --- | --- |
| `<directory>` | 是 | 要扫描资产的目录。 |
| `--output <file>` | 否 | 输出 CBOM JSON 文件。不传时输出到标准输出。 |
| `--theia <path>` | 否 | 指定 `cbomkit-theia` 可执行文件路径。 |
| `--bom <file>` | 否 | 基于已有 CBOM 增强输出。 |
| `--plugin <name>` | 否 | 指定 Theia 插件，可重复传。 |
| `--ignore <glob>` | 否 | 忽略目录资产扫描路径，可重复传。 |

### `image` 参数

| 参数 | 是否必需 | 含义 |
| --- | --- | --- |
| `<image-ref>` | 是 | 镜像引用，例如 `nginx:latest`。 |
| `--output <file>` | 否 | 输出 CBOM JSON 文件。不传时输出到标准输出。 |
| `--theia <path>` | 否 | 指定 `cbomkit-theia` 可执行文件路径。 |
| `--bom <file>` | 否 | 基于已有 CBOM 增强输出。 |
| `--plugin <name>` | 否 | 指定 Theia 插件，可重复传。 |
| `--ignore <glob>` | 否 | 忽略镜像内路径，可重复传。 |
| `--docker-host <uri>` | 否 | 指定 Docker host，例如 `unix:///var/run/docker.sock`。 |

### 环境变量

| 环境变量 | 含义 |
| --- | --- |
| `CBOMKIT_JAVA_OPTS` | 发布包脚本启动 JVM 时追加的参数，例如 `-Xmx6g`。 |
| `CBOMKIT_THEIA` | `dir-assets` 和 `image` 默认使用的 Theia 可执行文件路径。命令行 `--theia` 优先级更高。 |

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

## 常见问题

### 是否必须指定 `--language`

不是必须。不指定时会尝试扫描 Java、Python、Go、C/C++。大型仓库建议指定，例如 `--language cxx` 或 `--language java,go`，可以减少无意义扫描并让结果更明确。

### 为什么 C/C++ 扫描结果很少

常见原因：

- 密码库调用在 `#if` 或 `#ifdef` 后面，但没有传入对应 `--cxx-define`。
- 扫描目录不是源码根目录，或者用了过宽的 `--exclude`。
- 项目通过生成代码产生实际调用，但生成目录被跳过或还没有生成。
- 目标项目使用了当前规则未覆盖的密码库或 API。

### C/C++ 宏是不是越多越好

不是。宏应当匹配真实构建配置。打开互斥宏会把多个产品路径混在一次扫描里，容易把未发布、未编译或测试专用路径也算入 CBOM。多配置项目建议分别扫描并保留各自输出。

### 生成文件为空或组件很少

可能原因：

- 仓库中没有当前语言源码。
- 排除规则过宽。
- Java 项目没有构建产物，部分符号无法解析。
- C/C++ 条件编译宏没有按目标配置传入。
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

### `image` 或 `dir-assets` 命令失败

检查：

- `cbomkit-theia` 是否存在且可执行。
- 是否通过 `--theia` 或 `CBOMKIT_THEIA` 指定了路径。
- 扫描镜像时 Docker/OCI 访问是否可用。
- 是否有读取目标目录、Docker socket 或镜像源的权限。
