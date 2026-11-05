#!/bin/bash
# iPhone 6.9형 시뮬레이터에서 예시 데이터로 App Store 스크린샷 5장을 찍는다.
# 사용: ios/Screenshots/run.sh  (ios 폴더에서 xcodegen generate 를 먼저 실행)
# 결과: ios/Screenshots/out/raw/*.png (원본 1320x2868), out/framed/*.png (문구를 얹은 버전)
set -euo pipefail
cd "$(dirname "$0")"
HERE=$PWD
BUNDLE=com.dup04205.photolog
OUT=$HERE/out
rm -rf "$OUT" && mkdir -p "$OUT/raw" "$OUT/photos" "$OUT/candidates"

# 1) 예시 사진 내려받기 (Lorem Picsum = Unsplash 사진)
grep -v '^#' photos.txt | while read -r name id; do
  [ -z "$name" ] && continue
  curl -fsSL --retry 3 -o "$OUT/photos/$name.jpg" "https://picsum.photos/id/$id/1200/1500"
done
# 후보 미리보기용(사진을 바꾸고 싶을 때 참고)
for id in ${CANDIDATES:-}; do
  curl -fsSL --retry 2 -o "$OUT/candidates/$id.jpg" "https://picsum.photos/id/$id/400/500" || true
done

# 2) 시뮬레이터 준비 (iPhone 16 Pro Max = 6.9형, 1320x2868)
DEVTYPE=$(xcrun simctl list devicetypes -j | python3 -c "import json,sys;t=[d['identifier'] for d in json.load(sys.stdin)['devicetypes'] if d['name']=='iPhone 16 Pro Max'];print(t[0])")
UDID=$(xcrun simctl create PLShot "$DEVTYPE")
xcrun simctl boot "$UDID"
xcrun simctl bootstatus "$UDID" -b > /dev/null
xcrun simctl status_bar "$UDID" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --operatorName "" --batteryState charged --batteryLevel 100

# 3) 빌드·설치
cd "$HERE/.."
xcodebuild -project PhotoLog.xcodeproj -scheme PhotoLog -configuration Debug \
  -sdk iphonesimulator -destination "id=$UDID" -derivedDataPath build-sim \
  CODE_SIGNING_ALLOWED=NO build > "$OUT/build.log" 2>&1 || { grep -E 'error:' "$OUT/build.log" | head -30; exit 1; }
xcrun simctl install "$UDID" build-sim/Build/Products/Debug-iphonesimulator/PhotoLog.app
xcrun simctl privacy "$UDID" grant location "$BUNDLE" || true
xcrun simctl privacy "$UDID" grant photos "$BUNDLE" || true
xcrun simctl location "$UDID" set 37.5446,127.0557 || true

# 4) 예시 데이터 넣기
DATA=$(xcrun simctl get_app_container "$UDID" "$BUNDLE" data)
SUPPORT="$DATA/Library/Application Support"
mkdir -p "$SUPPORT"
python3 "$HERE/demo.py" "$OUT/photos" "$SUPPORT"

LANG_ARGS=(-AppleLanguages "(ko)" -AppleLocale ko_KR -PLDemo YES)
shot() { # 이름, 앱 실행 인자...
  local name=$1; shift
  xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
  xcrun simctl launch "$UDID" "$BUNDLE" "${LANG_ARGS[@]}" "$@" > /dev/null
  sleep 6
  xcrun simctl io "$UDID" screenshot --type=png "$OUT/raw/$name.png" > /dev/null
  echo "찍음: $name"
}

xcrun simctl ui "$UDID" appearance light
shot 1-grid
shot 2-detail -PLOpen demo00
shot 3-card -PLShare demo00 -PLShareStyle card -PLShareRatio 2
shot 4-story -PLShare demo09 -PLShareStyle overlay -PLShareRatio 3
xcrun simctl ui "$UDID" appearance dark
shot 5-dark

xcrun simctl shutdown "$UDID" || true
xcrun simctl delete "$UDID" || true
