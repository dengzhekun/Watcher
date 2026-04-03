package com.example.watcher

import com.example.watcher.data.local.MonitorTaskDao
import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.MonitorTask
import com.example.watcher.data.remote.ArkFileResponse
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoImageRequest
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.DoubaoResponse
import com.example.watcher.data.remote.DoubaoVideoRequest
import com.example.watcher.data.repository.IntentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRepositoryPromptTest {
    @Test
    fun `captured frame prompt keeps scene baseline mode`() {
        val prompt = buildIntentPrompt(BaselineSource.CapturedFrame, hasImage = true)

        assertTrue(prompt.contains("SceneBaseline"))
        assertTrue(prompt.contains("monitorMode"))
        assertTrue(prompt.contains("baselineSource"))
    }

    @Test
    fun `uploaded image prompt allows reference target mode`() {
        val prompt = buildIntentPrompt(BaselineSource.UploadedImage, hasImage = true)

        assertTrue(prompt.contains("ReferenceTarget"))
        assertTrue(prompt.contains("targetTrigger"))
        assertTrue(prompt.contains("主体/目标"))
    }

    @Test
    fun `text only prompt forces scene baseline`() {
        val prompt = buildIntentPrompt(BaselineSource.CapturedFrame, hasImage = false)

        assertTrue(prompt.contains("SceneBaseline"))
        assertTrue(prompt.contains("checkIntervalSeconds"))
    }

    private fun buildIntentPrompt(baselineSource: BaselineSource, hasImage: Boolean): String {
        val repository = IntentRepository(
            apiService = object : DoubaoApiService {
                override suspend fun analyzeIntent(
                    authorization: String,
                    contentType: String,
                    request: DoubaoRequest
                ): DoubaoResponse {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override suspend fun analyzeImage(
                    authorization: String,
                    contentType: String,
                    request: DoubaoImageRequest
                ): DoubaoResponse {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override suspend fun uploadFile(
                    authorization: String,
                    purpose: RequestBody,
                    file: MultipartBody.Part
                ): ArkFileResponse {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override suspend fun getFile(
                    authorization: String,
                    fileId: String
                ): ArkFileResponse {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override suspend fun analyzeVideo(
                    authorization: String,
                    contentType: String,
                    request: DoubaoVideoRequest
                ): DoubaoResponse {
                    throw UnsupportedOperationException("Not used in this test")
                }
            },
            taskDao = object : MonitorTaskDao {
                override fun observeTasks(): Flow<List<MonitorTask>> = emptyFlow()

                override suspend fun getTaskById(id: Long): MonitorTask? = null

                override suspend fun upsert(task: MonitorTask): Long = 1L

                override suspend fun deleteById(id: Long) = Unit
            }
        )

        val method = IntentRepository::class.java.getDeclaredMethod(
            "buildIntentPrompt",
            BaselineSource::class.java,
            Boolean::class.javaPrimitiveType!!
        )
        method.isAccessible = true
        return method.invoke(repository, baselineSource, hasImage) as String
    }
}
