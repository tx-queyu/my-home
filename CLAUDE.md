# my-home — 项目规范

家庭级应用，含「家居」与「教育」两大模块。技术栈参考 `/Users/terry/workspace/union_agent_2`，
但去掉企业级组件（k8s / Prometheus / Langfuse / 多服务）。

## 安全约束（重要）

### 敏感信息保护
- **不得**将任何敏感信息（密码、JWT Secret、连接串、私钥等）提交到代码仓库
- 所有敏感配置通过环境变量（前缀 `MYHOME_`）或 `.env`（已 gitignore）管理
- `.env.example` 中只能使用占位符值
- 提交前用 `git diff --cached` 自查

### 多家庭隔离（核心安全约束）
- **所有**业务查询必须按 `current_user.family_id` 过滤
- 跨家庭访问返回 **404 而非 403**（避免账号枚举/信息泄露，见 `app/api/appliances.py:_get_owned`）
- Token 携带 `family_id` claim；所有 router 直接从 `current_user.family_id` 取，不信任请求体里的 family_id

### Token 存储
- Android 端 token 存 **EncryptedSharedPreferences**（不用明文 DataStore）——家庭 App 有 child 账号，root 设备/备份提取时不希望 token 裸露
- 见 `apps/android/app/src/main/java/com/myhome/net/EncryptedTokenStorage.kt`

## 开发规范

### 技术栈
- **后端**：Python 3.11 + FastAPI + SQLAlchemy 2.0 async + asyncpg + PostgreSQL 16
- **认证**：python-jose (JWT HS256) + passlib[bcrypt]
- **App**：Android 原生（Kotlin 2.0.21 + Jetpack Compose + Hilt + Retrofit + kotlinx-serialization）
- **Web Landing**：VitePress 1.x + nginx（容器化），反代 backend `/docs` `/openapi.json` `/health` `/api/`
- **本地运行**：Docker Compose（仅 postgres）；backend 用 uvicorn 本地跑；landing 用 npm dev
- **包结构**：Monorepo（`services/backend/` + `apps/android/` + `apps/landing/` + `deploy/`）

### 代码风格

#### Python（后端）
- 类型注解必须
- 异步优先（async/await），SQLAlchemy 用 `async_sessionmaker` + `expire_on_commit=False`
- **模型用现代 `Mapped[]` / `mapped_column` 风格**（**不**用旧版 `Column()`；参考工程的 Column 风格是历史遗留）
- UUID 主键 + `server_default=func.now()` 时间戳（**不**用 Python-side default）
- Router prefix 统一 `/api/<module>`
- Pydantic schema 字段类型必须与 ORM 字段类型对齐：ORM 是 `UUID` 就用 `UUID`（**不要**写 `str` 然后期望自动转换，会触发 `ResponseValidationError`）

#### Kotlin（Android）
- DTO 全部 `@Serializable` + `@SerialName("snake_case")`，对应后端 snake_case JSON
- `Json { ignoreUnknownKeys=true; coerceInputValues=true; explicitNulls=false; encodeDefaults=true }`（容忍 schema 漂移）
- ViewModel 用单 `MutableStateFlow<UiState>` + `_ui.update { it.copy(...) }` + `runCatching` + `friendlyError(e)`
- Repository 通过 `@Inject constructor` 直接构造注入，不需要 `@Binds`（除非有多实现）
- 网络：单 Retrofit（单后端），`AuthInterceptor` 加 `Authorization: Bearer` 头

### 用户文案不暴露实现细节
- 页面/按钮文案只写「做什么」，不写「怎么做」（如「新建电器」而非「POST /api/appliances」）
- 错误提示用中文友好语（如「用户名或密码错误」），不暴露 detail code / SQL / 异常类名
- API 响应只返回前端需要的字段

### 错误 detail code 命名约定
后端 `HTTPException(detail="...")` 的 detail 字符串是机器可读的 snake_case code，前端 `errorMessageFromDetail()` 映射成中文。**新增 detail code 必须同步加到 `util/FriendlyError.kt` 的 `errorMessageFromDetail()`**，否则用户看到 raw code。

### 测试规范（提交前必须执行）

每次新增/修改代码，commit 前必须完成：

1. **后端冒烟**：
   - 本地启动 postgres + uvicorn（命令见下）
   - 用 curl 对改动接口做 create/list/get/update/delete 端到端
   - 验证返回码 + DB 实际数据落库（如改了 model 字段，DB schema 必须同步——`create_all` 在 dev 自动跑，但已有表加列时不会自动 ALTER，需手动 `docker exec -it myhome-postgres psql -U myhome -c "ALTER TABLE ..."`）

2. **Android 编译**：
   - `cd apps/android && ./gradlew :app:compileDebugKotlin` 必须 0 error
   - 涉及 UI 改动：`./gradlew :app:assembleDebug` 成功 + 真机/模拟器手动跑端到端
   - 不能只靠编译通过就声明完成——必须实际操作 UI 验证交互

**反模式（明确禁止）**：
- ❌ 只改后端不 curl 验证
- ❌ 只改 Kotlin 不跑 assembleDebug
- ❌ 改了 model 字段但 DB schema 不同步迁移
- ❌ 新增 detail code 但 `errorMessageFromDetail` 没更新
- ❌ 本地不冒烟直接说"完成"

## 架构约束

### 后端
- **单体 FastAPI**：所有 router 装到一个 app 实例，**不**拆 manager/gateway/hub 多服务（家庭场景不需要）
- **建表**：dev 用 `Base.metadata.create_all`（lifespan 启动时跑）；Phase 5 切 Alembic 迁移
- **lifespan**：`@asynccontextmanager` 内 `engine.begin()` → `run_sync(Base.metadata.create_all)` → `yield` → `engine.dispose()`
- **认证依赖**：`get_current_user`（基础）+ `require_parent`（家长操作）+ `family_id` 过滤（所有业务查询）
- **CORS**：dev `*` 通配（无 credentials），生产显式白名单（带 credentials）

### Android
- **单 Activity**：`MainActivity` + `RootNavGraph`（一个 Scaffold + 条件 BottomBar + NavHost）
- **单 Retrofit**：因为单后端，不需要参考工程的 manager/gateway 双 client
- **BuildConfig.BACKEND_URL**：debug 走 `http://10.0.2.2:8000/`（模拟器访问宿主机）；真机调试改 `app/build.gradle.kts` 或 `network_security_config.xml` 加本地 IP
- **明文 HTTP**：`network_security_config.xml` 允许 10.0.2.2 / localhost / 127.0.0.1 明文（debug only，生产必须 HTTPS）
- **Hilt**：`@HiltAndroidApp` Application + `@AndroidEntryPoint` Activity + `SingletonComponent` modules
- **Navigation**：String-based routes，`Routes.kt` 集中管理；可选参数用 `nullable=true defaultValue=""` pattern（如 `appliance_form?id={id}`）

### 路由层级（FastAPI）
所有 router 都挂 `prefix="/api/<module>"`，**不要**加额外 `/api/v1` 等版本前缀（家庭 App 不需要 API 版本协商）。

### Web Landing
- **技术栈**：VitePress 1.x（单工程，npm 不用 pnpm）+ nginx 容器（多阶段构建）
- **目录**：`apps/landing/`（`content/` markdown + `.vitepress/theme/` Vue 组件 + `Dockerfile` + `nginx.conf`）
- **端口**：8090:80（避开 backend 8000、pg 5432、宿主机 80）
- **nginx 反代**：`/docs` `/openapi.json` `/health` `/api/` → `http://backend:8000/...`（同 compose 网络走 service 名）
- **SPA 路由坑**：VitePress `cleanUrls: true` 生成 `download.html` 但 URL 是 `/download`，nginx 必须 `try_files $uri $uri.html $uri/` 才能命中
- **CTA 跨站跳转**：`LandingCTA` 的 `/docs` 链接必须加 `target="_top"`，否则 VitePress SPA router 拦截会 404（`/docs` 不在 VitePress 站点内，是反代到 backend 的外部入口）
- **Swagger 反代完整链路**：`/docs` 是 Swagger UI HTML，但页面内部还会 fetch `/openapi.json` 拿 schema，两者都必须反代，缺一不可

## 本地运行

### 后端
```bash
cd /Users/terry/workspace/my-home/deploy && docker compose up -d postgres   # 端口 5433
cd /Users/terry/workspace/my-home/services/backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --port 8000
# health: http://localhost:8000/health
```

