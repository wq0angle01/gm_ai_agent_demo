# GM 运营管理后台

游戏运营管理后台（玩家 / 道具 / 邮件 / 公告 / 工单 / 审计 / 报表），Phase A 是不含任何 LLM 调用的传统后台，Phase B 逐模块接入 AI（当前进度：B0 最小对话）。

## 怎么跑

本机 `java` 和 `mvn` 都不在 PATH，用 `scripts/build.ps1`（自动定位 JDK 与 Maven）：

```powershell
powershell -File scripts\build.ps1                   # mvn test
powershell -File scripts\build.ps1 spring-boot:run   # 启动，监听 8080
powershell -File scripts\build.ps1 clean package     # 打 jar
```

启动后：

| 地址 | 用途 |
| --- | --- |
| `http://localhost:8080/api/ping` | 存活探针，返回 pong / 服务器时间 / 端口 |
| `http://localhost:8080/swagger-ui/index.html` | 接口文档，可直接点按钮发请求 |
| `POST /api/assistant/ask` | 问 AI 一句，请求体 `{"question":"..."}`，需要有效的 DeepSeek Key |
| 看库里的数据 | IDEA 的 Database 工具窗口（已配好该 DataSource），或 `scripts/DbProbe.java` |

写接口需要带操作人请求头：`X-Operator: 你的名字`。

## 数据库与演示数据

只连一个数据库：阿里云 RDS MySQL。配置文件也只有一个 `src/main/resources/application.properties`，没有 profile 拆分，也没有 H2。

