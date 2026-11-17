#!/usr/bin/env bash
# The demo, end to end over the REST API: both offices, a visitor, budget limits, HR, the report.
# It runs every ```bash block in docs/DEMO-CURLS.md in order (that file is the single source of the curls).
#
#   mvn spring-boot:run      # in another terminal; a FRESH start, since the ids assume empty storage
#   ./demo.sh
set -euo pipefail

DOC="$(cd "$(dirname "$0")" && pwd)/docs/DEMO-CURLS.md"
export API="${API:-http://localhost:8085}"

command -v jq > /dev/null || { echo "demo.sh needs jq: brew install jq"; exit 1; }
curl -sf -o /dev/null "$API/api/refills" || { echo "No app at $API. Start it first: mvn spring-boot:run"; exit 1; }
[ "$(curl -s "$API/api/refills" | jq length)" = 0 ] || { echo "The app already has refills: restart it for a clean demo."; exit 1; }

python3 - "$DOC" <<'PY' | bash -euo pipefail
import re, shlex, sys

pending = []          # headings waiting for their first bash block
in_block = False
for line in open(sys.argv[1], encoding="utf-8").read().splitlines():
    if line == "```bash":
        in_block = True
        for level, title in pending:
            colour = "1;36" if level == 2 else "1"
            print(f"printf '\\n\\033[{colour}m%s\\033[0m\\n' {shlex.quote(title)}")
        pending = []
    elif line == "```" and in_block:
        in_block = False
    elif in_block:
        print(line)
    elif m := re.match(r"^(#{2,3}) (.+)", line):
        level = len(m.group(1))
        pending = [p for p in pending if p[0] < level] + [(level, m.group(2))]
PY
