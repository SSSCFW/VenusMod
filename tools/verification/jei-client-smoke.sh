#!/usr/bin/env bash
# 既存ワールドを使わず、一時ワールドを作成してJEI実クライアントを検証する。
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$root"
gradle_bin="${GRADLE_BIN:-gradle}"
command -v xvfb-run >/dev/null || { echo 'xvfb-run is required'; exit 2; }
mkdir -p run/jeiSmokeServer run/jeiSmokeClient build/reports/jei
# このディレクトリは検証専用。通常run/serverや所有者のワールドは変更しない。
printf 'eula=true\n' > run/jeiSmokeServer/eula.txt
cat > run/jeiSmokeServer/server.properties <<'PROPS'
online-mode=false
enforce-secure-profile=false
server-ip=127.0.0.1
server-port=25576
level-name=jei-smoke-world
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
motd=VenusMod isolated JEI verification
PROPS
# 初回のAccessibility画面がQuick Playの継続を止めないよう、検証専用optionsへ明記する。
printf 'onboardAccessibility:false\nnarrator:0\nlang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\nshowTutorial:false\n' > run/jeiSmokeClient/options.txt
mkdir -p run/jeiSmokeClient/jei-smoke
printf 'jei-smoke-world\n' > run/jeiSmokeClient/jei-smoke/fixture.txt
rm -f run/jeiSmokeClient/jei-smoke/PASS.txt run/jeiSmokeClient/jei-smoke/FAIL.txt run/jeiSmokeClient/jei-smoke/interaction-PASS.txt run/jeiSmokeClient/jei-smoke/0[5-9]-*.png
setsid "$gradle_bin" runServer -PvenusTestJei=true -PvenusJeiSmoke=true --no-daemon --console=plain > build/reports/jei/server.log 2>&1 &
server_pid=$!
cleanup(){
  kill -- -"$server_pid" 2>/dev/null || true
  if [[ -f run/jeiSmokeClient/options.txt ]]; then cp run/jeiSmokeClient/options.txt build/reports/jei/client-options.txt; fi
  if [[ -d run/jeiSmokeClient/jei-smoke ]]; then cp -a run/jeiSmokeClient/jei-smoke/. build/reports/jei/; fi
}
trap cleanup EXIT
ready=false
for ((i=0;i<300;i++)); do
  if grep -q 'Done (' build/reports/jei/server.log; then ready=true; break; fi
  if ! kill -0 "$server_pid" 2>/dev/null; then tail -80 build/reports/jei/server.log; exit 1; fi
  sleep 1
done
if [[ "$ready" != true ]]; then tail -80 build/reports/jei/server.log; exit 1; fi
# 同一プロジェクトのGradleを同時実行するとロック待ちになるため、作成後にサーバーを止める。
kill -- -"$server_pid" 2>/dev/null || true
wait "$server_pid" 2>/dev/null || true
for ((i=0;i<30;i++)); do
  if ! kill -0 -- -"$server_pid" 2>/dev/null; then break; fi
  sleep 1
done
if kill -0 -- -"$server_pid" 2>/dev/null; then echo 'Test server did not stop cleanly'; exit 1; fi
test -s run/jeiSmokeServer/jei-smoke-world/level.dat
python3 - <<'PYCODE'
import pathlib, shutil
source=pathlib.Path('run/jeiSmokeServer/jei-smoke-world')
target=pathlib.Path('run/jeiSmokeClient/saves/jei-smoke-world')
shutil.copytree(source,target,dirs_exist_ok=True)
(target/'session.lock').unlink(missing_ok=True)
PYCODE
# Minecraftのheadless AWTを変えず入力できることを、同じJDKの別プロセスで先に確認。
mkdir -p build/classes/jeiInputTests
javac -encoding UTF-8 -d build/classes/jeiInputTests \
  src/main/java/dev/ssscfw/venusmod/infrastructure/network/jei/NetworkSmokeInput.java \
  tests/verification/NetworkSmokeInputTest.java
xvfb-run -a -s '-screen 0 1280x800x24' java -cp build/classes/jeiInputTests \
  dev.ssscfw.venusmod.infrastructure.network.jei.NetworkSmokeInputTest \
  "$root/tools/verification/JeiNativeInput.java" "$root/build/reports/jei/native-input-contract-worker.log" \
  > build/reports/jei/native-input-contract.log 2>&1
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 timeout 360 xvfb-run -a -s '-screen 0 1280x800x24' "$gradle_bin" runClient -PvenusTestJei=true -PvenusJeiSmoke=true --no-daemon --console=plain > build/reports/jei/client.log 2>&1
if [[ -f run/jeiSmokeClient/jei-smoke/FAIL.txt ]]; then cat run/jeiSmokeClient/jei-smoke/FAIL.txt; exit 1; fi
test -s run/jeiSmokeClient/jei-smoke/PASS.txt
test -s run/jeiSmokeClient/jei-smoke/interaction-PASS.txt
for screenshot in 01-cable-spec 02-connection-guide 03-continued-instructions 04-upgrade-spec 05-guide-search 06-guide-empty 07-guide-article 08-guide-related 09-jei-from-device; do test -s "run/jeiSmokeClient/jei-smoke/$screenshot.png"; done
cp run/jeiSmokeClient/jei-smoke/* build/reports/jei/
cat build/reports/jei/PASS.txt
