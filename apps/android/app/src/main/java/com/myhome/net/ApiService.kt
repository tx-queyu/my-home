package com.myhome.net

import com.myhome.net.dto.ApplianceDto
import com.myhome.net.dto.ApplianceRequest
import com.myhome.net.dto.AuthResponse
import com.myhome.net.dto.ChangePasswordRequest
import com.myhome.net.dto.CreateMemberRequest
import com.myhome.net.dto.DeviceCommandAckRequest
import com.myhome.net.dto.DeviceCommandCreateRequest
import com.myhome.net.dto.DeviceCommandDto
import com.myhome.net.dto.DeviceDto
import com.myhome.net.dto.DeviceRegisterRequest
import com.myhome.net.dto.FamilyInfo
import com.myhome.net.dto.LoginRequest
import com.myhome.net.dto.MemberInfo
import com.myhome.net.dto.FamilyPointAccountDto
import com.myhome.net.dto.PointMeDto
import com.myhome.net.dto.PointTransactionDto
import com.myhome.net.dto.RedeemRequest
import com.myhome.net.dto.RedemptionDto
import com.myhome.net.dto.RefreshRequest
import com.myhome.net.dto.RegisterRequest
import com.myhome.net.dto.RewardDto
import com.myhome.net.dto.RewardRequest
import com.myhome.net.dto.ResetPasswordByAdminRequest
import com.myhome.net.dto.CourseDto
import com.myhome.net.dto.CourseExperienceRequest
import com.myhome.net.dto.CourseExperienceResult
import com.myhome.net.dto.ChildWordMasteryDto
import com.myhome.net.dto.TextbookCoverageDto
import com.myhome.net.dto.SelfStudyTextbookCreateRequest
import com.myhome.net.dto.SelfStudyTextbookDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TextbookOptionDto
import com.myhome.net.dto.WordDto
import com.myhome.net.dto.WordAssessmentResult
import com.myhome.net.dto.WordScoreRequest
import com.myhome.net.dto.WordScoreResponse
import com.myhome.net.dto.SystemFamilyDetailDto
import com.myhome.net.dto.SystemFamilyPageDto
import com.myhome.net.dto.SystemRoleDto
import com.myhome.net.dto.SystemUserDto
import com.myhome.net.dto.SystemUserPageDto
import com.myhome.net.dto.SystemUserCreateRequest
import com.myhome.net.dto.SystemUserUpdateRequest
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.TaskRecordDto
import com.myhome.net.dto.TaskRequest
import com.myhome.net.dto.TtsRequest
import com.myhome.net.dto.UpdateMemberRolesRequest
import com.myhome.net.dto.UserInfo
import com.myhome.net.dto.ChangeEmailRequest
import com.myhome.net.dto.ChangePhoneRequest
import com.myhome.net.dto.EmailConfigCreateRequest
import com.myhome.net.dto.EmailConfigDto
import com.myhome.net.dto.EmailConfigUpdateRequest
import com.myhome.net.dto.LoginByCodeRequest
import com.myhome.net.dto.ResetPasswordRequest
import com.myhome.net.dto.SendCodeResponse
import com.myhome.net.dto.SmsConfigCreateRequest
import com.myhome.net.dto.SmsConfigDto
import com.myhome.net.dto.SmsConfigUpdateRequest
import com.myhome.net.dto.TestResultDto
import com.myhome.net.dto.VerificationCodeSendRequest
import com.myhome.net.dto.VerificationCodeVerifyRequest
import com.myhome.net.dto.VerifyTokenResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(): UserInfo

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("api/families/me")
    suspend fun getMyFamily(): FamilyInfo

    @GET("api/families/members")
    suspend fun listMembers(): List<MemberInfo>

    @POST("api/families/members")
    suspend fun createMember(@Body body: CreateMemberRequest): MemberInfo

    @DELETE("api/families/members/{id}")
    suspend fun deleteMember(@Path("id") id: String)

    @POST("api/families/members/{id}/reset-password")
    suspend fun resetMemberPassword(
        @Path("id") id: String,
        @Body body: ResetPasswordByAdminRequest,
    )

    @PUT("api/families/members/{id}/roles")
    suspend fun updateMemberRoles(
        @Path("id") id: String,
        @Body body: UpdateMemberRolesRequest,
    ): MemberInfo

    @GET("api/appliances")
    suspend fun listAppliances(): List<ApplianceDto>

    @POST("api/appliances")
    suspend fun createAppliance(@Body body: ApplianceRequest): ApplianceDto

    @GET("api/appliances/{id}")
    suspend fun getAppliance(@Path("id") id: String): ApplianceDto

    @PUT("api/appliances/{id}")
    suspend fun updateAppliance(@Path("id") id: String, @Body body: ApplianceRequest): ApplianceDto

    @DELETE("api/appliances/{id}")
    suspend fun deleteAppliance(@Path("id") id: String)

    // ---- 教育：课程（系统预置，只读） ----
    @GET("api/courses")
    suspend fun listCourses(@Query("subject") subject: String? = null): List<CourseDto>

    @GET("api/courses/{id}")
    suspend fun getCourse(@Path("id") id: String): CourseDto

    @GET("api/courses/{id}/words")
    suspend fun listCourseWords(@Path("id") id: String): List<WordDto>

    @GET("api/courses/{id}/words/next")
    suspend fun listNextWords(
        @Path("id") id: String,
        @Query("limit") limit: Int = 10,
        @Query("mode") mode: String = "adaptive",
    ): List<WordDto>

    @Multipart
    @POST("api/courses/{course_id}/words/{word_id}/assess")
    suspend fun assessWord(
        @Path("course_id") courseId: String,
        @Path("word_id") wordId: String,
        @Part audio: MultipartBody.Part,
        @Part("ref_text_override") refTextOverride: RequestBody?,
        @Part("category") category: RequestBody?,
    ): WordAssessmentResult

    // 学习/测评课：离线评分回写（v0.15.0）
    @POST("api/courses/{course_id}/words/{word_id}/score")
    suspend fun submitWordScore(
        @Path("course_id") courseId: String,
        @Path("word_id") wordId: String,
        @Body body: WordScoreRequest,
    ): WordScoreResponse

    // ---- 教育:能力模型（v0.14.0 全局化；v0.16.2 教材维度） ----
    @GET("api/skills/me")
    suspend fun getMySkillOverview(): SkillOverviewDto

    @GET("api/skills/me/textbooks")
    suspend fun listMyTextbookCoverage(): List<TextbookCoverageDto>

    @GET("api/skills/me/words")
    suspend fun listMyWordMastery(
        @Query("state") state: String? = null,
        @Query("subject") subject: String? = null,
        @Query("textbook") textbook: String? = null,
    ): List<ChildWordMasteryDto>

    @GET("api/skills/children/{child_id}")
    suspend fun getChildSkillOverview(
        @Path("child_id") childId: String,
    ): SkillOverviewDto

    @GET("api/skills/children/{child_id}/textbooks")
    suspend fun listChildTextbookCoverage(
        @Path("child_id") childId: String,
    ): List<TextbookCoverageDto>

    @GET("api/skills/children/{child_id}/words")
    suspend fun listChildWordMastery(
        @Path("child_id") childId: String,
        @Query("state") state: String? = null,
        @Query("subject") subject: String? = null,
        @Query("textbook") textbook: String? = null,
    ): List<ChildWordMasteryDto>

    // ---- 家长自学教材（v0.16.1） ----
    @GET("api/self-study/textbooks")
    suspend fun listMyTextbooks(): List<SelfStudyTextbookDto>

    @GET("api/self-study/textbooks/available")
    suspend fun listAvailableTextbooks(): List<TextbookOptionDto>

    @POST("api/self-study/textbooks")
    suspend fun addTextbook(@Body body: SelfStudyTextbookCreateRequest): SelfStudyTextbookDto

    // ---- TTS（Edge TTS 代理，朗读练习用） ----
    @POST("api/tts")
    suspend fun synthTts(@Body body: TtsRequest): okhttp3.ResponseBody

    // ---- 系统管理：课程管理（admin only，只读 + 启停 + 体验） ----
    @GET("api/system/courses")
    suspend fun adminListCourses(@Query("subject") subject: String? = null): List<CourseDto>

    @POST("api/system/courses/{id}/activate")
    suspend fun activateCourse(@Path("id") id: String): CourseDto

    @POST("api/system/courses/{id}/deactivate")
    suspend fun deactivateCourse(@Path("id") id: String): CourseDto

    @POST("api/system/courses/{id}/experience")
    suspend fun experienceCourse(
        @Path("id") id: String,
        @Body body: CourseExperienceRequest,
    ): CourseExperienceResult

    // ---- 教育：任务 ----
    @GET("api/tasks")
    suspend fun listTasks(@Query("include_inactive") includeInactive: Boolean = false): List<TaskDto>

    @POST("api/tasks")
    suspend fun createTask(@Body body: TaskRequest): TaskDto

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): TaskDto

    @PUT("api/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: TaskRequest): TaskDto

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)

    @POST("api/tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): TaskRecordDto

    @GET("api/tasks/records")
    suspend fun listTaskRecords(@Query("user_id") userId: String? = null): List<TaskRecordDto>

    @DELETE("api/tasks/records/{id}")
    suspend fun deleteTaskRecord(@Path("id") id: String)

    // ---- 教育：积分 ----
    @GET("api/points/me")
    suspend fun getMyPoints(): PointMeDto

    @GET("api/points/transactions")
    suspend fun listPointTransactions(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): List<PointTransactionDto>

    @GET("api/points/family")
    suspend fun listFamilyPointAccounts(): List<FamilyPointAccountDto>

    // ---- 教育：奖励 + 兑换 ----
    @GET("api/rewards")
    suspend fun listRewards(@Query("include_inactive") includeInactive: Boolean = false): List<RewardDto>

    @POST("api/rewards")
    suspend fun createReward(@Body body: RewardRequest): RewardDto

    @GET("api/rewards/{id}")
    suspend fun getReward(@Path("id") id: String): RewardDto

    @PUT("api/rewards/{id}")
    suspend fun updateReward(@Path("id") id: String, @Body body: RewardRequest): RewardDto

    @DELETE("api/rewards/{id}")
    suspend fun deleteReward(@Path("id") id: String)

    @GET("api/redemptions")
    suspend fun listRedemptions(
        @Query("status") status: String? = null,
        @Query("user_id") userId: String? = null,
    ): List<RedemptionDto>

    @POST("api/redemptions")
    suspend fun createRedemption(@Body body: RedeemRequest): RedemptionDto

    @POST("api/redemptions/{id}/fulfill")
    suspend fun fulfillRedemption(@Path("id") id: String): RedemptionDto

    @POST("api/redemptions/{id}/reject")
    suspend fun rejectRedemption(@Path("id") id: String): RedemptionDto

    // ---- 设备管控 ----
    @POST("api/devices/register")
    suspend fun registerDevice(@Body body: DeviceRegisterRequest): DeviceDto

    @GET("api/devices")
    suspend fun listDevices(): List<DeviceDto>

    @GET("api/devices/{id}")
    suspend fun getDevice(@Path("id") id: String): DeviceDto

    @POST("api/devices/{id}/commands")
    suspend fun issueDeviceCommand(
        @Path("id") id: String,
        @Body body: DeviceCommandCreateRequest,
    ): DeviceCommandDto

    @GET("api/devices/me/commands/poll")
    suspend fun pollDeviceCommands(
        @Header("X-Device-Id") deviceId: String,
        @Query("timeout") timeoutSec: Int = 60,
    ): List<DeviceCommandDto>

    @POST("api/devices/me/commands/{cmd_id}/ack")
    suspend fun ackDeviceCommand(
        @Path("cmd_id") cmdId: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: DeviceCommandAckRequest,
    ): DeviceCommandDto

    // ---- 系统管理（admin only） ----
    @GET("api/system/users")
    suspend fun listSystemUsers(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("family_id") familyId: String? = null,
        @Query("role") role: String? = null,
        @Query("active") active: Boolean? = null,
        @Query("q") q: String? = null,
    ): SystemUserPageDto

    @POST("api/system/users")
    suspend fun createSystemUser(@Body body: SystemUserCreateRequest): SystemUserDto

    @PUT("api/system/users/{id}")
    suspend fun updateSystemUser(
        @Path("id") id: String,
        @Body body: SystemUserUpdateRequest,
    ): SystemUserDto

    @DELETE("api/system/users/{id}")
    suspend fun deleteSystemUser(@Path("id") id: String)

    @POST("api/system/users/{id}/reset-password")
    suspend fun adminResetUserPassword(
        @Path("id") id: String,
        @Body body: ResetPasswordByAdminRequest,
    )

    @GET("api/system/families")
    suspend fun listSystemFamilies(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("q") q: String? = null,
        @Query("has_members") hasMembers: Boolean? = null,
    ): SystemFamilyPageDto

    @GET("api/system/families/{id}")
    suspend fun getSystemFamilyDetail(@Path("id") id: String): SystemFamilyDetailDto

    @DELETE("api/system/families/{id}")
    suspend fun deleteSystemFamily(@Path("id") id: String)

    @GET("api/system/roles")
    suspend fun listSystemRoles(): List<SystemRoleDto>

    // ---- 验证码（用户端） ----
    @POST("api/auth/verification-code/send")
    suspend fun sendVerificationCode(@Body body: VerificationCodeSendRequest): SendCodeResponse

    @POST("api/auth/verification-code/verify")
    suspend fun verifyCode(@Body body: VerificationCodeVerifyRequest): VerifyTokenResponse

    @POST("api/auth/login-by-code")
    suspend fun loginByCode(@Body body: LoginByCodeRequest): AuthResponse

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Unit

    @POST("api/auth/change-phone")
    suspend fun changePhone(@Body body: ChangePhoneRequest): UserInfo

    @POST("api/auth/change-email")
    suspend fun changeEmail(@Body body: ChangeEmailRequest): UserInfo

    // ---- 短信服务商配置（admin only） ----
    @GET("api/system/sms-configs")
    suspend fun listSmsConfigs(): List<SmsConfigDto>

    @POST("api/system/sms-configs")
    suspend fun createSmsConfig(@Body body: SmsConfigCreateRequest): SmsConfigDto

    @PUT("api/system/sms-configs/{id}")
    suspend fun updateSmsConfig(
        @Path("id") id: String,
        @Body body: SmsConfigUpdateRequest,
    ): SmsConfigDto

    @DELETE("api/system/sms-configs/{id}")
    suspend fun deleteSmsConfig(@Path("id") id: String): Unit

    @POST("api/system/sms-configs/{id}/activate")
    suspend fun activateSmsConfig(@Path("id") id: String): SmsConfigDto

    @POST("api/system/sms-configs/{id}/deactivate")
    suspend fun deactivateSmsConfig(@Path("id") id: String): SmsConfigDto

    @POST("api/system/sms-configs/{id}/test")
    suspend fun testSmsConfig(@Path("id") id: String): TestResultDto

    // ---- 邮件服务商配置（admin only） ----
    @GET("api/system/email-configs")
    suspend fun listEmailConfigs(): List<EmailConfigDto>

    @POST("api/system/email-configs")
    suspend fun createEmailConfig(@Body body: EmailConfigCreateRequest): EmailConfigDto

    @PUT("api/system/email-configs/{id}")
    suspend fun updateEmailConfig(
        @Path("id") id: String,
        @Body body: EmailConfigUpdateRequest,
    ): EmailConfigDto

    @DELETE("api/system/email-configs/{id}")
    suspend fun deleteEmailConfig(@Path("id") id: String): Unit

    @POST("api/system/email-configs/{id}/activate")
    suspend fun activateEmailConfig(@Path("id") id: String): EmailConfigDto

    @POST("api/system/email-configs/{id}/deactivate")
    suspend fun deactivateEmailConfig(@Path("id") id: String): EmailConfigDto

    @POST("api/system/email-configs/{id}/test")
    suspend fun testEmailConfig(@Path("id") id: String): TestResultDto
}
