package io.github.al3j4ndrolm.faciliscam

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapDecoderTest {

    @Test
    fun smallImageIsNotSampled() {
        assertEquals(1, BitmapDecoder.sampleSizeFor(1000, 800, maxSize = 2048))
    }

    @Test
    fun sampleKeepsLongestSideAtLeastMaxSize() {
        // 4000 / 2 = 2000 >= 1080, 4000 / 4 = 1000 < 1080
        assertEquals(2, BitmapDecoder.sampleSizeFor(4000, 3000, maxSize = 1080))
        assertEquals(4, BitmapDecoder.sampleSizeFor(3000, 8000, maxSize = 2000))
    }

    @Test
    fun exactMultipleUsesLargerSample() {
        assertEquals(4, BitmapDecoder.sampleSizeFor(4096, 3072, maxSize = 1024))
    }
}
