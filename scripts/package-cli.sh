#!/usr/bin/env sh
set -eu

usage() {
  cat <<'EOF'
Usage:
  scripts/package-cli.sh [options]

Options:
  --mode minimal|full      Package mode. Defaults to full.
  --version <version>      Release version. Defaults to the project version from pom.xml.
  --platform <name>        Archive platform suffix. Defaults from uname, for example linux-amd64.
  --cli-jar <path>         CLI fat jar. Defaults to target/cbomkit-<version>-cli.jar.
  --theia <path>           cbomkit-theia executable. Required for --mode full if not found automatically.
  --output-dir <path>      Output directory. Defaults to dist.
  -h, --help               Show this help.

Examples:
  scripts/package-cli.sh --mode full --theia ../cbomkit-theia/cbomkit-theia
  scripts/package-cli.sh --mode minimal
EOF
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

mode=full
version=
platform=
cli_jar=
theia=
output_dir=dist

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode)
      mode=${2:?missing value for --mode}
      shift 2
      ;;
    --version)
      version=${2:?missing value for --version}
      shift 2
      ;;
    --platform)
      platform=${2:?missing value for --platform}
      shift 2
      ;;
    --cli-jar)
      cli_jar=${2:?missing value for --cli-jar}
      shift 2
      ;;
    --theia)
      theia=${2:?missing value for --theia}
      shift 2
      ;;
    --output-dir)
      output_dir=${2:?missing value for --output-dir}
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

case "$mode" in
  minimal|full) ;;
  *)
    echo "Invalid --mode: $mode" >&2
    exit 1
    ;;
esac

if [ -z "$version" ]; then
  version=$(
    awk '
      match($0, /<version>[^<]+<\/version>/) {
        gsub(/.*<version>|<\/version>.*/, "", $0)
        print
        exit
      }
    ' "$project_dir/pom.xml"
  )
fi

if [ -z "$platform" ]; then
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) platform=linux-amd64 ;;
    Linux-aarch64|Linux-arm64) platform=linux-arm64 ;;
    Darwin-x86_64) platform=darwin-amd64 ;;
    Darwin-arm64) platform=darwin-arm64 ;;
    MINGW*|MSYS*|CYGWIN*) platform=windows-amd64 ;;
    *) platform=$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m) ;;
  esac
fi

if [ -z "$cli_jar" ]; then
  cli_jar="$project_dir/target/cbomkit-$version-cli.jar"
fi

if [ ! -f "$cli_jar" ]; then
  echo "CLI jar not found: $cli_jar" >&2
  echo "Build it first with: mvn -DskipTests package" >&2
  exit 1
fi

if [ "$mode" = "full" ] && [ -z "$theia" ]; then
  if [ -x "$project_dir/../cbomkit-theia/cbomkit-theia" ]; then
    theia="$project_dir/../cbomkit-theia/cbomkit-theia"
  elif command -v cbomkit-theia >/dev/null 2>&1; then
    theia=$(command -v cbomkit-theia)
  fi
fi

if [ "$mode" = "full" ]; then
  if [ -z "$theia" ] || [ ! -x "$theia" ]; then
    echo "Full mode requires an executable cbomkit-theia binary." >&2
    echo "Build it from ../cbomkit-theia or pass --theia /path/to/cbomkit-theia." >&2
    exit 1
  fi
fi

mkdir -p "$output_dir"
output_dir=$(CDPATH= cd -- "$output_dir" && pwd)
package_name="cbomkit-cli-$version"
staging="$output_dir/$package_name"
archive="$output_dir/$package_name-$platform.tar.gz"

rm -rf "$staging"
mkdir -p "$staging/bin" "$staging/lib" "$staging/docs"

cp "$cli_jar" "$staging/lib/cbomkit-cli.jar"

if [ "$mode" = "full" ]; then
  theia_name=cbomkit-theia
  case "$theia" in
    *.exe) theia_name=cbomkit-theia.exe ;;
  esac
  cp "$theia" "$staging/bin/$theia_name"
  chmod +x "$staging/bin/$theia_name" 2>/dev/null || true
fi

cat > "$staging/bin/cbomkit" <<'EOF'
#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN=java
fi

if [ -z "${CBOMKIT_THEIA:-}" ] && [ -x "$APP_HOME/bin/cbomkit-theia" ]; then
  export CBOMKIT_THEIA="$APP_HOME/bin/cbomkit-theia"
fi

exec "$JAVA_BIN" ${CBOMKIT_JAVA_OPTS:-} -jar "$APP_HOME/lib/cbomkit-cli.jar" "$@"
EOF
chmod +x "$staging/bin/cbomkit"

cat > "$staging/bin/cbomkit.cmd" <<'EOF'
@echo off
set APP_HOME=%~dp0..
if defined JAVA_HOME (
  set JAVA_BIN=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_BIN=java
)
if not defined CBOMKIT_THEIA if exist "%APP_HOME%\bin\cbomkit-theia.exe" set CBOMKIT_THEIA=%APP_HOME%\bin\cbomkit-theia.exe
if not defined CBOMKIT_THEIA if exist "%APP_HOME%\bin\cbomkit-theia" set CBOMKIT_THEIA=%APP_HOME%\bin\cbomkit-theia
"%JAVA_BIN%" %CBOMKIT_JAVA_OPTS% -jar "%APP_HOME%\lib\cbomkit-cli.jar" %*
EOF

for doc in \
  "$project_dir/docs/cli-user-guide.md" \
  "$project_dir/docs/binary-release-deployment-guide.md" \
  "$project_dir/docs/sdk-cli-cyclonedx17-remediation.md"
do
  if [ -f "$doc" ]; then
    cp "$doc" "$staging/docs/"
  fi
done

if [ -f "$project_dir/LICENSE.txt" ]; then
  cp "$project_dir/LICENSE.txt" "$staging/"
fi

cat > "$staging/RELEASE_NOTES.txt" <<EOF
CBOMkit CLI $version

Package mode: $mode
Platform: $platform
Build date UTC: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

Included components:
- bin/cbomkit: launcher for the Java CLI
- lib/cbomkit-cli.jar: CBOMkit source scanner CLI
EOF

if [ "$mode" = "full" ]; then
  cat >> "$staging/RELEASE_NOTES.txt" <<EOF
- bin/$theia_name: delegated scanner for directory assets and container images
EOF
fi

cat >> "$staging/RELEASE_NOTES.txt" <<'EOF'

Start with:
  bin/cbomkit --help
  docs/cli-user-guide.md
EOF

(
  cd "$staging"
  find . -type f ! -name SHA256SUMS -print | LC_ALL=C sort | while IFS= read -r file; do
    sha256sum "$file"
  done > SHA256SUMS
)

rm -f "$archive" "$archive.sha256"
(
  cd "$output_dir"
  tar -czf "$(basename "$archive")" "$package_name"
  sha256sum "$(basename "$archive")" > "$(basename "$archive").sha256"
)

echo "Created package directory: $staging"
echo "Created archive: $archive"
echo "Created archive checksum: $archive.sha256"