**端口说明**：postgres 用 5433 而非 5432（宿主机 5432 被 union_agent_2 的 `ua-test-pg` 容器占用）。改端口见 `deploy/docker-compose.yml` + `services/backend/.env`。

### Android
```bash
cd /Users/terry/workspace/my-home/apps/android
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# 安装到模拟器：./gradlew :app:installDebug
# 启动：~/Library/Android/sdk/platform-tools/adb shell am start -n com.myhome.debug/com.myhome.MainActivity
```

### Web Landing
```bash
cd /Users/terry/workspace/my-home/apps/landing
npm install                              # 国内加速：npm config set registry https://registry.npmmirror.com
npm run dev                              # http://localhost:5173
npm run build                            # 产物到 .vitepress/dist/
npm run preview                           # http://localhost:4173 看构建产物
# 容器化构建：cd ../.. && docker compose -f deploy/docker-compose.prod.yml up -d --build landing
```

## 已知 Gotchas（踩过的坑）

### 后端
- **Pydantic + SQLAlchemy UUID 字段**：`from_attributes=True` 的 schema 字段类型必须用 `UUID` 而非 `str`，否则 `ResponseValidationError`。Pydantic v2 不会自动 stringify UUID。
- **`greenlet` 依赖**：`requirements.txt` 必须含 `greenlet>=3.0.0` 或用 `sqlalchemy[asyncio]` extra，否则 async SQLAlchemy 启动报 `No module named 'greenlet'`。
- **`bcrypt<4.1.0`**：passlib 1.7.4 与 bcrypt 4.1+ 不兼容（hash 时报 `$2b$` prefix error），必须 pin `<4.1.0`。
- **autoflush 引发难捕获的 IntegrityError**：`db.add(obj)` 后再做 `SELECT` 会触发 autoflush 把 `obj` INSERT 到 DB。如果 `obj` 违反唯一约束，IntegrityError 在 SELECT 时抛出，不在 `try: await db.commit(); except IntegrityError` 块里——表现为 500 而非业务 409。见 `app/api/tasks.py:complete_task`：先 `SELECT` 查重 → 取/建 account → 再 `db.add(record)`。Phase 4 写「同一天不可重复打卡」时要照搬这个顺序。

### Android
- **`dagger.hilt.android.EntryPointAccessors`**（不是 `dagger.hilt.EntryPointAccessors`）：参考工程写过对的，照抄时容易写错包路径，编译报 `Unresolved reference`。
- **`Icons.Filled.ArrowBack` 已弃用**：用 `Icons.AutoMirrored.Filled.ArrowBack`（RTL 适配）。Phase 3.5 已全量替换。
- **`Modifier.menuAnchor()` 已弃用**：用带 `MenuAnchorType` 参数的重载，见 `ApplianceFormScreen.kt` / `TaskFormScreen.kt` 待修（Phase 3.5 仍未修，编译有警告但不影响功能）。
- **Compose BOM 2024.09.03 + AGP 8.7.0 + Kotlin 2.0.21**：组合工作正常，不要随便升 Kotlin 到 2.1+（hilt 编译器可能跟不上不及时）。

### Web Landing
- **VitePress `cleanUrls: true` + nginx**：`/download` 这种无后缀 URL 必须靠 `try_files $uri $uri.html $uri/` 命中 `download.html`，少了 `$uri.html` 会 fallback 到 `index.html` 显示首页而非下载页。
- **npm 国内构建慢**：Dockerfile 设 `ENV npm_config_registry=https://registry.npmmirror.com` 加速 install。
- **Docker Hub 国内拉镜像**：Dockerfile 基础镜像走 `docker.m.daocloud.io/library/{node,nginx}`（与 backend Dockerfile 一致用国内源）。

## 当前实现进度

### Phase 1（后端骨架 + 认证 + 家居 CRUD）— ✅ 已完成
- `services/backend/app/{core,models,schemas,api}/`
- 模型：`Family`、`User`（role: parent/child）、`Appliance`
- 路由：`/api/auth/{register,login,refresh,me}`、`/api/families/{me,members}`、`/api/appliances` CRUD
- 全部 curl 验证通过

### Phase 2（Android 骨架 + 登录 + 家居页面）— ✅ 已完成
- `apps/android/app/src/main/java/com/myhome/`
- 屏幕：`LoginScreen`（含注册切换）、`ApplianceListScreen`、`ApplianceDetailScreen`、`ApplianceFormScreen`
- APK 构建成功（`app-debug.apk`）
- 手动 e2e 待真机/模拟器连接后跑

### Web Landing（首页 + 下载页）— ✅ 已完成
- `apps/landing/`（VitePress + nginx 容器）
- 页面：首页（Hero + 4 Features + CTA「查看 API 文档」）、`/download` 下载页（3 平台敬请期待）
- 加入 `deploy/docker-compose.prod.yml` 的 `landing` 服务（端口 8090:80，depends_on backend healthy）
- nginx 反代 `/docs` `/openapi.json` `/health` `/api/` 到 `backend:8000`

### Phase 3（教育模块：任务/积分/奖励）— ✅ 已完成
> ⚠️ v0.10.0 (Phase 3.6) 已将 `Subject` 重构为系统预置 `Course`，本节描述的是 Phase 3 当时的实现（家庭级 Subject + Task.subject_id）。当前代码以 Phase 3.6 为准。
- **后端**：`services/backend/app/{models,schemas,api}/{subject,task,point,reward}.py` + 4 个 router 装到 `main.py`
  - 模型：`Subject`（家庭级 name 唯一）、`Task` + `TaskRecord`（`ux_task_records_task_user` 唯一索引防重复完成）、`PointAccount` + `PointTransaction`、`Reward` + `Redemption`
  - 路由：`/api/subjects` CRUD、`/api/tasks` CRUD + `/complete` + `/records`、`/api/points/{me,transactions}`、`/api/rewards` CRUD + `/api/redemptions` 兑换 + 状态机
  - 关键设计：
    - 完成 task 自动加积分（`PointAccount.balance += points` + `PointTransaction(source=task)`），家长 DELETE record 退积分
    - 兑换原子扣积分（`UPDATE point_accounts SET balance=balance-cost WHERE user_id=? AND balance>=cost`，rowcount=0 → 409 insufficient_points），并发安全
    - 兑换拒绝退还积分（`source=adjustment`），状态机 `pending → fulfilled | rejected`
    - `PointAccount` 自动建：`auth.register` + `families.create_member` 都在 commit 前 `db.add(PointAccount(user_id=..., balance=0))`；老用户首次访问 `/api/points/me` 时 lazy 建
  - 本地 + 生产 curl 全路径验证通过（register → create subject/task → complete → balance 0→10 → redeem → balance 10→0 → reject → balance 0→10）
- **Android**：`apps/android/app/src/main/java/com/myhome/ui/education/`
  - 屏幕：`TaskListScreen`（教育首页：积分卡片 + 任务列表，已完成标记 ✓）+ `TaskDetailScreen` + `TaskFormScreen`（学科下拉）+ `PointsScreen`（流水）+ `RewardListScreen` + `RewardFormScreen` + `RedemptionListScreen`（家长 fulfill/reject）
  - 底部 tab 多了「教育」（`Icons.Filled.School`）
  - Repository + DTO + `FriendlyError.kt` 新增 10 个 detail code 翻译
  - `./gradlew :app:compileDebugKotlin` + `assembleDebug` + `assembleStaging` 全部成功
- **部署**：prod backend 容器重建、staging APK 重新构建并 cp 到 landing public、landing 容器重建；`/myhome.apk` 18.6MB 下载正常
- 手动 e2e 待真机跑（注册新家庭 → 学科 → 任务 → 孩子完成 → 积分 → 兑换 → 家长拒绝 → 积分退还）

### Phase 3.5（UI 美化 + 「我的」/设置模块 + 检查更新）— ✅ 已完成
- **主题改造**：`apps/android/app/src/main/java/com/myhome/ui/theme/{Color,Type,MyHomeTheme}.kt`
  - 8 个语义 token × light/dark（照搬 union_agent_2：bg/surface/surfaceSubtle/text/muted/border/accent/error）
  - `AppTypography`：displayLarge 32sp Bold / headlineMedium 20sp SemiBold / titleMedium 16sp SemiBold / bodyLarge 16sp·24sp / bodyMedium 14sp·20sp / labelLarge 14sp Medium / labelSmall 11sp
  - `primary` 由旧紫调 `0xFF5B6FDB` 切到蓝 `0xFF2563EB`
