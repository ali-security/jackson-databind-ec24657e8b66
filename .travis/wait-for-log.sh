#!/bin/bash
# Keep the job open until Travis has archived this job's own log up to the test
# summary, so a harvest made right after the job finishes is complete.
# after_script cannot change the build result.
URL="https://api.travis-ci.com/v3/job/${TRAVIS_JOB_ID}/log.txt"
MARKER='surefire totals: tests='
CAP=1500
waited=0
while [ "$waited" -lt "$CAP" ]; do
  if curl -fsSL "$URL" 2>/dev/null | grep -q "$MARKER"; then
    echo "job log archived through the test summary after ${waited}s"
    exit 0
  fi
  echo "waiting for log archive... (${waited}s)"
  sleep 20
  waited=$((waited + 20))
done
echo "log archive wait capped at ${CAP}s"
exit 0
