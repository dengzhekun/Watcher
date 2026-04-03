package com.example.watcher.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LedControlService {
    @GET("/control")
    suspend fun setControl(
        @Query("var") variable: String,
        @Query("val") value: Int
    ): Response<Void>

    @GET("/control")
    suspend fun setBrightness(
        @Query("var") variable: String = "led_intensity",
        @Query("val") value: Int
    ): Response<Void>

    @GET("/status")
    suspend fun getStatus(): Response<Esp32StatusResponse>
}

data class Esp32StatusResponse(
    val led_intensity: Int = 0,
    val framesize: Int = 0,
    val quality: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val special_effect: Int = 0,
    val awb: Int = 0,
    val awb_gain: Int = 0,
    val wb_mode: Int = 0,
    val aec: Int = 0,
    val aec2: Int = 0,
    val ae_level: Int = 0,
    val aec_value: Int = 0,
    val agc: Int = 0,
    val agc_gain: Int = 0,
    val gainceiling: Int = 0,
    val bpc: Int = 0,
    val wpc: Int = 0,
    val raw_gma: Int = 0,
    val lenc: Int = 0,
    val hmirror: Int = 0,
    val vflip: Int = 0,
    val dcw: Int = 0,
    val colorbar: Int = 0,
    val face_detect: Int = 0,
    val face_enroll: Int = 0,
    val face_recognize: Int = 0
)
