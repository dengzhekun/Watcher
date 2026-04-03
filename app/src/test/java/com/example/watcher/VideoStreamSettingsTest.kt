package com.example.watcher

import com.example.watcher.data.model.VideoStreamSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStreamSettingsTest {
    @Test
    fun `default resolution is hd`() {
        assertEquals(VideoStreamSettings.DEFAULT_RESOLUTION, VideoStreamSettings().resolution)
    }

    @Test
    fun `normalize resolution supports legacy aliases`() {
        assertEquals("HD", VideoStreamSettings.normalizeResolution("1280x720"))
        assertEquals("VGA", VideoStreamSettings.normalizeResolution("640x480"))
        assertEquals("QVGA", VideoStreamSettings.normalizeResolution("320x240"))
    }

    @Test
    fun `framesize mapping matches esp32 camera web values`() {
        assertEquals(11, VideoStreamSettings.framesizeValueFor("HD"))
        assertEquals(8, VideoStreamSettings.framesizeValueFor("VGA"))
        assertEquals(5, VideoStreamSettings.framesizeValueFor("QVGA"))
    }
}
