#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('.')
for path in root.rglob('*.xml'):
    ET.parse(path)
print('XML resources: OK')
for path in root.rglob('*.kt'):
    text = path.read_text(encoding='utf-8')
    if text.count('{') != text.count('}'):
        raise SystemExit(f'Brace mismatch: {path}')
print('Kotlin structure: OK')
PY
if grep -RInE 'AKIA|AIza|BEGIN PRIVATE KEY|password\s*=|token\s*=' app build.gradle.kts settings.gradle.kts gradle.properties --exclude-dir=.git; then
  echo 'Potential secret detected' >&2
  exit 1
fi
echo 'Secret scan: OK'
