"""스크린샷용 예시 데이터: 내려받은 예시 사진을 앱 저장 공간(Application Support)에 넣고 entries.json을 쓴다.

사용: python3 demo.py <사진 폴더> <앱 Application Support 폴더>
사진 폴더에는 photos.txt의 이름대로 <이름>.jpg 가 있어야 한다.
"""
import json
import shutil
import sys
from pathlib import Path

src, dst = Path(sys.argv[1]), Path(sys.argv[2])

# (이름, 날짜, 기록, 위도, 경도) — 최신순으로 적는다. 이름은 photos.txt와 같다.
DEMO = [
    ("cafe", "2026-11-05", "성수동 골목 끝 작은 카페.\n창가 자리에 앉아 라테 한 잔.\n오후 햇빛이 테이블 위로 길게 들어왔다.", 37.54460, 127.05570),
    ("street", "2026-11-05", "", 37.54420, 127.05610),
    ("dessert", "2026-11-05", "", 37.54471, 127.05522),
    ("autumn", "2026-11-03", "서울숲 은행나무길. 바람이 불 때마다 노란 잎이 쏟아졌다.", 37.54436, 127.03740),
    ("park", "2026-11-03", "", 37.54390, 127.03810),
    ("dog", "2026-11-01", "주말 아침 산책. 오늘도 제일 신난 건 너.", 37.52740, 126.93290),
    ("river", "2026-11-01", "", 37.52810, 126.93410),
    ("night", "2026-10-28", "퇴근길에 올려다본 하늘.", 37.56650, 126.97800),
    ("book", "2026-10-28", "", 37.57000, 126.98200),
    ("sea", "2026-10-25", "강릉 안목해변. 파도 소리만 한참 들었다.", 37.77180, 128.94730),
    ("coast", "2026-10-25", "", 37.77050, 128.95010),
    ("mountain", "2026-10-19", "설악산 울산바위까지 3시간. 다리는 후들거려도 이 풍경이면 됐다.", 38.17580, 128.48880),
    ("forest", "2026-10-19", "", 38.16900, 128.48100),
    ("flowers", "2026-10-12", "", 37.58260, 126.98340),
    ("breakfast", "2026-10-12", "오랜만에 집에서 차린 아침.", None, None),
    ("tea", "2026-09-27", "", 37.57940, 126.97700),
    ("lake", "2026-09-14", "춘천 호수 위 안개가 걷히는 시간.", 37.88130, 127.72980),
    ("trail", "2026-09-14", "", 37.88300, 127.73100),
]

photos = dst / "photos"
photos.mkdir(parents=True, exist_ok=True)
entries = []
base = 1_793_000_000_000  # 2026-10월 무렵 밀리초
for i, (name, date, note, lat, lon) in enumerate(DEMO):
    f = src / f"{name}.jpg"
    if not f.exists():
        print(f"없음: {f}")
        continue
    file = f"PHOTOLOG_DEMO_{i:02d}.jpg"
    shutil.copy(f, photos / file)
    e = {
        "id": f"demo{i:02d}",
        "uri": f"photos/{file}",
        "date": date,
        "note": note,
        "created": base + (len(DEMO) - i) * 60_000,
    }
    if lat is not None:
        e["lat"], e["lon"] = lat, lon
    entries.append(e)

(dst / "entries.json").write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
print(f"기록 {len(entries)}개를 넣었습니다")
