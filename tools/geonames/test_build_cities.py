# Run from the repository root without network:
#   python3 -I -m unittest discover -s tools/geonames -p 'test_*.py' -v

from __future__ import annotations

import contextlib
import importlib.util
import io
import os
import pathlib
import shutil
import tempfile
import unittest
from unittest import mock
import zipfile

SCRIPT_PATH = pathlib.Path(__file__).resolve().parent / "build_cities.py"


def load_build_cities():
    spec = importlib.util.spec_from_file_location("build_cities", SCRIPT_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load module from {SCRIPT_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


build_cities = load_build_cities()


def make_geonames_row(
    geoname_id: str = "1",
    name: str = "Test City",
    ascii_name: str = "Test City",
    lat: str = "40.4165",
    lon: str = "-3.70256",
    country_code: str = "ES",
    admin1_code: str = "29",
    population: str = "10000",
    timezone: str = "Europe/Madrid",
) -> str:
    """Build a 19-column GeoNames cities5000.txt TSV row."""
    cols = [
        geoname_id,       # 0: geonameid
        name,             # 1: name
        ascii_name,       # 2: asciiname
        "",               # 3: alternatenames
        lat,              # 4: latitude
        lon,              # 5: longitude
        "P",              # 6: feature class
        "PPL",            # 7: feature code
        country_code,     # 8: country code
        "",               # 9: cc2
        admin1_code,      # 10: admin1 code
        "",               # 11: admin2 code
        "",               # 12: admin3 code
        "",               # 13: admin4 code
        population,       # 14: population
        "0",              # 15: elevation
        "0",              # 16: dem
        timezone,         # 17: timezone
        "2026-01-01",     # 18: modification date
    ]
    assert len(cols) == 19
    return "\t".join(cols)


def write_fixtures(
    fixture_dir: pathlib.Path,
    *,
    country_lines: list[str],
    admin1_lines: list[str],
    city_lines: list[str],
    zip_member: str = "cities5000.txt",
) -> None:
    (fixture_dir / "countryInfo.txt").write_text(
        "\n".join(country_lines) + ("\n" if country_lines else ""),
        encoding="utf-8",
    )
    (fixture_dir / "admin1CodesASCII.txt").write_text(
        "\n".join(admin1_lines) + ("\n" if admin1_lines else ""),
        encoding="utf-8",
    )
    city_text = "\n".join(city_lines) + ("\n" if city_lines else "")
    with zipfile.ZipFile(fixture_dir / "cities5000.zip", "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(zip_member, city_text.encode("utf-8"))


def make_fake_downloader(fixture_dir: pathlib.Path):
    def _fake_download(url: str, dest: pathlib.Path, ctx) -> None:
        filename = url.rsplit("/", 1)[-1]
        shutil.copyfile(fixture_dir / filename, dest)

    return _fake_download


STANDARD_COUNTRIES = [
    "# ISO\tISO3\tISO-Numeric\tfips\tCountry",
    "# comment line to ignore",
    "ES\tESP\t724\tSP\tSpain\tMadrid",
    "FR\tFRA\t250\tFR\tFrance\tParis",
    "EC\tECU\t218\tEC\tEcuador\tQuito",
]

STANDARD_ADMIN1 = [
    "# key\tname\tasciiname\tgeonameid",
    "ES.29\tMadrid\tMadrid\t3117732",
    "ES.56\tCataluña\tCataluna\t3336901",
    "FR.11\tÎle-de-France\tIle-de-France\t3012874",
]


class TestHelpers(unittest.TestCase):
    def test_sanitize_field_strips_tabs_newlines_and_outer_whitespace(self) -> None:
        self.assertEqual(build_cities.sanitize_field("Madrid"), "Madrid")
        self.assertEqual(
            build_cities.sanitize_field("  San\tSebastián\r\nDonostia  "),
            "San Sebastián  Donostia",
        )
        self.assertEqual(build_cities.sanitize_field("\t\r\n  "), "")
        self.assertEqual(build_cities.sanitize_field("Line1\nLine2\tCol"), "Line1 Line2 Col")

    def test_format_coord_rounds_to_4_decimals_and_normalizes_negative_zero(self) -> None:
        self.assertEqual(build_cities.format_coord(40.41654), "40.4165")
        self.assertEqual(build_cities.format_coord(-3.70256), "-3.7026")
        self.assertEqual(build_cities.format_coord(12.3), "12.3000")
        self.assertEqual(build_cities.format_coord(0.0), "0.0000")
        self.assertEqual(build_cities.format_coord(-0.0), "0.0000")
        self.assertEqual(build_cities.format_coord(-0.00004), "0.0000")
        self.assertEqual(build_cities.format_coord(-0.00006), "-0.0001")

    def test_parse_countries_skips_comments_and_malformed_rows(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "countryInfo.txt"
            path.write_text(
                "\n".join(
                    [
                        "# header comment",
                        "",
                        "SHORT\tROW",
                        "\tESP\t724\tSP\tMissingCode",
                        "XX\tXXX\t000\tXX\t   ",
                        "ES\tESP\t724\tSP\tSpain\tMadrid",
                        "JP\tJPN\t392\tJA\tJapan\tTokyo",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            self.assertEqual(
                build_cities.parse_countries(path),
                {"ES": "Spain", "JP": "Japan"},
            )

    def test_parse_admin1_skips_comments_uses_ascii_fallback_and_sanitizes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "admin1CodesASCII.txt"
            path.write_text(
                "\n".join(
                    [
                        "# comment",
                        "ONLYONE",
                        "ES.29\t Madrid \tMadrid\t1",
                        "ES.56\t\tCataluna\t2",
                        "FR.11\tÎle-de-France\r\tIle-de-France\t3",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            self.assertEqual(
                build_cities.parse_admin1(path),
                {
                    "ES.29": "Madrid",
                    "ES.56": "Cataluna",
                    "FR.11": "Île-de-France",
                },
            )


class TestParseCitiesAndRenderTsv(unittest.TestCase):
    def test_parse_cities_joins_admin1_validates_country_keeps_tz_and_skips_malformed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            city_lines = [
                # Valid city with admin1, diacritics, and negative-zero longitude
                make_geonames_row(
                    geoname_id="1",
                    name="Castellón",
                    ascii_name="Castellon",
                    lat="39.98636",
                    lon="-0.00004",
                    country_code="ES",
                    admin1_code="29",
                    population="171728",
                    timezone="Europe/Madrid",
                ),
                # Valid city without matching admin1 and empty asciiname -> falls back to name
                make_geonames_row(
                    geoname_id="2",
                    name="Quito",
                    ascii_name="",
                    lat="-0.22985",
                    lon="-78.52495",
                    country_code="EC",
                    admin1_code="18",
                    population="1399814",
                    timezone="America/Guayaquil",
                ),
                # Malformed: too few columns (< 18)
                "3\tShortRow\tShortRow\t\t40.0\t-3.0\tP\tPPL\tES",
                # Malformed: non-numeric population
                make_geonames_row(geoname_id="4", name="BadPop", population="notanint"),
                # Malformed: negative population
                make_geonames_row(geoname_id="5", name="NegPop", population="-5"),
                # Malformed: non-numeric latitude
                make_geonames_row(geoname_id="6", name="BadLat", lat="north"),
                # Malformed: non-numeric longitude
                make_geonames_row(geoname_id="7", name="BadLon", lon="west"),
                # Malformed: NaN and out-of-range coordinates
                make_geonames_row(geoname_id="8", name="NanLat", lat="nan"),
                make_geonames_row(geoname_id="9", name="HighLat", lat="90.0001"),
                make_geonames_row(geoname_id="10", name="LowLon", lon="-180.0001"),
                # Malformed: unknown country code (not in countryInfo)
                make_geonames_row(geoname_id="11", name="UnknownCC", country_code="ZZ"),
                # Malformed: empty timezone
                make_geonames_row(geoname_id="12", name="NoTz", timezone=""),
                # Malformed: empty name
                make_geonames_row(geoname_id="13", name="   ", ascii_name="EmptyName"),
            ]
            write_fixtures(
                tmp_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=city_lines,
            )
            countries = build_cities.parse_countries(tmp_dir / "countryInfo.txt")
            admin1 = build_cities.parse_admin1(tmp_dir / "admin1CodesASCII.txt")
            rows = build_cities.parse_cities(tmp_dir / "cities5000.zip", countries, admin1)

            self.assertEqual(
                rows,
                [
                    (1399814, "Quito", "Quito", "", "EC", "-0.2298", "-78.5250", "America/Guayaquil"),
                    (171728, "Castellón", "Castellon", "Madrid", "ES", "39.9864", "0.0000", "Europe/Madrid"),
                ],
            )

    def test_parse_cities_deterministic_ordering(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            # Same population ties broken by name, country_code, admin1, lat, lon
            unsorted_lines = [
                make_geonames_row(geoname_id="1", name="Beta", country_code="ES", admin1_code="29", population="10000"),
                make_geonames_row(geoname_id="2", name="Alpha", country_code="FR", admin1_code="11", population="10000"),
                make_geonames_row(geoname_id="3", name="Alpha", country_code="ES", admin1_code="56", population="10000"),
                make_geonames_row(geoname_id="4", name="Alpha", country_code="ES", admin1_code="29", lat="41.0", lon="2.0", population="10000"),
                make_geonames_row(geoname_id="5", name="Alpha", country_code="ES", admin1_code="29", lat="40.0", lon="3.0", population="10000"),
                make_geonames_row(geoname_id="6", name="Biggest", country_code="ES", admin1_code="29", population="50000"),
            ]
            write_fixtures(
                tmp_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=unsorted_lines,
            )
            countries = build_cities.parse_countries(tmp_dir / "countryInfo.txt")
            admin1 = build_cities.parse_admin1(tmp_dir / "admin1CodesASCII.txt")
            rows_forward = build_cities.parse_cities(tmp_dir / "cities5000.zip", countries, admin1)

            # Reverse input order and confirm identical output
            write_fixtures(
                tmp_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=list(reversed(unsorted_lines)),
            )
            rows_reversed = build_cities.parse_cities(tmp_dir / "cities5000.zip", countries, admin1)

            self.assertEqual(rows_forward, rows_reversed)
            self.assertEqual(
                [(r[0], r[1], r[4], r[3], r[5]) for r in rows_forward],
                [
                    (50000, "Biggest", "ES", "Madrid", "40.4165"),
                    (10000, "Alpha", "ES", "Cataluña", "40.4165"),
                    (10000, "Alpha", "ES", "Madrid", "40.0000"),
                    (10000, "Alpha", "ES", "Madrid", "41.0000"),
                    (10000, "Alpha", "FR", "Île-de-France", "40.4165"),
                    (10000, "Beta", "ES", "Madrid", "40.4165"),
                ],
            )

    def test_render_tsv_threshold_boundaries_header_and_row_format(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            city_lines = [
                make_geonames_row(
                    geoname_id="1",
                    name="Above",
                    ascii_name="Above",
                    lat="40.4165",
                    lon="-3.70256",
                    country_code="ES",
                    admin1_code="29",
                    population="5001",
                    timezone="Europe/Madrid",
                ),
                make_geonames_row(
                    geoname_id="2",
                    name="AtThreshold",
                    ascii_name="AtThreshold",
                    lat="41.38879",
                    lon="2.15899",
                    country_code="ES",
                    admin1_code="56",
                    population="5000",
                    timezone="Europe/Madrid",
                ),
                make_geonames_row(
                    geoname_id="3",
                    name="BelowThreshold",
                    ascii_name="BelowThreshold",
                    lat="48.85341",
                    lon="2.3488",
                    country_code="FR",
                    admin1_code="11",
                    population="4999",
                    timezone="Europe/Paris",
                ),
            ]
            write_fixtures(
                tmp_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=city_lines,
            )
            countries = build_cities.parse_countries(tmp_dir / "countryInfo.txt")
            admin1 = build_cities.parse_admin1(tmp_dir / "admin1CodesASCII.txt")
            rows = build_cities.parse_cities(tmp_dir / "cities5000.zip", countries, admin1)

            payload, count = build_cities.render_tsv(rows, 5000, "2026-06-21")
            self.assertEqual(count, 2)

            expected = (
                "# source=GeoNames (https://download.geonames.org/export/dump/) "
                "license=CC-BY-4.0 download_date=2026-06-21 min_population=5000 count=2\n"
                "# columns=name\\tascii_name\\tadmin1_name\\tcountry_code\\tlat\\tlon\\tpopulation\\ttimezone\n"
                "Above\tAbove\tMadrid\tES\t40.4165\t-3.7026\t5001\tEurope/Madrid\n"
                "AtThreshold\tAtThreshold\tCataluña\tES\t41.3888\t2.1590\t5000\tEurope/Madrid\n"
            ).encode("utf-8")
            self.assertEqual(payload, expected)


class TestMainEndToEnd(unittest.TestCase):
    def _run_main(self, fixture_dir: pathlib.Path, argv: list[str]) -> tuple[int, str, str]:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(build_cities, "download_file", side_effect=make_fake_downloader(fixture_dir)),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            rc = build_cities.main(argv)
        return rc, stdout.getvalue(), stderr.getvalue()

    def test_main_uses_primary_threshold_under_cap_and_falls_back_over_cap(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            fixture_dir = tmp_dir / "fixtures"
            fixture_dir.mkdir()
            write_fixtures(
                fixture_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=[
                    make_geonames_row(geoname_id="1", name="BigCity", ascii_name="BigCity", population="15000"),
                    make_geonames_row(geoname_id="2", name="MidCity", ascii_name="MidCity", population="14999"),
                    make_geonames_row(geoname_id="3", name="EdgeCity", ascii_name="EdgeCity", population="5000"),
                    make_geonames_row(geoname_id="4", name="SmallTown", ascii_name="SmallTown", population="4999"),
                ],
            )

            out_primary = tmp_dir / "primary" / "cities.tsv"
            rc, stdout, _stderr = self._run_main(
                fixture_dir,
                ["--output", str(out_primary), "--max-bytes", "100000"],
            )
            self.assertEqual(rc, 0)
            self.assertIn("min_population=5000", stdout)
            primary_text = out_primary.read_text(encoding="utf-8")
            self.assertIn("min_population=5000 count=3\n", primary_text)
            self.assertIn("BigCity\t", primary_text)
            self.assertIn("MidCity\t", primary_text)
            self.assertIn("EdgeCity\t", primary_text)
            self.assertNotIn("SmallTown\t", primary_text)

            # Set --max-bytes below primary payload size to force fallback to 15000
            primary_size = len(primary_text.encode("utf-8"))
            out_fallback = tmp_dir / "fallback" / "cities.tsv"
            rc, stdout, stderr = self._run_main(
                fixture_dir,
                ["--output", str(out_fallback), "--max-bytes", str(primary_size - 1)],
            )
            self.assertEqual(rc, 0)
            self.assertIn("falling back to pop >= 15000", stderr)
            self.assertIn("min_population=15000", stdout)
            fallback_text = out_fallback.read_text(encoding="utf-8")
            self.assertIn("min_population=15000 count=1\n", fallback_text)
            self.assertIn("BigCity\t", fallback_text)
            self.assertNotIn("MidCity\t", fallback_text)
            self.assertNotIn("EdgeCity\t", fallback_text)

    def test_main_fails_without_writing_when_countries_empty(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            fixture_dir = tmp_dir / "fixtures"
            fixture_dir.mkdir()
            write_fixtures(
                fixture_dir,
                country_lines=["# comments only, no valid countries"],
                admin1_lines=STANDARD_ADMIN1,
                city_lines=[
                    make_geonames_row(geoname_id="1", name="Madrid", ascii_name="Madrid", population="3255944"),
                ],
            )

            # Case 1: absent output file stays absent
            absent_out = tmp_dir / "absent_dir" / "cities.tsv"
            rc, stdout, stderr = self._run_main(fixture_dir, ["--output", str(absent_out)])
            self.assertNotEqual(rc, 0)
            self.assertEqual(stdout, "")
            self.assertIn("countr", stderr.lower())
            self.assertFalse(absent_out.exists())

            # Case 2: existing output file keeps its exact bytes
            existing_dir = tmp_dir / "existing_dir"
            existing_dir.mkdir()
            existing_out = existing_dir / "cities.tsv"
            original_bytes = b"# preserved previous asset\nMadrid\tMadrid\n"
            existing_out.write_bytes(original_bytes)

            rc, stdout, stderr = self._run_main(fixture_dir, ["--output", str(existing_out)])
            self.assertNotEqual(rc, 0)
            self.assertEqual(stdout, "")
            self.assertIn("countr", stderr.lower())
            self.assertEqual(existing_out.read_bytes(), original_bytes)
            self.assertEqual(list(existing_dir.iterdir()), [existing_out])

    def test_main_fails_without_writing_when_rendered_city_count_is_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            fixture_dir = tmp_dir / "fixtures"
            fixture_dir.mkdir()
            write_fixtures(
                fixture_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=[
                    make_geonames_row(geoname_id="1", name="TinyVillage", ascii_name="TinyVillage", population="4999"),
                ],
            )

            # Case 1: absent output file stays absent
            absent_out = tmp_dir / "absent_dir" / "cities.tsv"
            rc, stdout, stderr = self._run_main(fixture_dir, ["--output", str(absent_out)])
            self.assertNotEqual(rc, 0)
            self.assertEqual(stdout, "")
            self.assertIn("0", stderr)
            self.assertFalse(absent_out.exists())

            # Case 2: existing output file keeps its exact bytes
            existing_dir = tmp_dir / "existing_dir"
            existing_dir.mkdir()
            existing_out = existing_dir / "cities.tsv"
            original_bytes = b"# preserved previous asset\nBarcelona\tBarcelona\n"
            existing_out.write_bytes(original_bytes)

            rc, stdout, stderr = self._run_main(fixture_dir, ["--output", str(existing_out)])
            self.assertNotEqual(rc, 0)
            self.assertEqual(stdout, "")
            self.assertIn("0", stderr)
            self.assertEqual(existing_out.read_bytes(), original_bytes)
            self.assertEqual(list(existing_dir.iterdir()), [existing_out])

    def test_main_fails_without_writing_when_fallback_renders_zero_cities(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            fixture_dir = tmp_dir / "fixtures"
            fixture_dir.mkdir()
            write_fixtures(
                fixture_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=[
                    make_geonames_row(geoname_id="1", name="MidTown", ascii_name="MidTown", population="8000"),
                ],
            )

            existing_dir = tmp_dir / "existing_dir"
            existing_dir.mkdir()
            existing_out = existing_dir / "cities.tsv"
            original_bytes = b"# must remain intact after empty fallback\n"
            existing_out.write_bytes(original_bytes)

            rc, stdout, stderr = self._run_main(
                fixture_dir,
                ["--output", str(existing_out), "--max-bytes", "10"],
            )
            self.assertNotEqual(rc, 0)
            self.assertEqual(stdout, "")
            self.assertIn("0", stderr)
            self.assertEqual(existing_out.read_bytes(), original_bytes)
            self.assertEqual(list(existing_dir.iterdir()), [existing_out])

    def test_main_writes_atomically_via_sibling_temp_and_leaves_no_temp_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = pathlib.Path(tmp)
            fixture_dir = tmp_dir / "fixtures"
            fixture_dir.mkdir()
            write_fixtures(
                fixture_dir,
                country_lines=STANDARD_COUNTRIES,
                admin1_lines=STANDARD_ADMIN1,
                city_lines=[
                    make_geonames_row(geoname_id="1", name="Madrid", ascii_name="Madrid", population="3255944"),
                ],
            )

            # Success path: verify os.replace is called from a sibling temp file and no temp file remains
            out_dir = tmp_dir / "atomic_ok"
            out_dir.mkdir()
            out_path = out_dir / "cities.tsv"
            replace_calls: list[tuple[pathlib.Path, pathlib.Path]] = []
            real_replace = os.replace

            def spy_replace(src, dst):
                src_p = pathlib.Path(src)
                dst_p = pathlib.Path(dst)
                self.assertEqual(src_p.parent, dst_p.parent)
                self.assertNotEqual(src_p, dst_p)
                self.assertTrue(src_p.is_file())
                replace_calls.append((src_p, dst_p))
                return real_replace(src, dst)

            with mock.patch.object(os, "replace", side_effect=spy_replace):
                rc, _stdout, _stderr = self._run_main(fixture_dir, ["--output", str(out_path)])

            self.assertEqual(rc, 0)
            self.assertEqual(len(replace_calls), 1)
            self.assertEqual(replace_calls[0][1], out_path)
            self.assertEqual(list(out_dir.iterdir()), [out_path])

            # Failure path: os.replace fails -> existing file preserved, no sibling temp file remains
            fail_dir = tmp_dir / "atomic_fail"
            fail_dir.mkdir()
            fail_out = fail_dir / "cities.tsv"
            original_bytes = b"# untouched original\n"
            fail_out.write_bytes(original_bytes)

            with mock.patch.object(os, "replace", side_effect=OSError("simulated replace failure")):
                with self.assertRaises(OSError):
                    self._run_main(fixture_dir, ["--output", str(fail_out)])

            self.assertEqual(fail_out.read_bytes(), original_bytes)
            self.assertEqual(list(fail_dir.iterdir()), [fail_out])

            # Failure path while writing/syncing to an absent output -> output stays absent, no sibling temp file
            sync_fail_dir = tmp_dir / "atomic_sync_fail"
            sync_fail_dir.mkdir()
            sync_fail_out = sync_fail_dir / "cities.tsv"

            with mock.patch.object(os, "fsync", side_effect=OSError("simulated fsync failure")):
                with self.assertRaises(OSError):
                    self._run_main(fixture_dir, ["--output", str(sync_fail_out)])

            self.assertFalse(sync_fail_out.exists())
            self.assertEqual(list(sync_fail_dir.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