- **共享组件**：`apps/android/app/src/main/java/com/myhome/ui/components/{SettingsCommons,StateComponents}.kt`
  - `SettingsScaffold(title, onBack?, actions?, content)` — TopAppBar titleMedium+SemiBold + 可滚动 content padding 16h/8v spacedBy(12dp)
  - `SettingsCard(content)` — `Surface(RoundedCornerShape(20.dp), color=surface)` 无 elevation
  - `SettingsRow(title, subtitle?, onClick?, leading?, trailing?, showDivider, titleColor)` — 64dp minHeight + 0.5dp outlineVariant 内缩分割线（start=20dp）+ chevron 自动加（onClick!=null 且无 trailing）
  - `SettingsSectionLabel(text)` — labelMedium + start=8dp top=4dp
  - `EmptyState`/`ErrorState` 加 48dp 图标（Info/Warning）+ 卡片包裹；`LoadingState` 不变
- **「我的」模块**：`apps/android/app/src/main/java/com/myhome/ui/mine/`
  - `MineScreen`：80dp 圆形 primary 色头像（首字母）+ 用户名 + 角色 pill（家长/孩子）+ 家庭名 + 齿轮入口
  - `MineViewModel` + `MineUiState`：`authRepo.me()` + `familyRepo.getMyFamily()` 拉取
  - `SettingsScreen`：3 Card（账号信息 / 应用 / 退出登录）；「检查更新」trailing 显示 `v${VERSION_NAME}`，「退出登录」红色
  - `UpdateScreen` + `UpdateViewModel`：状态机 `checking | error | updateAvailable | upToDate`，`updateAvailable` 时显示「前往下载」按钮 → `Intent(ACTION_VIEW)` 打开浏览器到 `${BACKEND_URL}${apkUrl}`
  - `AboutScreen`：app 名 + 版本 + 描述 + 技术栈 + 版权
  - `VersionInfoScreen` + `VersionInfoViewModel`：当前版本 + 服务器最新版本 + 发布日期 + APK 路径 + 版本说明
- **检查更新机制**（静态 JSON，不走后端 API）：
  - `apps/landing/public/version.json`：`{version, apk_url, description, release_date}` — nginx 直接 serve
  - `apps/landing/.vitepress/theme/components/DownloadCard.vue` 在 `onMounted` 时 `fetch('/version.json')`，动态渲染版本号/发布日期/版本描述到下载页（fetch 失败时 fallback 到本地常量 `FALLBACK_VERSION="0.1.0"`）
  - **坑**：landing 容器构建时 `COPY public/ /usr/share/nginx/html/`，改 version.json 后必须重建容器，单纯 rsync 到 ECS 文件系统不会生效（nginx 读取的是容器内的 baked-in 副本）
  - `repo/VersionRepository.kt`：注入 `OkHttpClient` + `Json`，fetch `${BACKEND_URL}version.json`，解析 `VersionInfoDto`
  - `repo/VersionUtil.kt`：`isVersionNewer(latest, current)` — 点分数字逐段比（0.1.0 < 0.2.0 < 1.0.0）
  - `net/dto/VersionInfoDto.kt`：`@Serializable` + `@SerialName` 映射 snake_case
  - debug 模式 `BACKEND_URL=http://10.0.2.2:8000/`，本地 backend 无 version.json → 网络错误 → UpdateScreen 显示「暂时无法检查更新」+ 重试按钮（预期行为）
- **底部导航**：`apps/android/app/src/main/java/com/myhome/ui/nav/BottomBar.kt`
  - 加第 3 个 tab「我的」（`Icons.Filled.Person`）
  - `HorizontalDivider` 在 NavigationBar 顶（outlineVariant，0.5dp）
  - `NavigationBarItemDefaults.colors(indicatorColor=surface)` — 无大 pill
  - 选中 label `FontWeight.SemiBold`
