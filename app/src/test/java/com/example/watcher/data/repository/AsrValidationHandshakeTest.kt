package com.example.watcher.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AsrValidationHandshakeTest {
    @Test
    fun buildsInitFirstAudioAndLastPacketFrames() {
        val frames = buildValidationHandshakeFrames(
            clientInfo = VolcengineAsrWireProtocol.ClientInfo(
                uid = "com.example.watcher",
                deviceId = "pixel",
                platform = "Android 15",
                appVersion = "1.0.4"
            ),
            sampleRate = 16000,
            bitsPerSample = 16,
            channelCount = 1,
            audioChunkBytes = 8
        )

        assertEquals(3, frames.size)

        val initFrame = VolcengineAsrWireProtocol.decode(frames[0])
        val initPayload = JSONObject(initFrame.payloadText)
        assertEquals(VolcengineAsrWireProtocol.MESSAGE_TYPE_FULL_CLIENT_REQUEST, initFrame.messageType)
        assertEquals("bigmodel", initPayload.getJSONObject("request").getString("model_name"))

        val firstAudioFrame = VolcengineAsrWireProtocol.decode(frames[1])
        assertEquals(VolcengineAsrWireProtocol.MESSAGE_TYPE_AUDIO_ONLY_REQUEST, firstAudioFrame.messageType)
        assertFalse(firstAudioFrame.isLastPacket)
        assertArrayEquals(ByteArray(8), firstAudioFrame.payload)

        val lastAudioFrame = VolcengineAsrWireProtocol.decode(frames[2])
        assertEquals(VolcengineAsrWireProtocol.MESSAGE_TYPE_AUDIO_ONLY_REQUEST, lastAudioFrame.messageType)
        assertTrue(lastAudioFrame.isLastPacket)
        assertArrayEquals(ByteArray(0), lastAudioFrame.payload)
    }
}
