#!/bin/sh
# Tests for scripts/check-manifest.sh, using aapt2 xmltree text dumps
# (the guard's APK-free input form), so no build or SDK is needed.
#
# Usage: scripts/test-check-manifest.sh
# Exits 0 when every case passes, 1 otherwise.

set -eu

here=$(cd "$(dirname "$0")" && pwd)
guard="$here/check-manifest.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
trap 'exit 130' HUP INT TERM

ns='http://schemas.android.com/apk/res/android'
perm_line="        A: $ns:name(0x01010003)=\"android.permission.INTERNET\" (Raw: \"android.permission.INTERNET\")"

# Writes a manifest dump to $1. Options: internet=uses-permission|uses-permission-sdk-23,
# backup=false|true|none, rules=yes|no, apps=1|2.
dump() {
    out=$1; shift
    internet=''; backup=false; rules=yes; apps=1
    for kv in "$@"; do
        case $kv in
            internet=*) internet=${kv#internet=} ;;
            backup=*) backup=${kv#backup=} ;;
            rules=*) rules=${kv#rules=} ;;
            apps=*) apps=${kv#apps=} ;;
        esac
    done
    {
        printf 'N: android=%s (line=2)\n' "$ns"
        printf '  E: manifest (line=2)\n'
        printf '    A: package="io.github.ivan_olivera_dev.cenit" (Raw: "io.github.ivan_olivera_dev.cenit")\n'
        printf '      E: uses-permission (line=15)\n'
        printf '        A: %s:name(0x01010003)="io.github.ivan_olivera_dev.cenit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"\n' "$ns"
        if [ -n "$internet" ]; then
            printf '      E: %s (line=16)\n' "$internet"
            printf '%s\n' "$perm_line"
        fi
        i=0
        while [ "$i" -lt "$apps" ]; do
            printf '      E: application (line=22)\n'
            printf '        A: %s:label(0x01010001)=@0x7f090001\n' "$ns"
            [ "$backup" = none ] || printf '        A: %s:allowBackup(0x01010280)=%s\n' "$ns" "$backup"
            [ "$rules" = no ] || printf '        A: %s:dataExtractionRules(0x0101063e)=@0x7f0c0000\n' "$ns"
            printf '          E: activity (line=34)\n'
            printf '            A: %s:name(0x01010003)="io.github.ivan_olivera_dev.cenit.ui.MainActivity"\n' "$ns"
            i=$((i + 1))
        done
    } > "$out"
}

failures=0
cases=0

# expect <name> <exit code> <stdout> <stderr> <guard args...>
expect() {
    name=$1 code=$2 want_out=$3 want_err=$4; shift 4
    cases=$((cases + 1))
    set +e
    "$guard" "$@" > "$work/out" 2> "$work/err"
    got=$?
    set -e
    got_out=$(cat "$work/out") got_err=$(cat "$work/err")
    if [ "$got" -ne "$code" ] || [ "$got_out" != "$want_out" ] || [ "$got_err" != "$want_err" ]; then
        failures=$((failures + 1))
        printf 'FAIL %s\n  exit %s (want %s)\n  stdout: %s\n  want:   %s\n  stderr: %s\n  want:   %s\n' \
            "$name" "$got" "$code" "$got_out" "$want_out" "$got_err" "$want_err"
    else
        printf 'ok   %s\n' "$name"
    fi
}

f="$work/m.txt"

dump "$f"
expect 'offline manifest passes' 0 \
    "Manifest guard passed for $f: no INTERNET permission, allowBackup=false, dataExtractionRules set." '' "$f"

dump "$f" internet=uses-permission
expect 'INTERNET via uses-permission fails' 1 '' \
    "FAIL: manifest declares android.permission.INTERNET (S5: Cenit works offline, no network access)
Manifest guard FAILED for $f" "$f"

dump "$f" internet=uses-permission-sdk-23
expect 'INTERNET via uses-permission-sdk-23 fails' 1 '' \
    "FAIL: manifest declares android.permission.INTERNET (S5: Cenit works offline, no network access)
Manifest guard FAILED for $f" "$f"

dump "$f" backup=true
expect 'allowBackup=true fails' 1 '' \
    "FAIL: <application android:allowBackup> is not false (no cloud backup)
Manifest guard FAILED for $f" "$f"

dump "$f" backup=none
expect 'missing allowBackup fails' 1 '' \
    "FAIL: <application> has no android:allowBackup; it defaults to true (must be false)
Manifest guard FAILED for $f" "$f"

dump "$f" rules=no
expect 'missing dataExtractionRules fails' 1 '' \
    "FAIL: <application> has no android:dataExtractionRules (cloud backup and device transfer must be excluded)
Manifest guard FAILED for $f" "$f"

dump "$f" apps=2
expect 'two application elements fail' 1 '' \
    "FAIL: expected exactly one <application> element, found 2
Manifest guard FAILED for $f" "$f"

printf 'not a manifest dump\n' > "$f"
expect 'malformed input is a usage error' 2 '' \
    "check-manifest: $f does not contain an aapt2 xmltree dump of AndroidManifest.xml" "$f"

expect 'missing file is a usage error' 2 '' \
    "check-manifest: no such file: $work/absent.txt" "$work/absent.txt"

expect 'no argument is a usage error' 2 '' \
    "check-manifest: usage: $guard <app.apk | aapt2-xmltree-dump.txt>"

printf '%d/%d passed\n' $((cases - failures)) "$cases"
[ "$failures" -eq 0 ]
