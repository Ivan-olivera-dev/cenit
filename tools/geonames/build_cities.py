#!/usr/bin/env python3
"""Download GeoNames city dumps and build Cenit's compact offline city TSV.

Run with:
    python3 -I tools/geonames/build_cities.py

Uses Python 3 stdlib only. Downloads cities5000.zip, admin1CodesASCII.txt and
countryInfo.txt into a temporary directory outside the repository, then writes
app/src/main/assets/geonames/cities.tsv.

Attribution: GeoNames (geonames.org), CC BY 4.0.
"""

from __future__ import annotations

import argparse
import datetime as dt
import math
import pathlib
import ssl
import sys
import tempfile
import urllib.request
import zipfile

BASE_URL = "https://download.geonames.org/export/dump/"
FILES = ("cities5000.zip", "admin1CodesASCII.txt", "countryInfo.txt")
MAX_BYTES = 3_000_000  # 3 MB cap for population >= 5000
PRIMARY_MIN_POP = 5_000
FALLBACK_MIN_POP = 15_000
USER_AGENT = "CenitGeoNamesBuilder/1.0 (https://github.com/ivan-olivera-dev/cenit)"
SYSTEM_CA_BUNDLES = (
    "/etc/ssl/cert.pem",
    "/etc/ssl/certs/ca-certificates.crt",
    "/etc/pki/tls/certs/ca-bundle.crt",
)


def build_ssl_context() -> ssl.SSLContext:
    paths = ssl.get_default_verify_paths()
    if paths.cafile or paths.capath:
        return ssl.create_default_context()
    for candidate in SYSTEM_CA_BUNDLES:
        if pathlib.Path(candidate).is_file():
            return ssl.create_default_context(cafile=candidate)
    return ssl.create_default_context()


def download_file(url: str, dest: pathlib.Path, ctx: ssl.SSLContext) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120, context=ctx) as resp, dest.open("wb") as out:
        while True:
            chunk = resp.read(65536)
            if not chunk:
                break
            out.write(chunk)


def parse_countries(path: pathlib.Path) -> dict[str, str]:
    countries: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as fh:
        for line in fh:
            if not line or line.startswith("#"):
                continue
            parts = line.rstrip("\r\n").split("\t")
            if len(parts) < 5:
                continue
            code = parts[0].strip()
            name = parts[4].strip()
            if code and name:
                countries[code] = name
    return countries


def parse_admin1(path: pathlib.Path) -> dict[str, str]:
    admin1: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as fh:
        for line in fh:
            if not line or line.startswith("#"):
                continue
            parts = line.rstrip("\r\n").split("\t")
            if len(parts) < 2:
                continue
            key = parts[0].strip()
            name = parts[1].strip() or (parts[2].strip() if len(parts) > 2 else "")
            if key and name:
                admin1[key] = sanitize_field(name)
    return admin1


def sanitize_field(value: str) -> str:
    return value.replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()


def format_coord(value: float) -> str:
    rounded = round(value, 4)
    if rounded == 0.0:
        rounded = 0.0
    return f"{rounded:.4f}"


def parse_cities(
    zip_path: pathlib.Path,
    countries: dict[str, str],
    admin1: dict[str, str],
) -> list[tuple[int, str, str, str, str, str, str, str]]:
    """Return all valid city rows as (pop, name, ascii_col, admin1, cc, lat, lon, tz)."""
    rows: list[tuple[int, str, str, str, str, str, str, str]] = []
    with zipfile.ZipFile(zip_path, "r") as zf:
        member = "cities5000.txt"
        if member not in zf.namelist():
            txt_members = [n for n in zf.namelist() if n.endswith(".txt")]
            if not txt_members:
                raise RuntimeError(f"No .txt entry inside {zip_path}")
            member = txt_members[0]
        with zf.open(member, "r") as raw:
            for raw_line in raw:
                line = raw_line.decode("utf-8")
                parts = line.rstrip("\r\n").split("\t")
                if len(parts) < 18:
                    continue
                name = sanitize_field(parts[1])
                ascii_name = sanitize_field(parts[2])
                country_code = parts[8].strip()
                admin1_code = parts[10].strip()
                tz = parts[17].strip()
                if not name or country_code not in countries or not tz:
                    continue
                try:
                    lat = float(parts[4])
                    lon = float(parts[5])
                    pop = int(parts[14])
                except ValueError:
                    continue
                if not (math.isfinite(lat) and -90.0 <= lat <= 90.0):
                    continue
                if not (math.isfinite(lon) and -180.0 <= lon <= 180.0):
                    continue
                if pop < 0:
                    continue
                admin1_name = admin1.get(f"{country_code}.{admin1_code}", "")
                ascii_col = ascii_name or name
                rows.append(
                    (
                        pop,
                        name,
                        ascii_col,
                        admin1_name,
                        country_code,
                        format_coord(lat),
                        format_coord(lon),
                        tz,
                    )
                )
    rows.sort(key=lambda r: (-r[0], r[1], r[4], r[3], r[5], r[6]))
    return rows


