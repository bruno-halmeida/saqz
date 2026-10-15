import unittest

from publish_stores import Abort, decide_ios_version, play_release_for, validate_notes

LIVE = ("1.0.4", "READY_FOR_SALE")


class DecideIosVersion(unittest.TestCase):
    def test_creates_when_nothing_in_queue(self):
        self.assertEqual(decide_ios_version([LIVE, ("1.0.3", "REPLACED_WITH_NEW_VERSION")], "1.0.5"), "create")

    def test_reuses_prepare_for_submission_of_tag(self):
        self.assertEqual(decide_ios_version([LIVE, ("1.0.5", "PREPARE_FOR_SUBMISSION")], "1.0.5"), "reuse")

    def test_stops_when_other_version_waiting(self):
        for state in ("WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_DEVELOPER_RELEASE", "PREPARE_FOR_SUBMISSION"):
            with self.assertRaisesRegex(Abort, "ocupando a fila"):
                decide_ios_version([LIVE, ("1.0.9", state)], "1.0.5")

    def test_stops_when_tag_version_already_in_queue_or_live(self):
        with self.assertRaisesRegex(Abort, "em andamento"):
            decide_ios_version([LIVE, ("1.0.5", "WAITING_FOR_REVIEW")], "1.0.5")
        with self.assertRaisesRegex(Abort, "já está na App Store"):
            decide_ios_version([("1.0.5", "READY_FOR_SALE")], "1.0.5")

    def test_requires_a_released_previous_version(self):
        with self.assertRaisesRegex(Abort, "primeira publicação"):
            decide_ios_version([], "1.0.5")


class Helpers(unittest.TestCase):
    def test_play_release_for(self):
        rels = [{"versionCodes": ["15"]}, {"versionCodes": ["16"], "status": "completed"}]
        self.assertEqual(play_release_for(rels, 16)["status"], "completed")
        self.assertIsNone(play_release_for(rels, 17))
        self.assertIsNone(play_release_for(None, 1))

    def test_notes(self):
        self.assertEqual(validate_notes("  oi \n"), "oi")
        with self.assertRaises(Abort):
            validate_notes("  ")
        with self.assertRaises(Abort):
            validate_notes("x" * 501)
        self.assertEqual(len(validate_notes("x" * 500)), 500)


if __name__ == "__main__":
    unittest.main()
