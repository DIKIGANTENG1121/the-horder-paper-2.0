#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$ROOT_DIR/test-server"
REPORT_FILE="$ROOT_DIR/neon-report.txt"
USER_AGENT="hordes-paper-testkit/1.0.0 (https://github.com/SmileycorpMC/The-Hordes)"
MC_VERSION="26.3"
SKIP_BUILD=false

if [ "${1:-}" = "--skip-build" ]; then
  SKIP_BUILD=true
fi

exec > >(tee "$REPORT_FILE") 2>&1

echo "================================================================"
echo "GEMINI NEON — HordesPaper Automated Build & Acceptance Test Run"
echo "Timestamp : $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Host OS   : $(uname -a)"
echo "CPU/RAM   : $(nproc 2>/dev/null || echo '?') cores | $(free -h 2>/dev/null | awk '/Mem:/{print $2}' || echo 'N/A')"
echo "================================================================"

echo ""
echo "[1/6] Checking JDK 25..."
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: 'java' not found in PATH. Install JDK 25 first."
  exit 1
fi
JAVA_VER_LINE="$(java -version 2>&1 | head -n 1)"
echo "Detected Java: $JAVA_VER_LINE"
if ! echo "$JAVA_VER_LINE" | grep -qE '"25(\.|")'; then
  echo "ERROR: JDK 25 is required for Paper 26.3. Found: $JAVA_VER_LINE"
  exit 1
fi

echo ""
if [ "$SKIP_BUILD" = "false" ]; then
  echo "[2/6] Building HordesPaper and neon-testkit with gradle..."
  cd "$ROOT_DIR"
  if ! command -v gradle >/dev/null 2>&1; then
    echo "ERROR: 'gradle' not found in PATH. Install Gradle 9.1.0+ and re-run."
    exit 1
  fi
  gradle clean build --no-daemon 2>&1 | tee "$ROOT_DIR/build.log"
else
  echo "[2/6] Skipping gradle build (--skip-build)."
fi

MAIN_JAR="$(find "$ROOT_DIR/build/libs" -maxdepth 1 -name 'HordesPaper-*.jar' ! -name '*-plain.jar' | head -n 1)"
TESTKIT_JAR="$(find "$ROOT_DIR/neon-testkit/build/libs" -maxdepth 1 -name 'neon-testkit*.jar' ! -name '*-plain.jar' | head -n 1)"
if [ -z "$MAIN_JAR" ] || [ -z "$TESTKIT_JAR" ]; then
  echo "ERROR: Built JAR(s) not found."
  exit 1
fi
echo "Main JAR    : $MAIN_JAR"
echo "Testkit JAR : $TESTKIT_JAR"

echo ""
echo "[3/6] Downloading latest STABLE Paper $MC_VERSION build..."
mkdir -p "$TEST_DIR/plugins"
BUILDS_JSON="$(curl -fsSL -H "User-Agent: $USER_AGENT" "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds")"

if command -v jq >/dev/null 2>&1; then
  PAPER_URL="$(echo "$BUILDS_JSON" | jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // empty')"
  PAPER_BUILD="$(echo "$BUILDS_JSON" | jq -r 'first(.[] | select(.channel == "STABLE") | .id) // empty')"
elif command -v python3 >/dev/null 2>&1; then
  PAPER_URL="$(python3 -c 'import sys,json; b=json.load(sys.stdin); s=[x for x in b if x.get("channel")=="STABLE"]; print(s[0]["downloads"]["server:default"]["url"] if s else "")' <<< "$BUILDS_JSON")"
  PAPER_BUILD="$(python3 -c 'import sys,json; b=json.load(sys.stdin); s=[x for x in b if x.get("channel")=="STABLE"]; print(s[0]["id"] if s else "")' <<< "$BUILDS_JSON")"
else
  echo "ERROR: Need either 'jq' or 'python3' to parse PaperMC Fill v3 API response."
  exit 1
fi

if [ -z "$PAPER_URL" ]; then
  echo "ERROR: No STABLE Paper $MC_VERSION build found from Fill v3 API."
  exit 1