- **路由扩展**：`Routes.kt` 加 `MINE / SETTINGS / ABOUT / VERSION_INFO / UPDATE` 5 个路由 + `RootNavGraph.kt` 装配 + `showBottomBar` 包含 MINE
- **现有屏幕改造**（11 个）：家居 3 屏 + 教育 7 屏 + 登录页 全量改用新 `SettingsCard`/`SettingsRow`/`SettingsScaffold` + 20dp 圆角 + SemiBold 标题
- **编译 + 部署**：
  - `./gradlew :app:compileDebugKotlin` 0 error（2 个 `menuAnchor()` deprecation 警告，已知不修）
  - `./gradlew :app:assembleDebug` + `:app:assembleStaging` 成功（staging APK 18MB）
  - rsync `landing/public/{version.json,myhome.apk}` 到 ECS + 重建 landing 容器
  - prod `curl http://115.120.213.13:8090/version.json` 返回 200 OK + `application/json`，body 正确
  - prod `curl -I http://115.120.213.13:8090/myhome.apk` 返回 200 OK + `application/vnd.android.package-archive` + 18MB
  - **0.2.0 发布（2026-07-20）**：bump `versionName` 0.1.0 → 0.2.0（[build.gradle.kts:21](apps/android/app/build.gradle.kts#L21) `versionCode=2`），version.json 同步 0.2.0 + 新描述（UI 美化 + Mine tab + 检查更新 + 关于/版本信息页），重新构建 + rsync + 重建 landing 容器
- 手动 e2e 待真机跑（登录 → 看新主题 → 切「我的」→ 看头像+用户名+角色 pill → 齿轮 → 设置 → 检查更新 upToDate（v0.2.0 == v0.2.0）→ 版本信息 → 关于 → 退出登录；改 version.json 为 0.3.0 重部署 → 检查更新显示「发现新版本 v0.3.0」+「前往下载」按钮 → 浏览器打开 myhome.apk）

### Phase 3.6（教育模块 Course 重构 v0.10.0）— ✅ 已完成
- **动机**：Phase 3 的 `Subject`（家庭级自定义）+ `Task` 结构不符合实际 — 家长每次建任务都得起标题定积分缺参考，每个家庭维护一份学科列表重复且无统一标准
- **目标**：`Subject` → `Course`（系统预置，非家庭级），`Task.course_id` 替代 `Task.subject_id`，41 条种子覆盖 13 学科
- **后端**：
  - 新增 `models/course.py` — `Course(id/subject/name/description/default_points/is_active/sort_order)`，无 `family_id`（系统级），`ux_courses_subject_name` 唯一
  - 新增 `core/seed_courses.py` — `SEED_COURSES: list[tuple[str,str,str|None,int,int]]` 常量 + `seed_courses_if_empty(db)` 函数
  - 新增 `schemas/course.py` + `api/courses.py` — 只读 router（`GET /api/courses?subject=` + `GET /api/courses/{id}`），任何登录用户可读
  - `Task.subject_id` → `Task.course_id`，加 `relationship("Course", lazy="joined")`（任何 Task 查询自动 JOIN Course，避免 N+1）
  - `TaskOut` schema 加嵌套 `course: CourseOut | None`，list/get 直接返回 course 详情
  - `main.py` lifespan：`create_all` 后跑 `seed_courses_if_empty(db)`（dev 自动 seed；prod 第一次启动也自动 seed）
  - 删除 `models/subject.py` + `schemas/subject.py` + `api/subjects.py` 三个文件
  - 种子目录（41 条 / 13 学科）：数学(4)/语文(5)/英语(4)/物理(3)/化学(2)/生物(2)/历史(3)/地理(3)/体育(4)/音乐(3)/美术(3)/课外(2)/实践(3)
- **Android**：
  - 新增 `net/dto/CourseDto.kt` + `repo/CourseRepository.kt`（只读 list/get），删除 `SubjectDto.kt` + `SubjectRepository.kt`
  - `TaskDto`：`subjectId` → `courseId` + 嵌套 `course: CourseDto?`
  - `ApiService.kt`：删 5 个 subject 端点，加 2 个 course 端点（`listCourses(subject)` + `getCourse(id)`）
  - `TaskFormScreen.kt`：学科下拉 → 选择课程下拉，label 为 `"{subject} · {name} (+{defaultPoints})"`；选中课程时自动填 title（若空）/points（若默认1）/description（若空+课程有描述）；仍可手动改 — 课程只是参考模板
  - `TaskDetailScreen.kt`：SettingsCard 顶部加「课程」行显示 `course?.let { "${it.subject} · ${it.name}" } ?: "无"`
  - `TaskListScreen.kt`：TaskCard 副标题加 `[subject]` 前缀（如 `[数学] 10 积分 · 截止 ...`）
  - Routes/NavGraph 不动 — Subject 无独立路由
- **DB 迁移（prod）**：`migrations/course_refactor.sql` — `CREATE TABLE courses` + `INSERT 41 条种子 ON CONFLICT DO NOTHING` + `ALTER TABLE tasks ADD COLUMN course_id` + `ALTER TABLE tasks DROP COLUMN subject_id` + `DROP TABLE subjects`。幂等（IF NOT EXISTS / ON CONFLICT），prod 跑前 pg_dump 备份到 `/opt/myhome-backup-YYYYMMDD-HHMMSS.sql`
- **坑**：dev `create_all` 不会 ALTER 已有表加列，必须先 `DROP TABLE task_records, tasks, subjects, courses` 再重启 backend；prod 用 migration SQL
- **e2e（dev + prod 都验证通过）**：register → list courses(41) → filter 数学(4) → create task with course_id → task.course nested → complete → points +10
- **0.10.0 发布（2026-07-23）**：bump versionCode 33→34, versionName 0.9.2→0.10.0；rsync backend + landing public；重建 backend + landing 容器；prod `/api/courses` 返回 41 条；`/version.json` 返回 0.10.0；APK 30MB

### Phase 3.7（系统管理 Course 管理 UI v0.10.1）— ✅ 已完成
- **动机**：v0.10.0 上线后，41 条种子课程目录是静态的，需要 admin 端 CRUD UI 才能后续调整课程目录（家长反馈新增/调整课程时不必写迁移 SQL）
- **后端**：
  - 新增 `schemas/course.py` 中 `CourseCreate` + `CourseUpdate`（全字段可空，支持部分更新）
  - 新增 `api/admin_courses.py` — prefix `/api/system/courses`，tags `system-courses`，全部 `Depends(require_admin)`：
    - `GET /api/system/courses?subject=` — list，默认 `include_inactive=True`（admin 看全部），可按 subject 过滤
    - `POST /api/system/courses` — create，409 `course_subject_name_taken` on IntegrityError
    - `PUT /api/system/courses/{id}` — update，409 同上，404 `course_not_found`
    - `DELETE /api/system/courses/{id}` — 204，404 同上
    - `POST /api/system/courses/{id}/activate` + `/deactivate` — 软停用（保留行，家长端 list 过滤掉）
  - `main.py` 装配 `admin_courses.router`（与 user-side `courses.router` 共存，前者写 `/api/system/courses` admin，后者读 `/api/courses` 任意登录用户）
- **Android**：
  - `net/dto/CourseDto.kt` — `CourseDto` 加 `sortOrder/createdAt/updatedAt` 字段；新增 `CourseCreateRequest` + `CourseUpdateRequest`（全字段可空，对齐后端 schema）
  - `repo/CourseRepository.kt` — 新增 `adminList/create/update/delete/activate/deactivate` 6 个方法（user-side `list/get` 保留）
  - `net/ApiService.kt` — 新增 6 个 admin 端点（`api/system/courses` 系列，`@Body CourseCreateRequest/UpdateRequest`、`@DELETE` 返回 `Unit`）
  - `ui/system/CourseListScreen.kt` + `CourseListViewModel.kt` — 列表页：
    - `CourseListUiState(loading/saving/courses/error/toast)`
    - `LaunchedEffect(Unit) { vm.refresh() }` 进入页自动拉一次
    - `LazyColumn` 按 `subject` 分组（groupBy），每组 header 显示 `"数学（4）"`
    - `CourseCard` 用 `SettingsCard` + `SettingsRow`，title=`course.name`，subtitle=`"+{defaultPoints} 积分"` + 「已停用」tag
    - `PillAction` 行：激活/停用（互斥） + 编辑 + 删除，红色 destructive variant for 删除
    - `AlertDialog` 删除二次确认
    - `SnackbarHost` 显示 toast（已激活/已停用/已删除）+ error
  - `ui/system/CourseEditScreen.kt` + `CourseEditViewModel.kt` — 表单页：
    - `CourseEditUiState(loading/saving/existing/error)`
    - 字段：学科 / 课程方式名 / 描述（multiline） / 默认积分 / 排序 / 启用 Switch
    - 编辑模式 `LaunchedEffect(existing)` 一次性回填（`initialized` flag 防止 re-composition 反复覆盖用户输入）
    - 数字字段用 `.filter { c.isDigit() }`，提交时 `toIntOrNull() ?: 10` 兜底
    - 创建调 `repo.create(CourseCreateRequest(...))`，更新调 `repo.update(id, CourseUpdateRequest(...))`
    - `onDone = onBack` 成功后自动返回列表
  - `ui/system/SystemScreen.kt` — 新增 `onOpenCourses` 参数 + 第 4 行「课程管理」(`Icons.Filled.School`)，subtitle「系统预置课程目录（学科 / 课程方式 / 默认积分）」
  - `ui/nav/Routes.kt` — 加 `COURSE_LIST = "course_list"` + `COURSE_EDIT = "course_edit?id={id}"` + `courseEdit(id)` helper
  - `ui/nav/RootNavGraph.kt` — 加 2 个 composable + SystemScreen 调用注入 `onOpenCourses = { navigate(COURSE_LIST) }`
  - `util/FriendlyError.kt` — 加 `course_not_found` → "课程不存在"，`course_subject_name_taken` → "该学科下课程名已存在"
- **e2e（dev + prod 都验证通过）**：admin login → list 41 → filter 数学(4) → create "数学·测试课程" 201 → 409 重复 → update default_points=20 → deactivate → activate → delete 204 → 404 not_found；non-admin 403
- **0.10.1 发布（2026-07-23）**：bump versionCode 34→35, versionName 0.10.0→0.10.1；rsync backend + landing public；重建 backend + landing 容器；prod `/api/system/courses` 返回 41 条；`/version.json` 返回 0.10.1；APK 30MB

### Phase 3.8（Android 401 自动刷新 + Session 失效跳登录 v0.10.2）— ✅ 已完成
- **动机**：v0.10.1 上线后用户报告切换账号 → MINE 页显示「登录已失效，请重新登录」+ 重试按钮无反应。根因：[net/AuthInterceptor.kt](apps/android/app/src/main/java/com/myhome/net/AuthInterceptor.kt) 只加 Bearer header 不处理 401；`refreshToken` 字段全代码库 write-only；[repo/AuthRepository.kt](apps/android/app/src/main/java/com/myhome/repo/AuthRepository.kt) `switchToAccount` 把过期 accessToken + 失效 refreshToken 复制到活跃槽不校验；`MineViewModel.refresh` 用同一张过期 token 重试必然再 401。
- **后端事实（已验证，无需改动）**：`POST /api/auth/refresh` 接受 `{refresh_token}`，返回 `AuthResponse {access_token, refresh_token, token_type, user}`，**rotate refreshToken**；access TTL 24h / refresh TTL 90d；stateless JWT 无 server-side 黑名单；`/api/auth/refresh` 不依赖 `Authorization` header，只读 body。
- **Android 实施**：
  - 新增 [net/RefreshApi.kt](apps/android/app/src/main/java/com/myhome/net/RefreshApi.kt) — 独立 Retrofit 接口，只一个 `refresh(@Body RefreshRequest): AuthResponse`，挂在无 Authenticator 的 OkHttpClient 上避免递归
  - 新增 [net/AuthAuthenticator.kt](apps/android/app/src/main/java/com/myhome/net/AuthAuthenticator.kt) — OkHttp `Authenticator` 实现：
    - 401 触发：用 `tokenStorage.getRefreshToken()` 调 `refreshApi.refresh(...)` → 拿新 accessToken + 可能 rotate 过的新 refreshToken
    - 写回：`tokenStorage.save(newToken)` + `accountStore.updateTokens(resp.user.id, newToken)`（保留 `savedAt`，refresh 是 silent 操作不重排账号列表）
    - 重试：用新 accessToken 重建原 request 返回给 OkHttp
    - 失败：`tokenStorage.clear()` → 复用 [ui/nav/RootNavGraph.kt](apps/android/app/src/main/java/com/myhome/ui/nav/RootNavGraph.kt) 现有 `tokenFlow.collect → hasToken==false → navigate(LOGIN)` 机制自动跳登录页
    - 并发：`Mutex` 串行化 refresh；后到的请求在 mutex 内 re-check `currentAccess != failedToken` 后直接用新 token 重试，不重复 refresh
    - 递归保护：`encodedPath.endsWith("/api/auth/refresh")` 命中直接 return null；`responseCount(response) >= 2` 提前放弃
  - 修改 [storage/AccountStore.kt](apps/android/app/src/main/java/com/myhome/storage/AccountStore.kt) — 新增 `updateTokens(userId, token: TokenData)`，`map { acc -> if (acc.id == userId) acc.copy(accessToken=..., refreshToken=...) else acc }` 保留 `savedAt`
  - 修改 [di/NetworkModule.kt](apps/android/app/src/main/java/com/myhome/di/NetworkModule.kt) — 新增 `@Named("refresh")` 三件套（OkHttpClient 无 authInterceptor/authenticator + Retrofit + RefreshApi）；主 OkHttpClient 加 `.authenticator(authAuthenticator)`
  - 不改：`AuthInterceptor` / `TokenStorage` 接口 / `AuthRepository.switchToAccount`（切完 MINE 页 `getMe` 自然 401 → Authenticator 自动处理）/ `MineViewModel.refresh` / `RootNavGraph` tokenFlow 重定向 / `FriendlyError` 现有 `invalid_token` 翻译
- **坑预判**：
  - DI 环：`AuthAuthenticator` → `RefreshApi` → `@Named("refresh") Retrofit` → `@Named("refresh") OkHttpClient`（无 Authenticator）— 不依赖主 OkHttpClient，无环
  - Authenticator 在 OkHttp dispatcher 线程，`runBlocking` 安全（与 `AuthInterceptor` 现有 `runBlocking { tokenStorage.getAccessToken() }` 同模式）
  - refresh 失败时 `tokenStorage.clear()` → `RootNavGraph` `LaunchedEffect(hasToken)` 在下一帧触发 navigate(LOGIN)，期间 ViewModel 可能短暂渲染 ErrorState 一帧（视觉上「立刻跳到登录页」）
- **e2e 验证（待真机）**：dev 注册 A → 登录 → 退出（AccountStore 保留 A）→ 注册 B → 登录 → 切换 A → MINE 显示 A profile（access 24h 内未过期，不触发 refresh）→ backend 改 `ACCESS_TOKEN_EXPIRE_MINUTES=1` 重启 → 等 1 分钟 → 切回 B 再切回 A → 401 → Authenticator 自动 refresh + 重试 + 写回 AccountStore → 成功；破坏 A 的 refreshToken → 切到 A → refresh 失败 → `tokenStorage.clear()` → 跳 LOGIN
- **0.10.2 发布（2026-07-24）**：bump versionCode 35→36, versionName 0.10.1→0.10.2；仅 Android 改动，rsync landing public + 重建 landing 容器；prod `/version.json` 返回 0.10.2；APK 30MB

### Phase 3.9（KET 学习课 + 测评课 v0.15.0）— ✅ 已完成
- **动机**：v0.14.x 只有「英语·KET·朗读」一种互动课程；补齐 KET 的另两个形态——学习课（教新词）+ 测评课（校准能力）
- **后端**：
  - `core/seed_courses.py` — LEARNING_METHODS 7 → 9：加 `("学习", 5)`、`("测评", 15)`
  - `core/seed_words.py` — 改为按课程幂等：对每门无词的 active 英语·KET 课程插 150 词（lexeme_id 走 lexicon 复用，三课共享）
  - `api/courses.py` — 新端点 `POST /api/courses/{cid}/words/{wid}/score`（body `{score:0-100}`）：学习/测评客户端判对错后回写，与朗读 ISE 共用 `upsert_mastery`（EMA + initial clamp）；`word.lexeme_id is None` → 400 `word_not_linked`
  - `list_next_words` 新 mode `assess`：分层抽样（new/learning<0.7/familiar 0.7-0.9/mastered≥0.9 四带各取约 1/4，不足互补），测评课用
  - `schemas/word_assessment.py` — `WordScoreIn` / `WordScoreOut`
  - 迁移 `migrations/ket_learn_quiz_courses.sql`（幂等）：插「英语·KET·学习」(5 分) +「英语·KET·测评」(15 分)，从朗读课复制 150 词（**同 lexeme_id**，能力跨课程互通）
  - 评分映射：学习对=50/错=20；测评对=100/错=0
- **Android**：
  - `net/dto/CourseDto.kt` — `CourseSessionType { READING, LEARN, QUIZ }` + `CourseDto.sessionType()`，消除三处 `textbook=="KET" && learningMethod=="朗读"` 硬编码（TaskDetailViewModel / CourseListScreen / CourseDetailScreen）
  - `net/dto/WordDto.kt` — `WordScoreRequest` / `WordScoreResponse`；`ApiService.submitWordScore` + `CourseRepository.submitWordScore`
  - 新建 `ui/education/LearnSession{Screen,ViewModel}.kt` — 10 词 `mode="learn"`；状态机 STUDY（拼写+音标+释义+例句+TTS，进卡自动发音）→ SPELL（看中文输英文，imeAction=Done）→ FEEDBACK → SUMMARY；判对 `trim().equals(spelling, ignoreCase=true)`
  - 新建 `ui/education/QuizSession{Screen,ViewModel}.kt` — 15 题 `mode="assess"`；QUESTION → FEEDBACK（1.5s 自动跳）→ REPORT（答对 X/15 + 正确率 + 逐题明细列表）
  - 收尾模式仿朗读：任务模式 SUMMARY/REPORT 手动 `taskRepo.complete`（学完/答完才可点）；体验模式进总结自动 `repo.experience`；`task_completed` savedStateHandle 回写
  - Routes + RootNavGraph：`LEARN_SESSION(_TASK)` / `QUIZ_SESSION(_TASK)` 四条路由；TaskDetailScreen 签名改 `onOpenSession(type, courseId, taskId)` 按 type 分流，按钮文案 开始朗读/开始学习/开始测评
  - ChildEducationScreen 任务卡标注 `[需朗读]/[需学习]/[需测评]`
  - `FriendlyError.kt` 加 `word_not_linked` → "该单词未关联词库"
- **e2e（dev + prod 都验证通过）**：迁移幂等（重复跑 INSERT 0 0）；三课共享 150 lexeme；mode=learn 新词优先 / mode=assess 覆盖四能力带；score=50 → 新词 mastery 0.5；跨课程 EMA（学习课 50 → 测评课 100 同 lexeme → 0.65）；score=200 → 422；bogus mode → 400
- **0.15.0 发布（2026-08-04）**：bump versionCode 61→62, versionName 0.14.1→0.15.0；prod 备份 → 跑迁移（INSERT 0 2 / INSERT 0 300）→ rsync backend + landing public → 重建 backend + landing 容器；aapt2 验证 versionCode=62；prod `/version.json` 返回 0.15.0；APK 32MB
- **0.15.1 发布（2026-08-04）**：学习/测评拼写改内置英文点按键盘（`ui/education/SpellingKeyboard.kt` — QWERTY 26 字母 + 退格，`SpellingInputDisplay` 方块序列展示已敲字母），替代系统输入法避免中文候选/自动纠错干扰；versionCode 63；仅 Android 改动，rsync landing public + 重建 landing 容器
- 手动 e2e 待真机跑（家长建学习/测评任务 → 孩子做完得积分；能力中心三课 tab 覆盖率一致）

### Phase 3.10（家长自学 tab + 底部导航改名 v0.16.0）— ✅ 已完成
- **动机**：家长也需要自己学习——底部「教育」改名「学习」，家长界面顶部加 教育/自学 tab；自学可自选互动课程、有自己的能力中心，但**无积分、不可兑换奖励**
- **后端**：
  - `api/skills.py` — `_build_course_coverage` 加 `touched_only` 参数；`/me/courses` 按角色分流：孩子（roles 含 child）仍走 v0.14.1「任务涉及课程」；家长（非 child）走「接触过的课程」（`Word.course_id` where lexeme_id ∈ 该用户 mastery，共享 lexeme 映射回全部姊妹课程）。`/children/{cid}/courses` 不变
- **Android**：
  - `BottomBar.kt` — TabItem label 教育→学习（route 仍 `Routes.EDUCATION`）
  - `ParentEducationScreen.kt` — title「学习」+ TopAppBar 下 TabRow（教育/自学，`rememberSaveable` 存选中）；奖励/设备管控 pills + 新建任务 FAB 仅教育 tab；原内容抽成 `EducationContent`；自学 tab 为 `SelfStudyContent`（我的能力中心入口 → SKILL_CENTER Self 模式 + selfCourses 课程卡 + 「自学不获得积分」说明）
  - `ParentEducationViewModel` — 注入 `CourseRepository`，UiState 加 `selfCourses`（`courseRepo.list()` filter `sessionType() != null`，失败不阻塞主内容）
  - Session 自学模式（无积分结算）：`Learn/QuizSessionViewModel` 加 `loadSelfStudy(courseId)` + UiState.selfStudy，进 SUMMARY/REPORT 时 `taskId==null && !selfStudy` 才 `finishExperience`；`ReadingSessionViewModel.load(courseId, selfStudy)`，`finishFlow` 中 `naturalEnding && !selfStudy` 才调 experience；提前结束 TTS 文案自学版不提积分（`FINALE_EARLY_SELF`）
  - Screens 加 `selfStudy: Boolean = false` 参数：Learn/Quiz 总结卡自学分支显示「完成」按钮直接 onBack（无结算 spinner / ResultDialog）；Reading ResultDialog + 提前结束确认框自学时不提积分
  - Routes + RootNavGraph：`READING/LEARN/QUIZ_SESSION_SELF` 三条路由（`selfStudy=true` 装配）；家长 SKILL_CENTER 复用 Self 模式（/me 端点已角色分流，无需改动）
- **e2e（dev + prod 都验证通过）**：家长 /me/courses 无 mastery → [] → 提交 score 50 → 3 门 KET 课（朗读/学习/测评，共享 lexeme）各 touched=1；孩子 /me/courses 仍任务制（prod 孩子只见 KET·朗读）
- **0.16.0 发布（2026-08-04）**：bump versionCode 63→64, versionName 0.15.1→0.16.0；rsync backend + landing public → 重建 backend + landing 容器；prod `/version.json` 返回 0.16.0；APK 32MB
- 手动 e2e 待真机跑（家长切自学 tab → 选课学一轮 → 能力中心出现课程 tab 且无积分变动；孩子界面仅底部 label 变化）

### Phase 3.11（自学教材层级 + 教育 tab 入口调整 v0.16.1）— ✅ 已完成
- **动机**：v0.16.0 自学 tab 平铺课程不合理——教材（英语·KET / 托业…）才是家长挑选的粒度，课程（朗读/学习/测评）是教材下的学习方式；奖励/设备管控作为 TopAppBar pills 视觉上不属于「教育」tab
- **后端**：
  - 新模型 `models/self_study.py` — `SelfStudyTextbook(id/user_id/subject/textbook)`，`ux_self_study_textbooks_user(user_id, subject, textbook)` 唯一；个人清单无 family_id，按 user_id 隔离
  - 新路由 `api/self_study.py` prefix `/api/self-study`：`GET /textbooks`（我的教材 + 各教材 active 课程嵌套）、`GET /textbooks/available`（系统 active 课程按 subject+textbook 聚合）、`POST /textbooks`（添加，404 `textbook_not_found` 无 active 课程 / 409 `textbook_already_added`）
  - 迁移 `migrations/self_study_textbooks.sql`（幂等 CREATE TABLE + UNIQUE INDEX）
  - 课程不在教材表冗余——教材下课程实时查 courses 表，前端按 `sessionType()` 过滤互动课
- **Android**：
  - `net/dto/SelfStudyDto.kt` + `repo/SelfStudyRepository.kt` + `ApiService` 3 端点
  - `ParentEducationViewModel` — `selfCourses`（平铺课程）换成 `selfTextbooks` + `availableTextbooks`（弹窗懒加载）+ `addTextbook` + toast；注入 SelfStudyRepository 替代 CourseRepository
  - `ParentEducationScreen` — 奖励/设备管控从 TopAppBar pills 移到教育 tab 内容顶部 SettingsCard（CardGiftcard/PhoneAndroid 图标 rows），PillAction 删除；自学 tab 改教材层级：`SelfTextbookCard` 点击展开教材下 朗读/学习/测评 课程行（rememberSaveable per-card expanded）；「添加教材」row → AlertDialog 列出有互动课且未添加的教材，点击即添加
  - `FriendlyError.kt` 加 `textbook_not_found`/`textbook_already_added`
- **e2e（dev + prod 都验证通过）**：available 返回 英语·KET(3 课)；add 201；mine 带出嵌套课程；dup 409；bogus textbook 404
- **0.16.1 发布（2026-08-05）**：bump versionCode 64→65, versionName 0.16.0→0.16.1；prod 备份 → 迁移（CREATE TABLE + INDEX）→ rsync backend + landing public → 重建 backend + landing 容器；aapt2 验证 versionCode=65；prod `/version.json` 返回 0.16.1；APK 32MB
- **0.16.1 后端热修（2026-08-05，无 APK 变更）**：家长反馈「添加了教材但能力中心不显示课程进度」——根因：touched_only 只按 mastery 映射课程，未学习时为空。修复：`_build_course_coverage` touched_only 分支改为「我的教材下课程 ∪ 接触过课程」（`tuple_(subject, textbook).in_(self_study_textbooks)`），添加教材即出现 0/150 进度 tab；dev + prod 验证（新家长加 KET → 3 课 0 进度；老账号 touched 计数不变）；仅 rsync backend + 重建 backend 容器
- 手动 e2e 待真机跑（自学 tab 添加 KET → 展开选测评 → 学完无积分；教育 tab 奖励/设备管控 rows 可点）

### Phase 3.12（能力中心教材维度 v0.16.2）— ✅ 已完成
- **动机**：能力中心原按课程展示进度，但教材下各课程（朗读/学习/测评）共享同一批 lexeme，课程 tab 会把同一份进度重复 3 次——教材才是跟踪学习进度的正确粒度，课程只是教材的学习手段
- **后端**：
  - `schemas/skill.py` — `CourseCoverageOut` → `TextbookCoverageOut`（subject/textbook/learning_methods/total/touched/mastered/覆盖率/is_completed；无 course_id）
  - `api/skills.py` — `_build_course_coverage` → `_build_textbook_coverage`：教材集合孩子=任务涉及课程所属 (subject,textbook) pairs，家长=我的教材 ∪ 接触过课程所属教材；每个教材对其全部 active 课程做 `distinct lexeme_id`（共享 lexeme 天然去重）；端点 `/me/courses`→`/me/textbooks`、`/children/{cid}/courses`→`/children/{cid}/textbooks`
  - 单词明细过滤 `course_id` → `subject`+`textbook`（两者须同时提供，否则 400 `invalid_textbook_filter`；内部 join Course 按 pair 过滤）
- **Android**：
  - `SkillDto.kt` — `CourseCoverageDto` → `TextbookCoverageDto`（含 `learning_methods` + `key = "$subject|$textbook"` 选中态复合键）
  - `ApiService`/`SkillRepository` — 端点与参数同步改名（myTextbooks/childTextbooks；words 传 subject+textbook）
  - `SkillCenterViewModel` — `courses`→`textbooks`、`selectedCourseId`→`selectedTextbookKey`，fetchWords 按 key 拆 subject/textbook
  - `SkillCenterScreen` — 「课程进度」→「教材进度」；tab 只显示教材名（不再带 ·朗读/·学习 后缀）；进度卡标题 `{subject} · {textbook}` + 学习方式行（朗读 / 学习 / 测评）；空态文案按 mode 分流（Self 提示去自学 tab 添加教材，Child 提示等家长布置任务）
  - `ParentEducationScreen` — 孩子能力卡 `CoursePill` → `TextbookPill`，每教材一个 pill（KET 从 3 个相同 pill 变 1 个）
  - `FriendlyError.kt` 加 `invalid_textbook_filter` → "教材过滤参数无效"
- **e2e（dev + prod 都验证通过）**：孩子建 KET 学习任务 → /me/textbooks 单条 KET（150 词，3 methods，touched/mastered 与共享 lexeme 一致）；家长 KET（我的教材 ∪ 接触过）；words subject+textbook 过滤 150/new=总数-touched；单 subject 400；旧 /me/courses 404；prod 孩子 KET 150/4/1 单条聚合
- **0.16.2 发布（2026-08-05）**：bump versionCode 65→66, versionName 0.16.1→0.16.2；rsync backend + landing public → 重建 backend + landing 容器；aapt2 验证 versionCode=66；prod `/version.json` 返回 0.16.2；APK 32MB
- 手动 e2e 待真机跑（能力中心教材 tab 切换 + 单词明细联动；教育 tab 孩子能力卡单 KET pill）

### Phase 3.13（KET 全量词库 150→1539，2026-08-10）— ✅ 已完成
- **动机**：v0.15.0 起 KET 三课只有 150 词，远低于剑桥 A2 Key 实际考核范围
- **词表来源**：剑桥官方 A2 Key (2020) 词表 PDF（alphabetical 主表 1431 个纯字母词条 + Appendix 1 词集 98 词）+ 既有 150 词中官方表外 10 词保留（beer/cherry/narrow/peach/pig/smell/taste/thick/touch/wine）→ 合计 1539 词；111 个多词短语/含符号条目（alarm clock、a.m.、o'clock 等）未收录——App 拼写键盘仅 26 字母无法输入
- **字段生成**：syllables 用 pyphen(en_US)（与既有 150 一致）；phonetic/meaning_cn/例句/译文 LLM 批量生成 + 脚本校验（格式/CJK/音标/例句含词/去重）
- **改动文件**：`core/seed_words.py` 全量重写（1645 行 1539 元组；既有 150 词保持 sort 1-150 原样，新词按字母序 151-1539）；新迁移 `migrations/ket_full_word_bank.sql`（幂等：lexicon 按 spelling upsert + 三门 KET 课 WHERE NOT EXISTS 插词，同 lexeme_id 共享）
- **纯数据变更**：无 API/schema 变化，无 Android 改动，**未 bump 版本**（versionCode 仍 66 / 0.16.2）
- **e2e（dev + prod 都验证通过）**：迁移 lexicon 150→1539、KET words 450→4617（1539×3）；幂等重跑 INSERT 0 0；孩子既有进度保留（touched 4 / mastered 1 不变，总量 150→1539）；new=总数-touched；learn mode 既有 150 优先；assess 15 题分层；新词 score=100 → mastery 0.5（initial clamp）落库
- **部署**：prod 备份 → `docker exec -i myhome-postgres psql < ket_full_word_bank.sql` → rsync backend → 重建 backend 容器（seed_words.py 影响新装环境）

### Phase 3.14（托业词库 0→2402 + 三门互动课，2026-08-10）— ✅ 已完成
- **动机**：KET 之后补齐托业（家长自学主要场景）；ETS 官方不公布托业词表，无剑桥式权威清单
- **词表来源**：三个公开词表并集——墨墨 TOEIC 990 核心词汇（1378 纯字母词）+ Pass the TOEIC Test 官方词表 PDF（1041）+ toeic-600 话题词集（562），去重 **2402 词**；多词短语/连字符条目（baggage claim、duty-free 等）未收录（拼写键盘仅 26 字母）
- **字段生成**：20 批并行 LLM 生成（音标/释义/例句/译文）+ 校验脚本 0 问题；syllables 用 pyphen(en_US)；与 KET 重叠 226 词按**托业商务语境重新生成义项**（check→支票；账单、plant→工厂、capital→资本），仍共享 lexeme_id（能力跨教材互通）
- **改动文件**：`core/seed_words.py` 泛化为多教材（`banks = {"KET": KET_WORDS, "托业": TOEIC_WORDS}`，先找空课程再 upsert lexicon 再插词）；新迁移 `migrations/toeic_word_bank.sql`（幂等：补建托业·学习/测评两门课 + 激活朗读/学习/测评 + lexicon upsert + 三课插词）
- **课程现状**：托业原只有 7 门 inactive 占位课（学习/测评从未被插入——v0.15 的 LEARNING_METHODS 扩充只经迁移 SQL 落了 KET），本次补建 2 门并激活 3 门互动课
- **纯数据变更**：无 API/schema 变化，无 Android 改动（sessionType 只认 subject+learning_method，自学添加教材/能力中心自动出现托业），**未 bump 版本**
- **e2e（dev + prod 都验证通过）**：迁移 courses +2 / UPDATE 1（激活朗读）/ lexicon +2176（2402−226 重叠）→ 3715 / words +7206（2402×3）；幂等重跑全 0；available 教材出现托业（3 课）；添加教材 → /me/textbooks 托业 2402/0；assess 15 题分层；新词 score=100 → mastery 0.5 → touched 1；check 在 KET/托业 6 门课共享同一 lexeme_id
- **部署**：prod 备份 → 迁移 → rsync backend → 重建 backend 容器

### Phase 3.15（人教版 20 册词库 0→5049 + 真实教材结构，2026-08-10）— ✅ 已完成
- **动机**：托业上线后补齐人教版（PEP）教材——孩子校内主战场，20 册覆盖小学三年级到高中选择性必修四
- **教材结构重构**：从 24 个旧占位（小学一/二年级上下、初中九年级上下、高中高一-高三上下 共 24 × 7 method = 168 行 inactive）改为 20 册真实结构——小学三起点 8 册（PEP）+ 初中 5 册（Go for it!，九年级合订为全一册）+ 高中 7 册（2019 版必修 1-3 + 选择性必修 1-4）。旧占位教材**保持 inactive 不删**（迁移 SQL 不动它们）。`seed_courses.py` ENGLISH_TEXTBOOKS 改 23 册（20 PEP + KET/托业/雅思），新增 `INTERACTIVE_METHODS = {"朗读","学习","测评"}` 决定 active 状态
- **词表来源**：cyforkk/pep-english-words (MIT) 单元 → 单词 JSON；20 册共 5049 词条次（4132 去重）。多词短语/含符号条目未收录（拼写键盘仅 26 字母）
- **字段策略**（关键决策）：4132 去重词中
  - 1359 与 KET 重叠 → 复用 KET tuple（校园语境友好，已校验）
  - 1043 与 TOEIC-only 重叠 → 复用 TOEIC tuple（商务义项，后续可重生成校园语境版）
  - 1948 净新词 → LLM 并行生成（17 批 × ~115 词/批），校园/家庭/日常场景，A2-B1 难度，6-12 词例句
  - 全部共享同一 lexeme_id → 能力跨教材（KET/托业/PEP）互通
- **改动文件**：`core/seed_courses.py` ENGLISH_TEXTBOOKS 改 23 册 + is_active 按 INTERACTIVE_METHODS 决定；`core/seed_words.py` 加 `PEP_BANKS: dict[textbook, list[tuple]]`（5049 行）+ `banks = {**{"KET","托业"}, **PEP_BANKS}`；新迁移 `migrations/pep_word_banks.sql`（幂等：插 8 新教材 × 9 method + 12 overlap 教材补 学习/测评 + 激活 朗读/学习/测评 + lexicon upsert + 每教材 × 3 active 课插词）
- **坑（迁移 SQL）**：PostgreSQL VALUES 子句的 boolean 字面量必须用 `TRUE`/`FALSE`,**不**接受 `t::boolean`/`f::boolean`（首跑 `ERROR: column "t" does not exist`）；修正后 INSERT 96 课程 / UPDATE 12 / INSERT 1948 lexicon / INSERT 15147 words
- **纯数据变更**：无 API/schema 变化，无 Android 改动（sessionType 只认 subject+learning_method，自学添加教材/能力中心自动出现 PEP），**未 bump 版本**
- **e2e（dev + prod 都验证通过）**：courses 230→326（+96）/ english_active 6→66（+60 = 20 教材 × 3 method）/ lexicon 3715→5663（+1948）/ words 11823→26970（+15147 = 5049×3）；幂等重跑全 0；ruler 在 KET/PEP 6 门课共享同一 lexeme_id；PEP ruler 50 分 → mastery 0.5，KET ruler 100 分 → mastery 0.65（EMA 跨教材累积）；assess 15 题分层；422/404/400 错误码齐全
- **部署**：prod 备份 4.2MB → 迁移 → rsync backend → 重建 backend 容器

### Phase 3.16（雅思词库 0→4321 + 三门互动课，2026-08-10）— ✅ 已完成
- **动机**：Phase 3.15 因 Kimi 5h 配额限流未上线的雅思,配额恢复后补齐
- **词表来源**：lzrknglsh IELTS-4000.txt 共 4321 词(纯字母条目;多词短语/连字符未收录——拼写键盘仅 26 字母)
- **字段策略**(同 PEP):
  - 275 与 KET 重叠 → 复用 KET tuple(校园语境)
  - 1183 与 TOEIC-only 重叠 → 复用 TOEIC tuple(商务义项)
  - 2863 净新词 → LLM 并行生成(36 批 × ~120 词/批,中间被 5h 配额 429 打断两轮,模型切换 + 波次缩到 5-8 并发后全部完成),学术/教育/科技/社会场景,A2-B2 难度,5-20 词例句(比 PEP 6-12 放宽)
  - 全部共享同一 lexeme_id → 能力跨教材(KET/托业/PEP/雅思 4 套)互通
- **改动文件**:`core/seed_words.py` 加 `IELTS_WORDS: list[tuple]`(4321 行,字母序)+ banks dict 加 `"雅思": IELTS_WORDS`;新迁移 `migrations/ielts_word_bank.sql`(幂等:插 雅思·学习/测评 2 门 + 激活 朗读/学习/测评 + lexicon upsert + 3 active 课插词)
- **纯数据变更**:无 API/schema 变化,无 Android 改动,**未 bump 版本**
- **e2e(dev + prod 都验证通过)**:courses_ielts 7→9(+2 学习/测评),lexicon 5663→7804(+2141 净新),words 26970→39933(+12963 = 4321×3);幂等重跑全 0;available 教材 23 册齐;添加雅思教材 → /me/textbooks 雅思 4321/0;learn 10 词(新词优先);assess 15 词;score=100 → mastery 0.5;abandon 在 托业/雅思/高中选必三 9 门课共享同一 lexeme_id;422/404/400 错误码齐全
- **部署**:prod 备份 2.7MB → 迁移 → rsync backend → 重建 backend 容器
- **batch_22 教训**:第一波 32 并发触发 Kimi 429 + 5h 配额,后续 5-8 并发 + 等 5h 重置窗口;`batch_26` 重启时 agent 启动后无回执消失,直接重启新 agent 即可,不等死信

### Phase 4（学科成绩 + 学习时长统计 + 每日打卡 v0.17.0）— ✅ 已完成
- **动机**：补齐教育模块三大件——考试成绩记录、学习时长统计、习惯养成打卡。设计决策（与用户确认）：打卡复用已有 Habit 后端（原计划的 DailyCheckinDefinition/Log 未建）；成绩单条录入；时长自动埋点不给积分；入口全放教育 tab
- **后端**（3 个 router，16→19 个）：
  - `api/habits.py`（已有未部署，本次上线）：家长建习惯（name 唯一/points/streak_cap/is_active）+ 全员打卡 `POST /{id}/log`（streak=昨日+1 断签归 1，积分 = min(streak,cap)*points，`PointSource.checkin` 三件套）+ `GET /logs` 双视角；`ux_habit_logs_habit_user_date` 按天唯一
  - `api/grades.py`（新）：`Grade`（family_id + subject String(32) 自由文本不 FK + score/score_full Float + exam_name/exam_date/note + assignee_user_id CASCADE NOT NULL + created_by SET NULL）；POST/PUT/DELETE require_parent（assignee 同家庭校验 404 `assignee_not_found`）；GET 双视角；score>score_full → 422 `score_exceeds_full`（PUT 时应用后组合校验 400）；无汇总端点（列表页内存聚合）
  - `api/study_sessions.py`（新）：`StudySession`（user_id + 冗余 subject/textbook/learning_method 三 String 不 FK course_id + session_type/source String + Literal 校验 + duration_seconds ge=1 le=86400 + session_date）；POST 任何登录用户（user_id 服务端取）；`GET /stats` 服务端聚合（today/week(周一)/total + group_by subject,textbook 降序）；`GET` 明细 limit 50。**本表无 family_id**——跨家庭防护靠 `_resolve_target_user` 显式校验（孩子传他人 403 parent_only / 家长跨家庭 404 child_not_found）
  - 迁移：`habits.sql`（含 `ALTER TYPE point_source ADD VALUE 'checkin'`——**必须先于新后端部署**，create_all 不给已有 enum 补值）+ `phase4_grades_study_sessions.sql`（幂等 CREATE TABLE×2 + INDEX×4）
- **Android**（5 新屏 + 3 repo + 埋点）：
  - `ui/education/Habit{List,Form}{Screen,ViewModel}.kt` — 双视角打卡页（habit 卡 trailing=打卡 pill/✓/spinner，subtitle=`连续N天 · 每次+X积分(封顶cap天)`；家长额外新建/编辑入口 + 最近打卡 20 条；409→toast 今天已打过）+ CRUD 表单（编辑模式含删除按钮）
  - `ui/education/Grade{List,Form}{Screen,ViewModel}.kt` — 成绩列表（家长成员 chips 内存过滤 + 汇总卡「共N条·平均得分率%·最近」+ subject groupBy 分组组头平均分）+ 表单（学科 ExposedDropdownMenu 可编辑下拉取 courseRepo.list() distinct subject 13 学科；分数/满分小数过滤 + score<=full 校验；DatePicker；单人家庭默认选第一个孩子）
  - `ui/education/StudyStats{Screen,ViewModel}.kt` — 家长成员 chips（我自己+各孩子切换重请求）+ 今日/本周/累计三列大数字卡 + 按教材分布 + 最近学习 10 条（reading/learn/quiz→中文）
  - 入口：ChildEducationScreen 加「每日打卡」卡（HabitCheckinCard，VM 并行拉 habits 算「今日 X/Y」失败降级）；ParentEducationScreen 教育 tab 顶部卡追加 成绩管理(Grade)/学习时长(Schedule)/习惯打卡(CheckCircle) 三行
  - **埋点**（Learn/Quiz/Reading 三 VM 同模式）：`sessionStartMs = SystemClock.elapsedRealtime()` 记起点 → 收尾（Learn/Quiz 进 SUMMARY/REPORT、Reading finishFlow 两分支 + finishTask 成功）时 fire-and-forget `studyRepo.report(...)`，<10s 过滤，`sessionReported` 防重，失败静默绝不影响结算；UiState 补 subject/textbook/learningMethod 三字段；source 按 taskId/selfStudy 分流 task/self_study/experience
  - Routes：HABITS/HABIT_FORM/GRADES/GRADE_FORM/STUDY_STATS 五条 + habitForm/gradeForm helper；两 FORM 家长守卫（照搬 TASK_FORM）；FriendlyError 加 habit_not_found/habit_name_taken/habit_inactive/already_checked_in_today/grade_not_found/score_exceeds_full
- **e2e（prod 全路径验证通过）**：Grade（201→403 孩子建→404 assignee 跨家庭/不存在→双视角列表→family 隔离 B 不见 A→PUT/DELETE→422）；StudySession（201/422×3/stats today=600/403/404/跨周 session_date/日期过滤）；Habits（create→list streak 注入→打卡+2 分→409 二次→logs→PUT→points balance=2 + source=checkin 流水→DELETE）；DB 4 表落库；测试数据已清理
- **0.17.0 发布（2026-08-19）**：versionCode 68→69；本地（Linux 检出点，低内存 gradle 配置）assembleDebug/assembleStaging 成功（32MB）；commit 08222bb push origin/main；ECS：pg_dump 14MB 备份 → habits.sql（enum 补 checkin）→ phase4 SQL → tar/scp 同步 → 重建 backend + landing 容器；`/version.json` 0.17.0；aapt2 验 versionCode=69；测试数据清理后 grades/study/habits 全 0
- 手动 e2e 待真机跑（孩子打卡+积分增长；家长录/改/删成绩；学完课 stats 增长；家长自学 stats 增长；中途退出不变；断网不崩）

### Phase 5（未实现）— 见 `/Users/terry/.claude/plans/silly-dazzling-valley.md`
- 容器化后端 + Alembic 迁移 + iOS/鸿蒙工程评估

## 参考工程关联

- **参考工程**：`/Users/terry/workspace/union_agent_2`
- 复用模式文件：
  - `union_agent_2/pkg/common/config.py` — Settings + `model_validator(mode="after")` 模式
  - `union_agent_2/pkg/common/database.py` — async engine + `get_db`
  - `union_agent_2/services/manager/app/core/auth.py` — JWT + bcrypt + `get_current_user`
  - `union_agent_2/apps/android/gradle/libs.versions.toml` — 版本 catalog 基线
  - `union_agent_2/apps/android/app/src/main/java/com/unionagents/enduser/di/NetworkModule.kt` — Json/OkHttp/Retrofit 提供模式
  - `union_agent_2/apps/android/app/src/main/java/com/unionagents/enduser/ui/login/LoginViewModel.kt` — StateFlow + `friendlyError` 模式

## 内存中的关联笔记

- 计划文件：`/Users/terry/.claude/plans/silly-dazzling-valley.md`（含 5 阶段完整设计与 Phase 1+2 实施步骤）
