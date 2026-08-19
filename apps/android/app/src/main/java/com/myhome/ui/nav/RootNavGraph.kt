package com.myhome.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myhome.repo.AuthRepository
import com.myhome.net.dto.CourseSessionType
import com.myhome.ui.admin.DeviceControlScreen
import com.myhome.ui.admin.DeviceOwnerSetupScreen
import com.myhome.ui.devices.DeviceDetailScreen
import com.myhome.ui.devices.DeviceListScreen
import com.myhome.ui.education.ChildEducationScreen
import com.myhome.ui.education.GradeFormScreen
import com.myhome.ui.education.GradeListScreen
import com.myhome.ui.education.HabitFormScreen
import com.myhome.ui.education.HabitListScreen
import com.myhome.ui.education.LearnSessionScreen
import com.myhome.ui.education.ParentEducationScreen
import com.myhome.ui.education.PointsScreen
import com.myhome.ui.education.QuizSessionScreen
import com.myhome.ui.education.ReadingSessionScreen
import com.myhome.ui.education.RedemptionListScreen
import com.myhome.ui.education.RewardFormScreen
import com.myhome.ui.education.RewardListScreen
import com.myhome.ui.education.SkillCenterMode
import com.myhome.ui.education.StudyStatsScreen
import com.myhome.ui.education.SkillCenterScreen
import com.myhome.ui.education.TaskDetailScreen
import com.myhome.ui.education.TaskFormScreen
import com.myhome.ui.family.FamilyMembersScreen
import com.myhome.ui.family.MemberFormScreen
import com.myhome.ui.home.ApplianceDetailScreen
import com.myhome.ui.home.ApplianceFormScreen
import com.myhome.ui.home.ApplianceListScreen
import com.myhome.ui.login.CodeLoginScreen
import com.myhome.ui.login.LoginScreen
import com.myhome.ui.login.ResetPasswordScreen
import com.myhome.ui.mine.AboutScreen
import com.myhome.ui.mine.ChangeEmailScreen
import com.myhome.ui.mine.ChangePasswordScreen
import com.myhome.ui.mine.ChangePhoneScreen
import com.myhome.ui.mine.MineScreen
import com.myhome.ui.mine.SettingsScreen
import com.myhome.ui.mine.SwitchAccountScreen
import com.myhome.ui.mine.UpdateScreen
import com.myhome.ui.mine.VersionInfoScreen
import com.myhome.ui.system.CourseDetailScreen
import com.myhome.ui.system.CourseListScreen
import com.myhome.ui.system.EmailConfigEditScreen
import com.myhome.ui.system.EmailConfigListScreen
import com.myhome.ui.system.FamilyDetailScreen
import com.myhome.ui.system.FamilyListScreen
import com.myhome.ui.system.RoleListScreen
import com.myhome.ui.system.SmsConfigEditScreen
import com.myhome.ui.system.SmsConfigListScreen
import com.myhome.ui.system.SystemScreen
import com.myhome.ui.system.UserEditScreen
import com.myhome.ui.system.UserListScreen
import com.myhome.net.JwtUtil
import com.myhome.util.RoleUtil
import android.widget.Toast
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun authRepository(): AuthRepository
}

