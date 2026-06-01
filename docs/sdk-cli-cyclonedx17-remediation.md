# SDK, CLI, and CycloneDX 1.7 Remediation

Date: 2026-06-01

## Scope

This record covers the local remediation across the CBOMkit integration workspace:

- `cyclonedx-core-java`
- `sonar-cryptography`
- `cbomkit-lib`
- `cbomkit`
- `cbomkit-theia`

The target dependency chain is local for CBOMkit-owned core artifacts and remote Maven for the rest:

```text
cyclonedx-core-java:12.2.0-SNAPSHOT
  -> sonar-cryptography:2.0.0-SNAPSHOT
  -> cbomkit-lib:1.0.0-SNAPSHOT
  -> cbomkit:2.0.0-SNAPSHOT
```

## Branch Record

| Repository | Branch | Role | Changes |
| --- | --- | --- | --- |
| `cyclonedx-core-java` | `dev_1.7_model` | CycloneDX CBOM schema/model dependency | Use the v1.7 model branch and add explicit compiler source/target 17 so the local install works on the current JDK. |
| `sonar-cryptography` | `feat/cyclonedx-1.7-local` | Analyzer and CBOM output bridge | Depend on local `cyclonedx-core-java:12.2.0-SNAPSHOT`; emit `Version.VERSION_17` in output and tests; README example updated to `specVersion: 1.7`. |
| `cbomkit-lib` | `feat/sdk-v17` | Source scanning SDK | Depend on local sonar/core artifacts; add `org.pqca.sdk` SDK facade; serialize CBOMs as CycloneDX 1.7; add metadata map support; enable Surefire/JUnit execution; add focused SDK test. |
| `cbomkit` | `feat/sdk-cli-v17` | Main entrypoint and executable CLI | Depend on local `cbomkit-lib:1.0.0-SNAPSHOT`; add shaded executable CLI; add source, directory asset, and image commands; normalize delegated theia JSON to `specVersion: 1.7`; update demo CBOM files. |
| `cbomkit-theia` | `main` | Directory/container asset scanner | No code changes in this pass. It remains an external Go executable invoked by the Java CLI for directory/image asset scanning. |

## Architecture

### Source Scanning

`cbomkit-lib` now exposes `CBOMGenerator` as the SDK entrypoint. It accepts a project directory and `CBOMScanOptions`, indexes selected languages, runs the existing Java/Python/Go scanner services, merges the per-language CBOMs, and adds directory metadata.

The default Java mode is source-only (`requireJavaBuild=false`) so the executable can scan an arbitrary directory without prebuilt jars/classes. Callers can opt into stricter Java builds and pass jar/class-directory hints through SDK or CLI options.

### Executable CLI

The packaged CLI is attached as:

```shell
target/cbomkit-2.0.0-SNAPSHOT-cli.jar
```

Source scan:

```shell
java -jar target/cbomkit-2.0.0-SNAPSHOT-cli.jar source /path/to/project \
  --output cbom.json \
  --language java,python,go \
  --exclude 'target/.*' \
  --git-url https://example.test/repo.git \
  --revision main \
  --commit abc123
```

Directory asset scan through `cbomkit-theia`:

```shell
java -jar target/cbomkit-2.0.0-SNAPSHOT-cli.jar dir-assets /path/to/root \
  --theia /path/to/cbomkit-theia \
  --output assets.cbom.json
```

Container image scan through `cbomkit-theia`:

```shell
java -jar target/cbomkit-2.0.0-SNAPSHOT-cli.jar image nginx:latest \
  --theia /path/to/cbomkit-theia \
  --output image.cbom.json \
  --docker-host unix:///var/run/docker.sock
```

`--theia` is optional if `cbomkit-theia` is on `PATH`. `CBOMKIT_THEIA` can also point to the executable. The Java CLI validates delegated output as JSON and writes it back with `specVersion` normalized to `1.7`.

### SDK Example

```java
CBOMScanOptions options =
        CBOMScanOptions.builder()
                .languages(EnumSet.of(Language.JAVA, Language.PYTHON, Language.GO))
                .gitUrl("https://example.test/repo.git")
                .revision("main")
                .commit("abc123")
                .build();

CBOMScanReport report = new CBOMGenerator().generate(Path.of("/path/to/project"), options);
JsonNode cbom = report.cbom().toJSON();
```

## Container and Directory Scan Design

`cbomkit-theia` already owns non-source filesystem/image asset scanning:

- directory filesystems
- Docker daemon images
- Docker/OCI archive inputs
- OCI registry and Docker Hub references
- Singularity images

The CLI integration intentionally keeps this as a delegated executable instead of embedding Go code into the Java service. This keeps source-code analysis in `cbomkit-lib` and file/container asset scanning in `cbomkit-theia`, with a narrow JSON contract between them.

Operational notes:

- Image scans require the local `cbomkit-theia` binary and whatever Docker/OCI access the target image source requires.
- `cbomkit-theia` writes config under `$HOME/.cbomkit-theia`.
- `cbomkit-theia` currently uses the flag name `--docker_host`; the Java CLI exposes `--docker-host` and maps it correctly.
- If the delegated scanner logs an error and emits invalid or empty JSON, the Java CLI treats that as a failed CBOM generation.

## Validation

Commands run successfully:

```shell
cd cyclonedx-core-java && mvn -DskipTests install
cd sonar-cryptography && mvn -DskipTests install
cd cbomkit-lib && mvn test
cd cbomkit-lib && mvn -DskipTests install
cd cbomkit && mvn -DskipTests package
cd cbomkit && java -jar target/cbomkit-2.0.0-SNAPSHOT-cli.jar \
  source ../cbomkit-lib/src/test/testdata/python/pyca \
  --language python \
  --output /tmp/cbomkit-python.cbom.json \
  --git-url https://example.test/repo.git \
  --revision main \
  --commit abc123
```

Observed results:

- `cbomkit-lib` test run: 19 tests, 0 failures, 0 errors.
- CLI smoke test scanned 1 file and 11 lines.
- `/tmp/cbomkit-python.cbom.json` contains `"specVersion" : "1.7"`.
- Repeated Maven warnings about GitHub Packages metadata returned `401 Unauthorized`; builds completed using local artifacts and public Maven repositories.
- The shaded CLI build reports duplicate-class/resource warnings from existing Sonar/Quarkus dependencies. The CLI jar is executable after excluding dependency signature files.

Blocked validation:

```shell
cd cbomkit && mvn -Dtest=CBOMResourceTest test
```

This failed because Quarkus tried to connect to PostgreSQL at `localhost:5432`, and no database was listening. This is an environment dependency, not a compile or CBOM serialization failure.

Not run:

- `cbomkit-theia` Go tests/build. The local environment does not have `go` installed.

## Known Gaps and Next Iteration

- Current v1.7 work updates schema/version output and dependency alignment. It does not yet populate all new v1.7 model fields such as `algorithmFamily`, `ellipticCurve`, or `relatedCryptographicAssets`.
- `cbomkit-theia` remains a delegated external scanner. Native Go-side v1.7 emission should be handled in a separate theia branch once the Go toolchain and CycloneDX Go model support are available locally.
- Full Quarkus API tests require a local or containerized PostgreSQL test database.
- Maven repository configuration still probes GitHub Packages for metadata and logs 401 warnings without credentials.
