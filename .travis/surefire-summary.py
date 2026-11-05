"""Print every skipped test with its reason, then the suite totals (last)."""
import glob
import xml.etree.ElementTree as ET

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
skipped = []
for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
    root = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(root.get(key, "0") or 0)
    for case in root.iter("testcase"):
        node = case.find("skipped")
        if node is not None:
            reason = node.get("message") or (node.text or "").strip() or "(no message)"
            skipped.append("%s.%s: %s" % (case.get("classname"), case.get("name"), reason))

print("Skipped tests (%d):" % len(skipped))
for line in skipped:
    print("  SKIPPED " + line)
print(
    "surefire totals: tests=%(tests)d failures=%(failures)d errors=%(errors)d skipped=%(skipped)d"
    % totals
)
print("passed=%d" % (totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]))
