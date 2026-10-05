#!/usr/bin/env python3
"""KDPA 보호지역 shapefile에서 자연공원 경계를 뽑아 적재용 TSV로 만든다.

KDPA 원본(zip 또는 .shp)에서 국립공원·도립공원·군립공원만 골라,
서버의 공원 경계 적재 작업이 읽는 형식으로 저장한다.

실행:
    uv run --with pyshp python3 scripts/publicdata/extract-park-boundaries.py <input> \\
        [--output-dir data/processed] [--source-date 20251231]

출력 파일은 <output-dir>/kdpa-park-boundaries_<source-date>.tsv 이다.
UTF-8(BOM 없음), LF 줄바꿈, 탭 구분이고 머리글은 name, area_type, wkt 이다.
wkt는 항상 경도·위도 순서의 MULTIPOLYGON이며, 좌표는 반올림하지 않는다.
"""

import argparse
import datetime
import sys
import tempfile
import warnings
import zipfile
from pathlib import Path

import shapefile

AREA_TYPES = {
    "국립공원": "NATIONAL_PARK",
    "도립공원": "PROVINCIAL_PARK",
    "군립공원": "COUNTY_PARK",
}
TYPE_ORDER = ["NATIONAL_PARK", "PROVINCIAL_PARK", "COUNTY_PARK"]
MAX_NAME_LENGTH = 100


def fail(message):
    print(f"오류: {message}", file=sys.stderr)
    sys.exit(1)


def ring_wkt(ring, name):
    points = [(float(p[0]), float(p[1])) for p in ring]
    if not points:
        fail(f"{name}: 빈 고리가 있다")
    if points[0] != points[-1]:
        points.append(points[0])
    for x, y in points:
        if not (-180.0 <= x <= 180.0 and -90.0 <= y <= 90.0):
            fail(f"{name}: 좌표가 경위도 범위를 벗어난다 ({x}, {y})")
    return "(" + ", ".join(f"{x!r} {y!r}" for x, y in points) + ")"


def multipolygon_wkt(geo, name):
    kind = geo["type"]
    if kind == "Polygon":
        polygons = [geo["coordinates"]]
    elif kind == "MultiPolygon":
        polygons = geo["coordinates"]
    else:
        fail(f"{name}: 지원하지 않는 도형 유형이다 ({kind})")
    parts = ["(" + ",".join(ring_wkt(r, name) for r in poly) + ")" for poly in polygons]
    return "MULTIPOLYGON(" + ",".join(parts) + ")"


def extract(shp_path):
    rows = []
    seen = set()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        reader = shapefile.Reader(str(shp_path), encoding="cp949")
        try:
            for sr in reader.iterShapeRecords():
                area_type = AREA_TYPES.get(sr.record["DESIG"])
                if area_type is None:
                    continue
                name = (sr.record["ORIG_NAME"] or "").strip()
                if not name:
                    fail(f"{area_type}: 이름이 빈 레코드가 있다")
                if len(name) > MAX_NAME_LENGTH:
                    fail(f"{name}: 이름이 {MAX_NAME_LENGTH}자를 넘는다")
                if any(c in name for c in "\t\r\n"):
                    fail(f"{name!r}: 이름에 탭이나 줄바꿈이 있다")
                key = (area_type, name)
                if key in seen:
                    fail(f"{area_type} {name}: 같은 공원이 두 번 나온다")
                seen.add(key)
                rows.append((area_type, name, multipolygon_wkt(sr.shape.__geo_interface__, name)))
        finally:
            reader.close()
    rows.sort(key=lambda r: (TYPE_ORDER.index(r[0]), r[1]))
    return rows


def find_shp(directory):
    found = list(Path(directory).rglob("*.shp"))
    if len(found) != 1:
        fail(f"zip 안에서 .shp 파일을 하나만 찾아야 하는데 {len(found)}개를 찾았다")
    return found[0]


def parse_source_date(value):
    try:
        datetime.datetime.strptime(value, "%Y%m%d")
    except ValueError:
        raise argparse.ArgumentTypeError(f"YYYYMMDD 형식의 실제 날짜가 아니다: {value}")
    if len(value) != 8:
        raise argparse.ArgumentTypeError(f"YYYYMMDD 형식의 실제 날짜가 아니다: {value}")
    return value


def main():
    parser = argparse.ArgumentParser(description="KDPA 자연공원 경계를 적재용 TSV로 만든다.")
    parser.add_argument("input", type=Path, help="KDPA zip 파일 또는 .shp 파일 경로")
    parser.add_argument("--output-dir", type=Path, default=Path("data/processed"))
    parser.add_argument("--source-date", type=parse_source_date, default="20251231")
    args = parser.parse_args()

    if not args.input.is_file():
        fail(f"입력 파일이 없다: {args.input}")

    if args.input.suffix.lower() == ".zip":
        with tempfile.TemporaryDirectory() as tmp:
            with zipfile.ZipFile(args.input) as archive:
                archive.extractall(tmp)
            rows = extract(find_shp(tmp))
    elif args.input.suffix.lower() == ".shp":
        rows = extract(args.input)
    else:
        fail(f"입력은 .zip 또는 .shp 파일이어야 한다: {args.input}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / f"kdpa-park-boundaries_{args.source_date}.tsv"
    with open(output, "w", encoding="utf-8", newline="\n") as f:
        f.write("name\tarea_type\twkt\n")
        for area_type, name, wkt in rows:
            f.write(f"{name}\t{area_type}\t{wkt}\n")

    for area_type in TYPE_ORDER:
        print(f"{area_type} {sum(1 for r in rows if r[0] == area_type)}")
    print(output)


if __name__ == "__main__":
    main()
