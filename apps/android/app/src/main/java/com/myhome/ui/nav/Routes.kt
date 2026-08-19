package com.myhome.ui.nav

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val APPLIANCE_DETAIL = "appliance_detail/{id}"
    const val APPLIANCE_FORM = "appliance_form?id={id}"

    const val EDUCATION = "education"
    const val TASK_DETAIL = "task_detail/{id}"
    const val TASK_FORM = "task_form?id={id}"
    const val POINTS = "points"
    const val REWARDS = "rewards"
    const val REWARD_FORM = "reward_form?id={id}"
    const val REDEMPTIONS = "redemptions"

    const val MINE = "mine"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val VERSION_INFO = "version_info"
    const val UPDATE = "update"
    const val DEVICE_CONTROL = "device_control"
    const val DEVICE_OWNER_SETUP = "device_owner_setup"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "device_detail/{id}"
    const val FAMILY_MEMBERS = "family_members"
    const val MEMBER_FORM = "member_form"
    const val SYSTEM = "system"
    const val USER_LIST = "user_list"
    const val FAMILY_LIST = "family_list"
    const val FAMILY_DETAIL = "family_detail/{id}"
    const val ROLE_LIST = "role_list"
    const val USER_EDIT = "user_edit?id={id}"
    const val SWITCH_ACCOUNT = "switch_account"

    // 验证码功能（v0.7.0）
    const val CODE_LOGIN = "code_login"
    const val RESET_PASSWORD = "reset_password"
    const val CHANGE_PASSWORD = "change_password"
    const val CHANGE_PHONE = "change_phone"
    const val CHANGE_EMAIL = "change_email"
    const val SMS_CONFIG_LIST = "sms_config_list"
    const val SMS_CONFIG_EDIT = "sms_config_edit?id={id}"
    const val EMAIL_CONFIG_LIST = "email_config_list"
    const val EMAIL_CONFIG_EDIT = "email_config_edit?id={id}"
    const val COURSE_LIST = "course_list"
    const val COURSE_DETAIL = "course_detail/{id}"
    const val READING_SESSION = "reading_session/{id}"
    const val READING_SESSION_TASK = "reading_session_task/{courseId}/{taskId}"
    const val LEARN_SESSION = "learn_session/{id}"
    const val LEARN_SESSION_TASK = "learn_session_task/{courseId}/{taskId}"
    const val QUIZ_SESSION = "quiz_session/{id}"
    const val QUIZ_SESSION_TASK = "quiz_session_task/{courseId}/{taskId}"
    // 家长自学(v0.16.0):与体验模式同流程,但不结算积分
    const val READING_SESSION_SELF = "reading_session_self/{id}"
    const val LEARN_SESSION_SELF = "learn_session_self/{id}"
    const val QUIZ_SESSION_SELF = "quiz_session_self/{id}"
    const val SKILL_CENTER = "skill_center"
    const val SKILL_CENTER_CHILD = "skill_center_child/{childId}/{childName}"

    // Phase 4（v0.17.0）：每日打卡 / 学科成绩 / 学习时长
    const val HABITS = "habits"
    const val HABIT_FORM = "habit_form?id={id}"
    const val GRADES = "grades"
    const val GRADE_FORM = "grade_form?id={id}"
    const val STUDY_STATS = "study_stats"

    fun applianceDetail(id: String) = "appliance_detail/$id"
    fun applianceForm(id: String? = null) =
        if (id == null) "appliance_form?id=" else "appliance_form?id=$id"

    fun taskDetail(id: String) = "task_detail/$id"
    fun taskForm(id: String? = null) =
        if (id == null) "task_form?id=" else "task_form?id=$id"

    fun rewardForm(id: String? = null) =
        if (id == null) "reward_form?id=" else "reward_form?id=$id"

    fun deviceDetail(id: String) = "device_detail/$id"
    fun userEdit(id: String? = null) =
        if (id == null) "user_edit?id=" else "user_edit?id=$id"
    fun familyDetail(id: String) = "family_detail/$id"
    fun smsConfigEdit(id: String? = null) =
        if (id == null) "sms_config_edit?id=" else "sms_config_edit?id=$id"
    fun emailConfigEdit(id: String? = null) =
        if (id == null) "email_config_edit?id=" else "email_config_edit?id=$id"
    fun courseDetail(id: String) = "course_detail/$id"
    fun readingSession(id: String) = "reading_session/$id"
    fun readingSessionTask(courseId: String, taskId: String) = "reading_session_task/$courseId/$taskId"
    fun learnSession(id: String) = "learn_session/$id"
    fun learnSessionTask(courseId: String, taskId: String) = "learn_session_task/$courseId/$taskId"
    fun quizSession(id: String) = "quiz_session/$id"
    fun quizSessionTask(courseId: String, taskId: String) = "quiz_session_task/$courseId/$taskId"
    fun readingSessionSelf(id: String) = "reading_session_self/$id"
    fun learnSessionSelf(id: String) = "learn_session_self/$id"
    fun quizSessionSelf(id: String) = "quiz_session_self/$id"
    fun skillCenterChild(childId: String, childName: String) =
        "skill_center_child/$childId/${android.net.Uri.encode(childName)}"
    fun habitForm(id: String? = null) =
        if (id == null) "habit_form?id=" else "habit_form?id=$id"
    fun gradeForm(id: String? = null) =
        if (id == null) "grade_form?id=" else "grade_form?id=$id"
}
