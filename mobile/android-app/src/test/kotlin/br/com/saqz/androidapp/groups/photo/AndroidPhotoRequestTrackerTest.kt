package br.com.saqz.androidapp.groups.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidPhotoRequestTrackerTest {
    private val target = File("camera-target")

    @Test fun cameraBeginRequiresPrivateTarget() {
        val tracker = AndroidPhotoRequestTracker()
        assertTrue(tracker.begin(AndroidPhotoRequestKind.CAMERA, target))
        assertSame(target, tracker.activeCameraTarget())
    }

    @Test fun libraryBeginRequiresNoCameraTarget() {
        assertTrue(AndroidPhotoRequestTracker().begin(AndroidPhotoRequestKind.LIBRARY))
    }

    @Test fun overlappingRequestIsRejected() {
        val tracker = AndroidPhotoRequestTracker()
        tracker.begin(AndroidPhotoRequestKind.CAMERA, target)
        assertFalse(tracker.begin(AndroidPhotoRequestKind.LIBRARY))
    }

    @Test fun wrongCallbackCannotConsumePendingRequest() {
        val tracker = AndroidPhotoRequestTracker()
        tracker.begin(AndroidPhotoRequestKind.CAMERA, target)
        assertNull(tracker.complete(AndroidPhotoRequestKind.LIBRARY))
        assertSame(target, tracker.activeCameraTarget())
    }

    @Test fun completionIsDeliveredOnlyOnce() {
        val tracker = AndroidPhotoRequestTracker()
        tracker.begin(AndroidPhotoRequestKind.CAMERA, target)
        assertSame(target, tracker.complete(AndroidPhotoRequestKind.CAMERA)?.cameraTarget)
        assertNull(tracker.complete(AndroidPhotoRequestKind.CAMERA))
    }

    @Test fun libraryCompletionIsTypedEvenWithoutFile() {
        val tracker = AndroidPhotoRequestTracker()
        tracker.begin(AndroidPhotoRequestKind.LIBRARY)
        assertEquals(AndroidPhotoRequestCompletion(null), tracker.complete(AndroidPhotoRequestKind.LIBRARY))
    }

    @Test fun cancellationReturnsCameraTargetForCleanup() {
        val tracker = AndroidPhotoRequestTracker()
        tracker.begin(AndroidPhotoRequestKind.CAMERA, target)
        assertSame(target, tracker.cancel())
        assertNull(tracker.activeCameraTarget())
    }
}
