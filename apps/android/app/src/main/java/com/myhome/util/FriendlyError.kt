package com.myhome.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * 将网络异常转成中文友好提示，并解析后端 FastAPI 返回的 `{"detail": "..."}` 错误体。
 *
 * FastAPI 的 422 detail 是数组（Pydantic validation error list）：
 * `{"detail":[{"type":"string_too_short","loc":["body","username"],"msg":"String should have at least 3 characters",...}]}`
 * 这里映射成中文可读字段提示。其他状态码的 detail 通常是 snake_case code，走 errorMessageFromDetail。
 */
fun friendlyError(e: Throwable, json: Json = Json): String = when (e) {
    is HttpException -> {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val detailElem = runCatching {
            raw?.let { json.parseToJsonElement(it).jsonObject["detail"] }
        }.getOrNull()
        when {
            detailElem == null -> e.message() ?: "请求失败，请重试"
            detailElem is JsonArray -> parseValidationErrors(detailElem.jsonArray)
            detailElem is JsonObject -> detailElem.jsonObject["msg"]?.jsonPrimitive?.contentOrNull ?: "请求参数有误"
            else -> {
                val s = detailElem.jsonPrimitive.contentOrNull
                if (s != null) errorMessageFromDetail(s) else (e.message() ?: "请求失败，请重试")
            }
        }
    }
    else -> e.message ?: "网络异常，请稍后重试"
}

/**
 * 解析 FastAPI 422 数组 detail，取第一个错误映射成中文。
 * 字段名 → 中文：username→用户名，password→密码，display_name→昵称，family_name→家庭名称。
 */
private fun parseValidationErrors(arr: JsonArray): String {
    val first = arr.firstOrNull()?.let { it.jsonObject } ?: return "请求参数有误"
    val field = first["loc"]?.jsonArray?.lastOrNull()?.jsonPrimitive?.contentOrNull ?: "参数"
    val fieldCn = when (field) {
        "username" -> "用户名"
        "password" -> "密码"
        "display_name" -> "昵称"
        "family_name" -> "家庭名称"
        else -> field
    }
    val type = first["type"]?.jsonPrimitive?.contentOrNull
    val msg = when (type) {
        "string_too_short" -> {
            val min = first["ctx"]?.jsonObject?.get("min_length")?.jsonPrimitive?.contentOrNull
            if (min != null) "${fieldCn}至少 ${min} 个字符" else "${fieldCn}太短"
        }
        "string_too_long" -> "${fieldCn}超出长度限制"
        "missing" -> "缺少字段：${fieldCn}"
        "value_error" -> {
            // msg 形如 "Value error, family_id_required"，提取 snake_case code 走翻译
            val rawMsg = first["msg"]?.jsonPrimitive?.contentOrNull ?: ""
            val code = rawMsg.removePrefix("Value error, ").trim()
            if (code != rawMsg && code.matches(Regex("[a-z_0-9]+"))) {
                errorMessageFromDetail(code)
            } else {
                rawMsg.ifBlank { "${fieldCn}格式有误" }
            }
        }
        else -> first["msg"]?.jsonPrimitive?.contentOrNull ?: "${fieldCn}有误"
    }
    return msg ?: "请求参数有误"
}

