import tempfile
import unittest
from pathlib import Path

from failed_tests import failed_tests

XML = """<testsuite name="s" tests="2">
  <testcase classname="app.LoginTest" name="entra"/>
  <testcase classname="app.LoginTest" name="falha"><failure message="x"/></testcase>
</testsuite>"""


class FailedTestsTest(unittest.TestCase):
    def test_lists_only_failed_cases_with_module(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            out = root / "feature-a/build/test-results/iosSimulatorArm64Test"
            out.mkdir(parents=True)
            (out / "TEST-app.LoginTest.xml").write_text(XML)
            self.assertEqual(failed_tests(root), ["feature-a · app.LoginTest.falha"])

    def test_no_reports_means_no_failures(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(failed_tests(Path(tmp)), [])


if __name__ == "__main__":
    unittest.main()