@Composable
fun RootNavGraph() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    val context = LocalContext.current
    val authRepo = remember(context) {
        EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java).authRepository()
    }

    var hasToken by remember { mutableStateOf<Boolean?>(null) }
    var isSystemAdmin by remember { mutableStateOf(false) }
    var isParent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        authRepo.tokenFlow.collect { token ->
            hasToken = token != null
            val roles = JwtUtil.extractRoles(token?.accessToken)
            isSystemAdmin = roles.contains("admin")
            isParent = RoleUtil.canManageFamily(roles)
        }
    }

    val route = currentDestination?.route
    val showBottomBar = route == Routes.HOME || route == Routes.EDUCATION ||
        route == Routes.MINE || route == Routes.SYSTEM

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController, currentDestination, isSystemAdmin = isSystemAdmin)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onOpenCodeLogin = { navController.navigate(Routes.CODE_LOGIN) },
                    onOpenResetPassword = { navController.navigate(Routes.RESET_PASSWORD) },
                )
            }
            composable(Routes.CODE_LOGIN) {
                CodeLoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.RESET_PASSWORD) {
                ResetPasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HOME) {
                ApplianceListScreen(
                    onOpenAppliance = { id -> navController.navigate(Routes.applianceDetail(id)) },
                    onCreateAppliance = { navController.navigate(Routes.applianceForm(null)) },
                )
            }
            composable(
                Routes.APPLIANCE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ApplianceDetailScreen(
                    applianceId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.applianceForm(it)) },
                )
            }
            composable(
                Routes.APPLIANCE_FORM,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                ApplianceFormScreen(
                    applianceId = id,
                    onBack = { navController.popBackStack() },
                )
            }

            // ---- 教育 ----
            composable(Routes.EDUCATION) {
                if (isParent) {
                    ParentEducationScreen(
                        onCreateTask = { navController.navigate(Routes.taskForm(null)) },
                        onOpenRedemptions = { navController.navigate(Routes.REDEMPTIONS) },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onOpenRewards = { navController.navigate(Routes.REWARDS) },
                        onOpenDeviceControl = { navController.navigate(Routes.DEVICES) },
                        onOpenChildSkill = { cid, name ->
                            navController.navigate(Routes.skillCenterChild(cid, name))
                        },
                        onOpenSelfSkill = { navController.navigate(Routes.SKILL_CENTER) },
                        onOpenSelfSession = { type, id ->
                            when (type) {
                                CourseSessionType.READING ->
                                    navController.navigate(Routes.readingSessionSelf(id))
                                CourseSessionType.LEARN ->
                                    navController.navigate(Routes.learnSessionSelf(id))
                                CourseSessionType.QUIZ ->
                                    navController.navigate(Routes.quizSessionSelf(id))
                            }
                        },
                        onOpenGrades = { navController.navigate(Routes.GRADES) },
                        onOpenStudyStats = { navController.navigate(Routes.STUDY_STATS) },
                        onOpenHabits = { navController.navigate(Routes.HABITS) },
                    )
                } else {
                    ChildEducationScreen(
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onOpenPoints = { navController.navigate(Routes.POINTS) },
                        onOpenRewards = { navController.navigate(Routes.REWARDS) },
                        onOpenSkillCenter = { navController.navigate(Routes.SKILL_CENTER) },
                        onOpenHabits = { navController.navigate(Routes.HABITS) },
                    )
                }
            }
            composable(Routes.SKILL_CENTER) {
                SkillCenterScreen(
                    mode = SkillCenterMode.Self,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.SKILL_CENTER_CHILD,
                arguments = listOf(
                    navArgument("childId") { type = NavType.StringType },
                    navArgument("childName") { type = NavType.StringType },
                ),
            ) { entry ->
                val childId = entry.arguments?.getString("childId").orEmpty()
                val childName = entry.arguments?.getString("childName").orEmpty()
                SkillCenterScreen(
                    mode = SkillCenterMode.Child(childId, childName),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.TASK_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                // 监听 ReadingSession 任务模式回写的 task_completed 事件，递增 refreshKey 触发重载
                val completedFlag = entry.savedStateHandle
                    .getStateFlow<Boolean?>("task_completed", null)
                    .collectAsStateWithLifecycle().value
                val refreshKey = completedFlag ?: false
                TaskDetailScreen(
                    taskId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.taskForm(it)) },
                    onOpenSession = { type, cid, tid ->
                        when (type) {
                            CourseSessionType.READING ->
                                navController.navigate(Routes.readingSessionTask(cid, tid))
                            CourseSessionType.LEARN ->
                                navController.navigate(Routes.learnSessionTask(cid, tid))
                            CourseSessionType.QUIZ ->
                                navController.navigate(Routes.quizSessionTask(cid, tid))
                        }
                    },
                    refreshKey = refreshKey,
                )
            }
            composable(
                Routes.READING_SESSION_TASK,
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType },
                ),
            ) { entry ->
                val cid = entry.arguments?.getString("courseId").orEmpty()
                val tid = entry.arguments?.getString("taskId").orEmpty()
                ReadingSessionScreen(
                    courseId = cid,
                    taskId = tid,
                    onBack = { navController.popBackStack() },
                    onTaskCompleted = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("task_completed", true)
                    },
                )
            }
            composable(
                Routes.LEARN_SESSION_TASK,
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType },
                ),
            ) { entry ->
                val cid = entry.arguments?.getString("courseId").orEmpty()
                val tid = entry.arguments?.getString("taskId").orEmpty()
                LearnSessionScreen(
                    courseId = cid,
                    taskId = tid,
                    onBack = { navController.popBackStack() },
                    onTaskCompleted = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("task_completed", true)
                    },
                )
            }
            composable(
                Routes.QUIZ_SESSION_TASK,
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType },
                ),
            ) { entry ->
                val cid = entry.arguments?.getString("courseId").orEmpty()
                val tid = entry.arguments?.getString("taskId").orEmpty()
                QuizSessionScreen(
                    courseId = cid,
                    taskId = tid,
                    onBack = { navController.popBackStack() },
                    onTaskCompleted = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("task_completed", true)
                    },
                )
            }
            composable(
                Routes.TASK_FORM,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                if (!isParent) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, "仅家长可操作", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                } else {
                    TaskFormScreen(
                        taskId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.POINTS) {
                PointsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REWARDS) {
                RewardListScreen(
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(Routes.rewardForm(null)) },
                    onOpenRedemptions = { navController.navigate(Routes.REDEMPTIONS) },
                )
            }
            composable(
                Routes.REWARD_FORM,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                if (!isParent) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, "仅家长可操作", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                } else {
                    RewardFormScreen(
                        rewardId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.REDEMPTIONS) {
                RedemptionListScreen(onBack = { navController.popBackStack() })
            }

            // ---- 我的 / 设置 ----
            composable(Routes.MINE) {
                MineScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenChangePhone = { navController.navigate(Routes.CHANGE_PHONE) },
                    onOpenChangeEmail = { navController.navigate(Routes.CHANGE_EMAIL) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUpdate = { navController.navigate(Routes.UPDATE) },
                    onOpenVersionInfo = { navController.navigate(Routes.VERSION_INFO) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenDevices = { navController.navigate(Routes.DEVICES) },
                    onOpenLocalDeviceControl = { navController.navigate(Routes.DEVICE_CONTROL) },
                    onOpenSwitchAccount = { navController.navigate(Routes.SWITCH_ACCOUNT) },
                    onOpenFamilyMembers = { navController.navigate(Routes.FAMILY_MEMBERS) },
                    onOpenChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                )
            }
            composable(Routes.FAMILY_MEMBERS) {
                FamilyMembersScreen(
                    onBack = { navController.popBackStack() },
                    onAddMember = { navController.navigate(Routes.MEMBER_FORM) },
                )
            }
            composable(Routes.MEMBER_FORM) {
                MemberFormScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SWITCH_ACCOUNT) {
                SwitchAccountScreen(
                    onBack = { navController.popBackStack() },
                    onAddNewAccount = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    },
                    onSwitched = {
                        navController.navigate(Routes.MINE) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.UPDATE) {
                UpdateScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.VERSION_INFO) {
                VersionInfoScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DEVICE_CONTROL) {
                DeviceControlScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSetup = { navController.navigate(Routes.DEVICE_OWNER_SETUP) },
                )
            }
            composable(Routes.DEVICE_OWNER_SETUP) {
                DeviceOwnerSetupScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DEVICES) {
                DeviceListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { id -> navController.navigate(Routes.deviceDetail(id)) },
                )
            }
            composable(
                Routes.DEVICE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                DeviceDetailScreen(
                    deviceId = id,
                    onBack = { navController.popBackStack() },
                    onOpenSetup = { navController.navigate(Routes.DEVICE_OWNER_SETUP) },
                )
            }
            composable(Routes.SYSTEM) {
                SystemScreen(
                    onOpenUsers = { navController.navigate(Routes.USER_LIST) },
                    onOpenFamilies = { navController.navigate(Routes.FAMILY_LIST) },
                    onOpenRoles = { navController.navigate(Routes.ROLE_LIST) },
                    onOpenSmsConfigs = { navController.navigate(Routes.SMS_CONFIG_LIST) },
                    onOpenEmailConfigs = { navController.navigate(Routes.EMAIL_CONFIG_LIST) },
                    onOpenCourses = { navController.navigate(Routes.COURSE_LIST) },
                )
            }
            composable(Routes.USER_LIST) {
                UserListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUserEdit = { id -> navController.navigate(Routes.userEdit(id)) },
                    onOpenUserCreate = { navController.navigate(Routes.userEdit(null)) },
                )
            }
            composable(Routes.FAMILY_LIST) {
                FamilyListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFamilyDetail = { id -> navController.navigate(Routes.familyDetail(id)) },
                )
            }
            composable(
                Routes.FAMILY_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                FamilyDetailScreen(
                    familyId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ROLE_LIST) {
                RoleListScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.USER_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                UserEditScreen(
                    userId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            // ---- 系统管理：短信/邮箱配置 ----
            composable(Routes.SMS_CONFIG_LIST) {
                SmsConfigListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEdit = { id -> navController.navigate(Routes.smsConfigEdit(id)) },
                )
            }
            composable(
                Routes.SMS_CONFIG_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                SmsConfigEditScreen(
                    configId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.EMAIL_CONFIG_LIST) {
                EmailConfigListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEdit = { id -> navController.navigate(Routes.emailConfigEdit(id)) },
                )
            }
            composable(
                Routes.EMAIL_CONFIG_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                EmailConfigEditScreen(
                    configId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            // ---- 系统管理：课程管理（admin only，只读 + 启停 + 体验） ----
            composable(Routes.COURSE_LIST) {
                CourseListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { id -> navController.navigate(Routes.courseDetail(id)) },
                    onOpenSession = { type, id ->
                        when (type) {
                            CourseSessionType.READING -> navController.navigate(Routes.readingSession(id))
                            CourseSessionType.LEARN -> navController.navigate(Routes.learnSession(id))
                            CourseSessionType.QUIZ -> navController.navigate(Routes.quizSession(id))
                        }
                    },
                )
            }
            composable(
                Routes.COURSE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                CourseDetailScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { type, cid ->
                        when (type) {
                            CourseSessionType.READING -> navController.navigate(Routes.readingSession(cid))
                            CourseSessionType.LEARN -> navController.navigate(Routes.learnSession(cid))
                            CourseSessionType.QUIZ -> navController.navigate(Routes.quizSession(cid))
                        }
                    },
                )
            }
            composable(
                Routes.READING_SESSION,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReadingSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.LEARN_SESSION,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LearnSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.QUIZ_SESSION,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                QuizSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            // ---- 家长自学(v0.16.0):同 session 流程,无积分结算 ----
            composable(
                Routes.READING_SESSION_SELF,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReadingSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                    selfStudy = true,
                )
            }
            composable(
                Routes.LEARN_SESSION_SELF,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LearnSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                    selfStudy = true,
                )
            }
            composable(
                Routes.QUIZ_SESSION_SELF,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                QuizSessionScreen(
                    courseId = id,
                    onBack = { navController.popBackStack() },
                    selfStudy = true,
                )
            }
            // ---- Phase 4（v0.17.0）：每日打卡 / 学科成绩 / 学习时长 ----
            composable(Routes.HABITS) {
                HabitListScreen(
                    isParent = isParent,
                    onBack = { navController.popBackStack() },
                    onOpenForm = { id -> navController.navigate(Routes.habitForm(id)) },
                )
            }
            composable(
                Routes.HABIT_FORM,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                if (!isParent) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, "仅家长可操作", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                } else {
                    HabitFormScreen(
                        habitId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.GRADES) {
                GradeListScreen(
                    isParent = isParent,
                    onBack = { navController.popBackStack() },
                    onOpenForm = { id -> navController.navigate(Routes.gradeForm(id)) },
                )
            }
            composable(
                Routes.GRADE_FORM,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }),
            ) { entry ->
                val raw = entry.arguments?.getString("id").orEmpty()
                val id = raw.takeIf { it.isNotBlank() }
                if (!isParent) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, "仅家长可操作", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                } else {
                    GradeFormScreen(
                        gradeId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.STUDY_STATS) {
                StudyStatsScreen(
                    isParent = isParent,
                    onBack = { navController.popBackStack() },
                )
            }
            // ---- 我的：改绑手机/邮箱/改密码 ----
            composable(Routes.CHANGE_PHONE) {
                ChangePhoneScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHANGE_EMAIL) {
                ChangeEmailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHANGE_PASSWORD) {
                ChangePasswordScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    LaunchedEffect(hasToken) {
        if (hasToken == false) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}