fun errorMessageFromDetail(detail: String): String = when (detail) {
    "invalid_credentials" -> "用户名或密码错误"
    "invalid_current_password" -> "当前密码不正确"
    "use_change_password_endpoint" -> "请使用「修改密码」改自己密码"
    "invalid_token" -> "登录已失效，请重新登录"
    "invalid_token_type" -> "登录已失效，请重新登录"
    "user_not_found_or_inactive" -> "账号不存在或已停用"
    "user_disabled" -> "账号已停用，请联系家长"
    "username_taken" -> "用户名已被占用"
    "registration_disabled" -> "注册已关闭，请联系管理员"
    "parent_only" -> "需要家长或家庭管理员权限"
    "family_admin_only" -> "仅家庭管理员可操作"
    "no_family" -> "尚未加入家庭"
    "family_not_found" -> "家庭不存在"
    "family_id_required" -> "请选择所属家庭"
    "family_id_not_allowed" -> "管理员不属于任何家庭"
    "role_group_conflict" -> "同组角色只能选择一个"
    "member_not_found" -> "成员不存在"
    "cannot_delete_self" -> "不能删除自己"
    "cannot_modify_self_role" -> "不能修改自己的角色"
    "cannot_demote_last_family_admin" -> "至少保留一位家庭管理员"
    "user_in_use" -> "用户有依赖记录无法删除（如创建了短信/邮箱配置），请先停用"
    "appliance_not_found" -> "电器不存在"
    // 教育：学科
    "subject_not_found" -> "学科不存在"
    "subject_name_taken" -> "学科名称已存在"
    // 教育：任务
    "task_not_found" -> "任务不存在"
    "task_inactive" -> "任务已停用"
    "task_already_completed" -> "已完成过该任务"
    "task_already_completed_today" -> "今天已完成该任务"
    "task_not_assigned_to_you" -> "该任务没有指派给你"
    "task_not_started_yet" -> "任务还未开始"
    "task_expired" -> "任务已过期"
    "task_not_available_today" -> "今天不是该任务的执行日"
    "task_outside_time_window" -> "当前不在任务可完成时间段内"
    "assignee_not_found" -> "指定的孩子不存在"
    "record_not_found" -> "完成记录不存在"
    // 教育：积分
    "insufficient_points" -> "积分不足"
    // 教育：奖励 + 兑换
    "reward_not_found" -> "奖励不存在"
    "reward_out_of_stock" -> "奖励已兑换完"
    "redemption_not_found" -> "兑换记录不存在"
    "redemption_status_invalid" -> "兑换状态不允许该操作"
    // 验证码
    "code_send_failed" -> "验证码发送失败"
    "invalid_code" -> "验证码错误或已过期"
    "verify_token_invalid" -> "验证已过期，请重新获取"
    "phone_mismatch" -> "手机号与验证码不匹配"
    "email_mismatch" -> "邮箱与验证码不匹配"
    "phone_in_use" -> "该手机号已被使用"
    "email_in_use" -> "该邮箱已被使用"
    "rate_limited" -> "发送过于频繁，请稍后再试"
    "no_active_provider" -> "系统未配置发码渠道"
    "provider_already_exists" -> "该服务商配置已存在"
    "config_not_found" -> "配置不存在"
    "cannot_delete_active_config" -> "无法删除已激活的配置，请先停用"
    "user_not_found" -> "账号不存在"
    "family_not_empty" -> "家庭还有成员，请先移出或删除成员后再删除家庭"
    "family_in_use" -> "家庭存在关联数据，无法删除"
    // 课程管理（admin，只读 + 启停 + 体验）
    "course_not_found" -> "课程不存在"
    "course_inactive" -> "课程已停用，无法体验"
    "child_not_found" -> "孩子账号不存在"
    "no_child_available" -> "暂无可用的孩子账号"
    "child_no_family" -> "孩子账号未关联家庭"
    // 单词发音评测（讯飞 ISE）
    "word_not_found" -> "单词不存在"
    "word_not_linked" -> "该单词未关联词库"
    // 家长自学教材（v0.16.1）
    "textbook_not_found" -> "该教材暂无可学课程"
    "textbook_already_added" -> "已添加过该教材"
    "empty_ref_text" -> "参考文本为空"
    "ise_not_configured" -> "朗读评分未启用（后端未配置讯飞 ISE）"
    "ise_no_audio" -> "录音数据为空"
    "ise_no_ref_text" -> "参考文本为空"
    "ise_timeout" -> "评分超时，请重试"
    "ise_ws_status" -> "评分服务连接失败"
    "ise_ws_error" -> "评分服务异常"
    "ise_invalid_response" -> "评分响应格式错误"
    "ise_no_xml" -> "评分响应缺少数据"
    "ise_invalid_xml_encoding" -> "评分结果解析失败"
    "ise_score_not_found" -> "评分结果解析失败"
    // 能力模型（v0.13.0）
    "invalid_state" -> "状态过滤参数无效"
    "invalid_mode" -> "选词模式无效"
    "invalid_textbook_filter" -> "教材过滤参数无效"
    // TTS（Edge TTS 代理）
    "tts_empty_text" -> "合成文本为空"
    "tts_timeout" -> "语音合成超时，请重试"
    "tts_no_audio" -> "语音合成返回空"
    "tts_synth_failed" -> "语音合成服务异常"
    // ADB 一键激活
    "adb_init_failed" -> "ADB 初始化失败"
    "adb_pair_failed" -> "ADB 配对失败"
    "adb_pair_rejected" -> "配对码或端口被拒绝"
    "adb_unauthorized" -> "已配对但未授权连接"
    "adb_connect_failed" -> "ADB 连接失败"
    "adb_shell_failed" -> "ADB 命令执行失败"
    "adb_invalid_input" -> "IP:端口或配对码格式有误"
    "dpm_set_failed" -> "Device Owner 激活失败"
    else -> if (detail.startsWith("ise_code_")) "评分服务异常（${detail}）" else detail
}