fi
echo "Paper $MC_VERSION stable build #$PAPER_BUILD -> $PAPER_URL"
curl -fsSL -H "User-Agent: $USER_AGENT" -o "$TEST_DIR/paper.jar" "$PAPER_URL"

echo ""
echo "[4/6] Preparing sandbox test-server (EULA accepted ONLY in test-server/)..."
echo "eula=true" > "$TEST_DIR/eula.txt"
cat > "$TEST_DIR/server.properties" << 'PROPS'
online-mode=false
enable-rcon=false
level-type=minecraft\:flat
spawn-protection=0
simulation-distance=10
view-distance=10
difficulty=hard
motd=NeonTestkit Headless Sandbox
max-players=5
PROPS
rm -f "$TEST_DIR/plugins"/HordesPaper-*.jar "$TEST_DIR/plugins"/neon-testkit-*.jar
cp "$MAIN_JAR" "$TEST_DIR/plugins/"
cp "$TESTKIT_JAR" "$TEST_DIR/plugins/"

echo ""
echo "[5/6] Booting Paper $MC_VERSION headless with auto-run AT-01..AT-23..."
cd "$TEST_DIR"
rm -f neon-testkit-results.txt
java -Xms1G -Xmx2G -Dneon.autorun=true -Dneon.autoshutdown=true -jar paper.jar --nogui

echo ""
echo "[6/6] Collecting acceptance test results & evaluating strict per-ID gate..."
echo "---------------- NEON TESTKIT RESULTS ----------------"
RESULTS_FILE="$TEST_DIR/neon-testkit-results.txt"
if [ ! -f "$RESULTS_FILE" ]; then
  echo "ERROR: neon-testkit-results.txt was not generated! Status: UNVERIFIED"
  exit 1
fi
cat "$RESULTS_FILE"

echo "---------------- SERVER ERRORS/WARNINGS --------------"
if [ -f "$TEST_DIR/logs/latest.log" ]; then
  grep -E "ERROR|WARN|Exception|HordesPaper|NeonTestkit" "$TEST_DIR/logs/latest.log" | tail -n 120 || true
fi
echo "================================================================"
echo "Report saved to: $REPORT_FILE"

CORE_REQUIRED=(AT-01 AT-02 AT-03 AT-04 AT-05 AT-06 AT-07 AT-09 AT-10 AT-11 AT-12 AT-13 AT-14 AT-15 AT-16 AT-17 AT-20 AT-22 AT-23)
TICK_REQUIRED=(AT-18 AT-19)

HAS_FAIL=0
MISSING_CORE=0
TICK_SKIPPED=0
TICK_MISSING_OR_FAIL=0

if grep -qE '^\[FAIL\] ' "$RESULTS_FILE"; then
  HAS_FAIL=1
fi

for id in "${CORE_REQUIRED[@]}"; do
  if ! grep -qE "^\[PASS\] ${id} " "$RESULTS_FILE"; then
    echo "MISSING OR NON-PASS core required test: $id"
    MISSING_CORE=1
  fi
done

for id in "${TICK_REQUIRED[@]}"; do
  if grep -qE "^\[PASS\] ${id} " "$RESULTS_FILE"; then
    continue
  elif grep -qE "^\[INFO\] ${id} .*SKIPPED:" "$RESULTS_FILE"; then
    echo "Headless tick-skipped test: $id"
    TICK_SKIPPED=1
  else
    echo "MISSING OR FAIL tick-dependent test: $id"
    TICK_MISSING_OR_FAIL=1
  fi
done

if [ "$HAS_FAIL" -eq 0 ] && [ "$MISSING_CORE" -eq 0 ] && [ "$TICK_MISSING_OR_FAIL" -eq 0 ]; then
  if [ "$TICK_SKIPPED" -eq 0 ]; then
    echo "VERIFICATION STATUS: VERIFIED"
  else
    echo "VERIFICATION STATUS: VERIFIED-PARTIAL (AT-18/AT-19 skipped on headless server without active player ticking)"
  fi
else
  echo "VERIFICATION STATUS: UNVERIFIED"
  exit 1
fi
