package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.CourseDto
import com.myhome.net.dto.CourseExperienceRequest
import com.myhome.net.dto.CourseExperienceResult
import com.myhome.net.dto.WordAssessmentResult
import com.myhome.net.dto.WordDto
import com.myhome.net.dto.WordScoreRequest
import com.myhome.net.dto.WordScoreResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val api: ApiService,
) {
    // 用户端只读
    suspend fun list(subject: String? = null): List<CourseDto> = api.listCourses(subject)
    suspend fun get(id: String): CourseDto = api.getCourse(id)
    suspend fun listWords(courseId: String): List<WordDto> = api.listCourseWords(courseId)

    /**
     * 自适应选词（v0.13.0）—— 后端基于该用户的 ChildWordMastery 排序。
     *
     * - adaptive: 70% 学习 + 30% 复习（默认）
     * - learn:    全量按 mastery 升序
     * - review:   仅已掌握词按最久未练排序
     * - random:   完全随机（旧行为）
     */
    suspend fun listNextWords(
        courseId: String,
        limit: Int = 10,
        mode: String = "adaptive",
    ): List<WordDto> = api.listNextWords(courseId, limit, mode)

    /**
     * 评测用户朗读单词的发音。
     * audio: 16kHz 16bit mono PCM raw bytes（不带 WAV header）
     * 后端按 multipart/form-data 上传，文件名 "audio.pcm"。
     *
     * 可选参数：
     * - refTextOverride: 自定义参考文本，覆盖默认 word.spelling。
     *     拼读+连读场景传多行 paper（如 "D\nO\nG\ndog"）让 ISE 评估整段。
     * - category: 评测题型，默认 "read_word"。当前 ISE en_vip 仅 read_word 验证可用。
     */
    suspend fun assessWord(
        courseId: String,
        wordId: String,
        audio: ByteArray,
        refTextOverride: String? = null,
        category: String = "read_word",
    ): WordAssessmentResult {
        val body = audio.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("audio", "audio.pcm", body)
        val refTextPart = refTextOverride?.toRequestBody("text/plain".toMediaTypeOrNull())
        val categoryPart = category.toRequestBody("text/plain".toMediaTypeOrNull())
        return api.assessWord(courseId, wordId, part, refTextPart, categoryPart)
    }

    // admin 端管理（只读 + 启停 + 体验）
    suspend fun adminList(subject: String? = null): List<CourseDto> = api.adminListCourses(subject)
    suspend fun activate(id: String): CourseDto = api.activateCourse(id)
    suspend fun deactivate(id: String): CourseDto = api.deactivateCourse(id)
    suspend fun experience(
        id: String,
        childId: String? = null,
    ): CourseExperienceResult = api.experienceCourse(id, CourseExperienceRequest(childId))

    /** 学习/测评课离线评分回写（v0.15.0）：客户端判对错后回传 score。 */
    suspend fun submitWordScore(
        courseId: String,
        wordId: String,
        score: Int,
    ): WordScoreResponse = api.submitWordScore(courseId, wordId, WordScoreRequest(score))
}