def render_tsv(
    rows: list[tuple[int, str, str, str, str, str, str, str]],
    min_pop: int,
    download_date: str,
) -> tuple[bytes, int]:
    filtered = [r for r in rows if r[0] >= min_pop]
    header = (
        f"# source=GeoNames (https://download.geonames.org/export/dump/) "
        f"license=CC-BY-4.0 download_date={download_date} "
        f"min_population={min_pop} count={len(filtered)}\n"
        f"# columns=name\\tascii_name\\tadmin1_name\\tcountry_code\\tlat\\tlon\\tpopulation\\ttimezone\n"
    )
    lines = [header]
    for pop, name, ascii_col, admin1_name, cc, lat, lon, tz in filtered:
        lines.append(f"{name}\t{ascii_col}\t{admin1_name}\t{cc}\t{lat}\t{lon}\t{pop}\t{tz}\n")
    return "".join(lines).encode("utf-8"), len(filtered)


def main(argv: list[str]) -> int:
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    default_out = repo_root / "app" / "src" / "main" / "assets" / "geonames" / "cities.tsv"

    parser = argparse.ArgumentParser(description="Build Cenit's offline GeoNames city TSV.")
    parser.add_argument(
        "--output",
        type=pathlib.Path,
        default=default_out,
        help=f"Output TSV path (default: {default_out})",
    )
    parser.add_argument(
        "--max-bytes",
        type=int,
        default=MAX_BYTES,
        help=f"Max bytes for pop >= {PRIMARY_MIN_POP} before falling back to {FALLBACK_MIN_POP} (default: {MAX_BYTES})",
    )
    args = parser.parse_args(argv)

    download_date = dt.date.today().isoformat()
    ssl_ctx = build_ssl_context()

    with tempfile.TemporaryDirectory(prefix="cenit-geonames-") as tmp:
        tmp_dir = pathlib.Path(tmp)
        for filename in FILES:
            url = BASE_URL + filename
            print(f"Downloading {url} ...", file=sys.stderr)
            download_file(url, tmp_dir / filename, ssl_ctx)

        countries = parse_countries(tmp_dir / "countryInfo.txt")
        admin1 = parse_admin1(tmp_dir / "admin1CodesASCII.txt")
        all_rows = parse_cities(tmp_dir / "cities5000.zip", countries, admin1)

    payload, count = render_tsv(all_rows, PRIMARY_MIN_POP, download_date)
    chosen_pop = PRIMARY_MIN_POP
    if len(payload) > args.max_bytes:
        print(
            f"pop >= {PRIMARY_MIN_POP} is {len(payload):,} bytes ({count:,} cities) > {args.max_bytes:,}; "
            f"falling back to pop >= {FALLBACK_MIN_POP}",
            file=sys.stderr,
        )
        payload, count = render_tsv(all_rows, FALLBACK_MIN_POP, download_date)
        chosen_pop = FALLBACK_MIN_POP

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    print(
        f"Wrote {args.output}: count={count}, size={len(payload)} bytes "
        f"({len(payload) / 1_000_000:.2f} MB), min_population={chosen_pop}, "
        f"download_date={download_date}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
