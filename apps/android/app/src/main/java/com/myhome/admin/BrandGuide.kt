package com.myhome.admin

/**
 * 设备品牌识别 + 品牌专属的 Device Owner 设置指引步骤。
 *
 * 注册时记录的 `Build.MANUFACTURER` 字符串值是品牌判定的源头（小米="xiaomi"、华为="Huawei"、
 * 荣耀="HONOR"、三星="samsung"）。不同品牌的「开发者选项」开启路径、无线调试位置、工厂重置入口
 * 都不同——给用户看错路径会浪费大量时间。
 *
 * 当前覆盖：
 * - XIAOMI（xiaomi / redmi / poco，含 MIUI 与 HyperOS）：详细步骤
 * - GENERIC：通用 Android 11+ 步骤（其他品牌 fallback）
 *
 * 后续扩展只需在 BrandGuide.setupSteps 加对应分支即可。
 */
enum class DeviceBrand {
    XIAOMI,
    HUAWEI,
    HONOR,
    OPPO,
    VIVO,
    SAMSUNG,
    GENERIC,
}

object BrandGuide {
    /**
     * 根据 Build.MANUFACTURER（注册时已上报到 device.manufacturer）识别品牌家族。
     * 大小写不敏感。未识别返回 GENERIC。
     */
    fun brandFamily(manufacturer: String?): DeviceBrand {
        val m = manufacturer?.trim()?.lowercase() ?: return DeviceBrand.GENERIC
        return when {
            m == "xiaomi" || m == "redmi" || m == "poco" -> DeviceBrand.XIAOMI
            m == "huawei" -> DeviceBrand.HUAWEI
            m == "honor" -> DeviceBrand.HONOR
            m.startsWith("oppo") || m == "oneplus" || m == "realme" -> DeviceBrand.OPPO
            m == "vivo" || m == "iqoo" -> DeviceBrand.VIVO
            m == "samsung" -> DeviceBrand.SAMSUNG
            else -> DeviceBrand.GENERIC
        }
    }

    /**
     * 返回该品牌的 Device Owner 设置步骤列表。
     *
     * 步骤要尽量准确——错误路径（例如小米说成"关于平板"）会让用户找不到入口。
     * 当前小米用详细版（用户主对接），其他品牌暂用 GENERIC fallback（结构已分好，后续扩展只改这里）。
     */
    fun setupSteps(brand: DeviceBrand): List<String> = when (brand) {
        DeviceBrand.XIAOMI -> listOf(
            "工厂重置平板（必须，Device Owner 要求设备无账户）：设置 → 我的设备 → 恢复出厂设置 / 清除所有数据",
            "重置后初始引导中跳过「登录小米账号」和「登录 Google 账户」（都点「跳过」）",
            "平板浏览器打开 http://115.120.213.13:8090/download 下载我家 App 装上",
            "App 登录孩子账号（设备自动注册到后端，远程状态显示 DO=否）",
            "启用「开发者选项」：设置 → 我的设备 → 全部参数与信息 → 连续点 7 次「MIUI 版本」或「OS 版本」（HyperOS）",
            "在「开发者选项」中开启「无线调试」：设置 → 更多设置 → 开发者选项 → 无线调试（Android 11+）",
            "手机和平板连同一个 WiFi；建议关闭「WLAN 助理」避免小米自动切换到移动数据",
            "平板「无线调试」页面点击「使用配对码配对设备」，记下 6 位配对码和端口号",
        )
        // 华为 / 荣耀由于是鸿蒙系统（HarmonyOS NEXT），整个 ADB / Device Owner 路径走不通——
        // 在 UI 上会显示「鸿蒙设备暂不支持远程激活」，不会进到 SetupInstructionsCard。
        // 但 HarmonyOS 4.x（基于 AOSP）理论上可走通用步骤，这里先按 GENERIC 处理。
        DeviceBrand.HUAWEI,
        DeviceBrand.HONOR,
        DeviceBrand.OPPO,
        DeviceBrand.VIVO,
        DeviceBrand.SAMSUNG,
        DeviceBrand.GENERIC -> listOf(
            "在平板上：工厂重置（必须，Device Owner 要求设备无账户）",
            "在平板上：重置后跳过 Google 账户设置",
            "在平板上：浏览器打开 http://115.120.213.13:8090/download 下载我家 App 装上",
            "在平板上：App 登录孩子账号（设备自动注册到后端，此时远程状态 DO 否）",
            "在平板上：启用「开发者选项」：设置 → 关于平板 → 连续点 7 次「版本号」",
            "在平板上：在「开发者选项」中开启「无线调试」（Android 11+）",
            "手机和平板连同一个 WiFi",
            "平板「无线调试」页面点击「配对设备」生成配对码和端口",
        )
    }

    /** 在卡片标题处显示，方便用户确认指导适用自己的设备。 */
    fun cardTitle(brand: DeviceBrand): String = when (brand) {
        DeviceBrand.XIAOMI -> "首次设置 · 小米 / 红米 / POCO（MIUI / HyperOS）"
        DeviceBrand.HUAWEI -> "首次设置 · 华为（EMUI / 鸿蒙兼容层）"
        DeviceBrand.HONOR -> "首次设置 · 荣耀（MagicOS）"
        DeviceBrand.OPPO -> "首次设置 · OPPO / 一加 / 真我（ColorOS / OxygenOS / realme UI）"
        DeviceBrand.VIVO -> "首次设置 · vivo / iQOO（OriginOS / Funtouch OS）"
        DeviceBrand.SAMSUNG -> "首次设置 · 三星（One UI）"
        DeviceBrand.GENERIC -> "首次设置 · 通用 Android"
    }
}
