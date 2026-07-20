package br.com.saqz.androidapp.groups.photo

import br.com.saqz.groups.port.GroupPhotoCrop
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSquareCropTest {
    @Test fun landscapeCenterUsesFullShortAxis() {
        assertEquals(AndroidPixelCrop(100, 0, 200), AndroidSquareCrop.calculate(400, 200, GroupPhotoCrop()))
    }

    @Test fun portraitCenterUsesFullShortAxis() {
        assertEquals(AndroidPixelCrop(0, 100, 200), AndroidSquareCrop.calculate(200, 400, GroupPhotoCrop()))
    }

    @Test fun squareSourceRemainsSquare() {
        assertEquals(AndroidPixelCrop(0, 0, 300), AndroidSquareCrop.calculate(300, 300, GroupPhotoCrop()))
    }

    @Test fun zoomProducesSmallerSquare() {
        assertEquals(AndroidPixelCrop(150, 50, 100), AndroidSquareCrop.calculate(400, 200, GroupPhotoCrop(zoom = 2f)))
    }

    @Test fun leftEdgeIsClampedWithoutChangingSide() {
        assertEquals(AndroidPixelCrop(0, 0, 200), AndroidSquareCrop.calculate(400, 200, GroupPhotoCrop(centerX = 0f)))
    }

    @Test fun rightEdgeIsClampedWithoutChangingSide() {
        assertEquals(AndroidPixelCrop(200, 0, 200), AndroidSquareCrop.calculate(400, 200, GroupPhotoCrop(centerX = 1f)))
    }

    @Test fun bottomEdgeIsClampedWithoutChangingSide() {
        assertEquals(AndroidPixelCrop(0, 200, 200), AndroidSquareCrop.calculate(200, 400, GroupPhotoCrop(centerY = 1f)))
    }
}