- 连接信息在 `secrets.properties`（已 gitignore、不打进 jar），模板 `secrets.properties.example`，键名 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USER` / `MYSQL_PASSWORD`。
- 演示数据不在 Java 代码里，在 `src/main/resources/db/seed.sql`，由 `spring.sql.init` 每次启动执行。写法是 `INSERT ... SELECT ... WHERE NOT EXISTS`，所以重复启动是 no-op；`spring.jpa.defer-datasource-initialization=true` 让它排在 Hibernate 建表之后，否则首次启动会因表不存在而失败。不要用 `INSERT IGNORE`，它把真正的错误一起吞掉。
- 代价说清楚：**测试也连这个公网库**，断网或实例到期就跑不了 `mvn test`；所以测试用户名带随机后缀，并在 `@AfterEach` 里删掉自己建的记录，库不会积脏数据。
- **库和表必须是 `utf8mb4`**：RDS 交付的是 utf8mb3（3 字节），存不下 emoji；`ddl-auto=update` 会建表但不会改已存在表的字符集，只能手工 `ALTER DATABASE / ALTER TABLE ... CONVERT TO`。已修正并实测。
- 排障探针 `scripts/DbProbe.java`（只读）打印服务端/库/表/列字符集与 `HEX(nickname)`；`scripts/CharsetDemo.java` 用一张临时表对比严格与非严格模式的行为差异，跑完自删。

## A0 当前状态

| 项 | 结果 |
| --- | --- |
| `mvn test` | Tests run: 10, Failures: 0, Errors: 0（连的是真实 RDS） |
| `GET /api/ping` | 200，返回 pong / serverTime |
| `GET /v3/api-docs` | 4 个 path：`/api/ping`、`/api/players`、`/api/players/{id}`、`/api/assistant/ask` |
| `POST /api/players` | 201 + `Location` |
| 重复用户名 | 409 + 统一错误体 |
| 参数非法 | 400 + 3 条 `fieldErrors`（中文消息无乱码） |
| 查不存在的 id | 404 |
| 数据库 | 阿里云 RDS MySQL 8.0.36，库与表已升为 utf8mb4，表由 Hibernate `update` 创建 |
| 脚本幂等性 | 删空 player 表后重启，3 条演示数据由 seed.sql 自动重建（id 变 9/10/11），中文、金额、NULL、ENUM 均正确 |

## B0：最小 LLM 接入

只加了一条「一句进、一句出」的链路，没有工具、没有检索、没有会话记忆。

| 项 | 结果 |
| --- | --- |
| 依赖 | `spring-ai-bom:1.1.8` + `spring-ai-starter-model-deepseek`，本地仓库已存，零下载 |
| 配置 | `spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY:sk-none}`，真实值在 `secrets.properties` |
| 代码 | `AssistantController` → `AssistantService`（`ChatClient.Builder.defaultSystem().build()`） |
| `mvn test` | 10 条全绿，其中 3 条是 AI 端点，**全程不发网络请求** |
| 真实调用 | `HTTP 200`，`model=deepseek-chat`、`tookMs=935`，中文往返无损 |
| 踩到的坑 | 第一次调用拿到 401：IDEA 进程环境快照里残留一个旧 `DEEPSEEK_API_KEY`，而环境变量优先级高于 `secrets.properties`，实际发出去的是它 |

四件需要明白的事：

- **测试为什么不调模型**：调用要花钱、结果不确定、断网就红。所以只测两件不花钱的事——容器能 `build` 出 `ChatClient`（证明自动配置生效），以及 `@Valid` 在进模型之前就把空/超长问题挡成 400。真实调用交给人工。
- **Key 怎么换**：直接编辑项目根 `secrets.properties` 里的 `DEEPSEEK_API_KEY=` 一行，把控制台新签发的 Key 贴进去，保存后重启即可。不要把它贴到聊天里，也不要提交进仓库。
- **`:sk-none` 兜底的作用**：没有 Key 时应用照样启动，Phase A 的玩家管理完全不受影响，只有真调 `/api/assistant/ask` 才会失败。
- **环境变量会静默盖掉文件**：Spring 属性源优先级是「操作系统环境变量 > `spring.config.import` 的文件」。IDEA 启动时的环境快照如果被改过 Key 就没重启过 IDE，那么不管 `secrets.properties` 里写的是什么，发出去的都是环境变量那个旧值，而且不报错、只报 401。排查时看上游回显的 Key 尾四位，跟文件里的对不上就是这个原因；解法是重启 IDEA，或在启动配置里显式清掉该变量。

## 目录结构

```
src/main/java/com/gm/demo/
├── GmDemoApplication.java      启动类，组件扫描边界 = 本包及子包
├── domain/                     Player 实体、PlayerStatus 枚举
├── repository/                 Spring Data JPA 接口，无实现代码
├── service/                    业务规则，所有写入的唯一入口
├── controller/                 HTTP 适配，不含业务规则
├── dto/                        请求/响应 record，与实体隔离
├── error/                      异常 + @RestControllerAdvice + 统一错误体
└── config/                     OpenAPI 元信息

src/main/resources/
├── application.properties      唯一的配置文件，无 profile 拆分
└── db/seed.sql                 演示数据，启动时由 spring.sql.init 执行（幂等）
```

三条贯穿全程的约定：

1. 写操作失败抛异常，由全局处理器翻译成 404 / 409 / 400，**绝不返回 200 + 提示文案**。
2. `operator` 从请求头取，不接受请求体自报，也不写死常量。
3. 业务规则只在 Service 一份，Controller 不含规则（Phase B 的 AI 工具会直接注入 Service）。

## 阶段路线

**Phase A（传统后台）** A0 骨架 ✅ → A1 数据模型 → A2 玩家管理 → A3 道具 → A4 邮件 → A5 公告 → A6 工单 → A7 报表 → A8 审计 → A9 登录与权限 → A10 契约质量 → A11 测试与部署

**Phase B（接 LLM）** B0 ChatClient 装配 ✅ → B1 只读工具 → B2 写操作工具 + Guardrail + 人工确认 → B3 AI 操作审计与幂等 → B4 RAG 客服问答 → B5 结构化输出与多 Agent 工单 → B6 自然语言指标查询 → B7 观测 → B8 评测

