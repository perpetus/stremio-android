#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def detect_abi(element):
    abi = "universal"
    for item_filter in element.get("filters", []):
        if item_filter.get("filterType") == "ABI":
            abi = item_filter.get("value")
    return abi


def main():
    if len(sys.argv) < 3:
        print("usage: verify-apk-outputs.py <apk-dir> <required-abi>...", file=sys.stderr)
        return 2

    apk_dir = Path(sys.argv[1])
    required_abis = set(sys.argv[2:])
    metadata_path = apk_dir / "output-metadata.json"

    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"metadata file not found: {metadata_path}", file=sys.stderr)
        return 1
    except json.JSONDecodeError as error:
        print(f"metadata file is not valid JSON: {metadata_path}: {error}", file=sys.stderr)
        return 1

    elements = metadata.get("elements")
    if not isinstance(elements, list) or not elements:
        print(f"metadata file has no APK elements: {metadata_path}", file=sys.stderr)
        return 1

    seen = {}
    for element in elements:
        abi = detect_abi(element)
        if abi not in required_abis:
            print(f"unexpected APK ABI output: {abi}", file=sys.stderr)
            return 1
        if abi in seen:
            print(f"duplicate APK ABI output: {abi}", file=sys.stderr)
            return 1

        output_file = element.get("outputFile")
        if not output_file:
            print(f"APK element is missing outputFile for ABI {abi}", file=sys.stderr)
            return 1

        apk_path = apk_dir / output_file
        if not apk_path.is_file():
            print(f"APK file not found for ABI {abi}: {apk_path}", file=sys.stderr)
            return 1

        seen[abi] = apk_path

    missing = required_abis - set(seen)
    if missing:
        print(f"missing required APK ABI outputs: {', '.join(sorted(missing))}", file=sys.stderr)
        return 1

    for abi in sorted(seen):
        print(f"{abi}: {seen[abi]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
