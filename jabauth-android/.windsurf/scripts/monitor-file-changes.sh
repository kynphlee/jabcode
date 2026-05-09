#!/bin/bash
# File corruption monitor - detects blank file writes

# Change to project root (script must be in .windsurf/scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

LOG_FILE=".windsurf/logs/file-monitor.log"
mkdir -p "$(dirname "$LOG_FILE")"

echo "=== File Monitor Started: $(date) ===" >> "$LOG_FILE"
echo "Monitoring: $PROJECT_ROOT" >> "$LOG_FILE"

# Watch for zero-byte file writes
inotifywait -m -r \
  --exclude '(\.git|\.gradle|build|\.idea)' \
  -e modify \
  apps/ framework/ \
  --format '%T %w%f %e' \
  --timefmt '%Y-%m-%d %H:%M:%S' | \
while read timestamp file event; do
  if [ -f "$file" ] && [ ! -s "$file" ]; then
    echo "⚠️  BLANK FILE DETECTED: $timestamp - $file" | tee -a "$LOG_FILE"
    # Auto-restore from git if possible
    if git ls-files --error-unmatch "$file" &>/dev/null; then
      echo "   → Restoring from git..." | tee -a "$LOG_FILE"
      git checkout HEAD -- "$file"
    fi
  fi
done
