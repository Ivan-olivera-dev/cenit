#!/bin/sh
# Cenit manifest guard (offline contract S5).
#
# Reads the merged, compiled AndroidManifest.xml of a built APK and fails if:
#   - any <uses-permission> / <uses-permission-sdk-23> requests android.permission.INTERNET
#   - <application android:allowBackup> is missing or not false (missing means true)
#   - <application android:dataExtractionRules> is missing
#
# Usage:
#   scripts/check-manifest.sh path/to/app.apk
#   scripts/check-manifest.sh path/to/manifest-dump.txt
#
# The second form takes the text printed by
#   aapt2 dump xmltree --file AndroidManifest.xml <apk>
# so the guard itself can be tested without building an APK.
#
# aapt2 is located through $AAPT2, then PATH, then the newest
# build-tools/ under $ANDROID_HOME, $ANDROID_SDK_ROOT or the default SDK paths.
# Only POSIX sh, awk and sort are used besides aapt2.

set -eu

die() {
    printf 'check-manifest: %s\n' "$*" >&2
    exit 2
}

[ "$#" -eq 1 ] || die "usage: $0 <app.apk | aapt2-xmltree-dump.txt>"
input=$1
[ -f "$input" ] || die "no such file: $input"

find_aapt2() {
    if [ -n "${AAPT2:-}" ]; then
        printf '%s\n' "$AAPT2"
        return 0
    fi
    if command -v aapt2 >/dev/null 2>&1; then
        command -v aapt2
        return 0
    fi
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
        "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || continue
        latest=$(ls -1 "$sdk/build-tools" | grep -v -- '-rc' |
            sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)
        if [ -n "$latest" ] && [ -x "$sdk/build-tools/$latest/aapt2" ]; then
            printf '%s\n' "$sdk/build-tools/$latest/aapt2"
            return 0
        fi
    done
    return 1
}

# An APK is a zip archive: it starts with "PK".
magic=$(dd if="$input" bs=2 count=1 2>/dev/null || true)
if [ "$magic" = "PK" ]; then
    aapt2=$(find_aapt2) || die "aapt2 not found; set AAPT2 or ANDROID_HOME (Android SDK build-tools)"
    dump=$("$aapt2" dump xmltree --file AndroidManifest.xml "$input") ||
        die "aapt2 could not read AndroidManifest.xml from $input"
else
    dump=$(cat "$input")
fi

printf '%s\n' "$dump" | grep -q 'E: manifest' ||
    die "$input does not contain an aapt2 xmltree dump of AndroidManifest.xml"

# Each "A:" line belongs to the most recent "E:" element line above it.
printf '%s\n' "$dump" | awk -v src="$input" '
    function trim(s) { sub(/^[ \t]+/, "", s); return s }
    {
        line = trim($0)
        if (line ~ /^E: /) {
            split(substr(line, 4), parts, " ")
            element = parts[1]
            if (element == "application") apps++
            next
        }
        if (line !~ /^A: /) next
        if ((element == "uses-permission" || element == "uses-permission-sdk-23") &&
            line ~ /android:name\(0x[0-9a-f]+\)="android\.permission\.INTERNET"/) {
            internet = 1
        }
        if (element == "application") {
            if (line ~ /android:allowBackup\(0x[0-9a-f]+\)=/) {
                backup_seen = 1
                if (line ~ /android:allowBackup\(0x[0-9a-f]+\)=false$/) backup_false = 1
            }
            if (line ~ /android:dataExtractionRules\(0x[0-9a-f]+\)=@0x[0-9a-f]+/) rules = 1
        }
    }
    END {
        failed = 0
        if (apps != 1) {
            printf "FAIL: expected exactly one <application> element, found %d\n", apps > "/dev/stderr"
            failed = 1
        }
        if (internet) {
            print "FAIL: manifest declares android.permission.INTERNET (S5: Cenit works offline, no network access)" > "/dev/stderr"
            failed = 1
        }
        if (!backup_seen) {
            print "FAIL: <application> has no android:allowBackup; it defaults to true (must be false)" > "/dev/stderr"
            failed = 1
        } else if (!backup_false) {
            print "FAIL: <application android:allowBackup> is not false (no cloud backup)" > "/dev/stderr"
            failed = 1
        }
        if (!rules) {
            print "FAIL: <application> has no android:dataExtractionRules (cloud backup and device transfer must be excluded)" > "/dev/stderr"
            failed = 1
        }
        if (failed) {
            printf "Manifest guard FAILED for %s\n", src > "/dev/stderr"
            exit 1
        }
        printf "Manifest guard passed for %s: no INTERNET permission, allowBackup=false, dataExtractionRules set.\n", src
    }
'
