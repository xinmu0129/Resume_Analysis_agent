# Resume Analysis Agent

> AI 驱动的简历与岗位匹配分析系统 — 上传简历，录入 JD，智能量化匹配度，发现自身不足。

## 目录

- [选题说明](#选题说明)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [功能概览](#功能概览)
- [开发过程](#开发过程)
- [提示词产生方法](#提示词产生方法)
- [遇到的问题与解决方法](#遇到的问题与解决方法)
- [JSONL 文件生成方式](#jsonl-文件生成方式)
- [快速开始](#快速开始)
- [API 文档](#api-文档)
- [项目结构](#项目结构)

---

## 选题说明

### 背景

作为一名求职者，在秋招/春招中面对海量 JD（岗位描述），每份 JD 动辄数百字，涵盖技术栈、经验年限、学历要求、软技能等多个维度。人工逐一比对耗时耗力，且主观判断容易产生偏差——要么高估匹配度盲目投递，要么低估自身优势错失机会。

### 解决思路

引入 **AI Agent 系统**作为求职辅助工具，将"简历 ↔ JD"的匹配过程**自动化、量化、可解释**：

| 痛点 | 本系统解决方案 |
|---|---|
| JD 太多，看不过来 | 批量上传 JD，系统自动提取关键信息 |
| 人工判断不准确 | LLM 多维度评估，输出 0-100 量化得分 |
| 不知道自己的短板 | 明确列出技能差距 (gaps) 和优化建议 (suggestions) |
| PDF 简历噪声大 | 四层 Token 防护：清洗 → 结构化提取 → 缓存 → 截断 |
| 重复分析费 Token | 缓存机制，同一简历+JD 仅分析一次 |

最终实现：**上传简历 → 录入 JD → 一键获取匹配分数 + 优势 + 差距 + 建议**。

---

## 技术栈

| 层次 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.7 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0+ |
| 文档解析 | Apache Tika | 2.9.2 |
| LLM | DeepSeek API (OpenAI 兼容) | deepseek-v4-flash |
| 前端 | 原生 HTML + CSS + JavaScript | — |
| 构建 | Maven | 3.8+ |

**刻意不引入**：Spring AI、Redis、前端框架（保持轻量、降低学习门槛）。

---

## 系统架构

```
┌─────────────────────────────────────────────────┐
│                   Frontend                      │
│         static/index.html  (原生 JS)            │
│      拖拽上传 / 文本录入 / 匹配分析 / 可视化     │
└────────────────────┬────────────────────────────┘
                     │ HTTP REST
┌────────────────────┴────────────────────────────┐
│               Controller 层                     │
│  ResumeController  JobController  MatchController│
│  HealthController                               │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────┐
│                Service 层                       │
│  ┌─────────────┐  ┌──────────┐  ┌────────────┐ │
│  │ResumeService│  │JobService│  │MatchService│ │
│  │  • Tika提取 │  │• Tika提取│  │• 缓存检查  │ │
│  │  • 文本清洗 │  │• 文本清洗│  │• Token Guard│ │
│  │  • 结构提取 │  │• 结构提取│  │• LLM 调用  │ │
│  └──────┬──────┘  └────┬─────┘  └─────┬──────┘ │
└─────────┼───────────────┼──────────────┼────────┘
          │               │              │
┌─────────┴───────────────┴──────────────┴────────┐
│              工具层 (Util)                      │
│  TextCleaner (清洗)  │  TextAnalyzer (结构化提取) │
└─────────────────────────────────────────────────┘
          │               │              │
┌─────────┴───────────────┴──────────────┴────────┐
│              数据层 (MySQL)                     │
│  resume  │  job  │  match_record                │
└─────────────────────────────────────────────────┘
```

### 四层 Token 防护

```
Upload → Tika 提取
          ↓
       [Layer 1: TextCleaner]  ← 去除控制字符/页眉页脚/全角转换
          ↓
       [Layer 2: TextAnalyzer] ← 结构化提取技能/经验/学历
          ↓
       DB (raw_text + cleaned_text + skills + ...)
          ↓
Match Request
          ↓
       [Layer 3: 缓存检查]     ← 同一 resume+job 命中直接返回
          ↓
       [Layer 4: Token Guard]  ← 超长文本截断 (6000 chars)
          ↓
       LLM Prompt → DeepSeek API
```

---

## 功能概览

### 1. 简历上传
- 支持 **PDF / DOC / DOCX** 格式，最大 10MB
- Tika 自动提取文本，存入 `raw_text`
- 文本清洗去噪 → `cleaned_text`
- 结构化提取：**技能**（85+ 关键词词典）、**工作年限**、**学历**

### 2. JD 录入
- **双模式**：文件上传（PDF/DOC/DOCX/TXT） + 纯文本 JSON 提交
- 同样经过清洗 + 结构化提取
- 支持岗位名称、公司名称录入

### 3. 智能匹配分析
- 调用 DeepSeek LLM，返回结构化 JSON：
  - `matchScore`：0-100 量化得分
  - `isMatched`：得分 ≥60 判定为匹配
  - `strengths`：候选人优势列表
  - `gaps`：技能/经验差距
  - `suggestions`：针对性优化建议

### 4. 前端可视化
- 拖拽上传简历和 JD 文件
- SVG 环形进度条展示匹配分数（绿/黄/红三色）
- 匹配/不匹配标签
- 优势(绿) / 差距(红) / 建议(蓝) 分色列表

### 5. 缓存机制
- 同一 resume + job 组合仅分析一次
- 缓存永久有效，简历或 JD 更新后自动失效
- 缓存命中时 Token 消耗 = 0

---

## 开发过程

项目采用**分阶段迭代**方式，共 7 轮交互完成，每轮聚焦一个独立模块。

### Round 1：基础脚手架

**目标**：搭建 Spring Boot 项目骨架。

- 创建 Maven 项目，配置 Spring Boot 3.2.7 + Java 17 + MyBatis-Plus + MySQL
- 实现分层目录结构（controller / service / entity / mapper / common / config）
- 实现统一响应封装 `Result<T>`（code + message + data）
- 实现 `BusinessException` 运行时异常、`ErrorCode` 枚举（21 个错误码）、`GlobalExceptionHandler` 全局异常处理
- 实现 `GET /api/health` 健康检查接口
- 编写 `requirements.txt` 依赖清单

**Commit**: `1d08751`

### Round 2：简历上传模块

**目标**：实现简历文件上传与 PDF/DOCX 文本提取。

- 创建 `resume` 表，`init.sql` 初始化脚本
- 引入 Apache Tika 2.9.2（`tika-core` + `tika-parsers-standard-package`）
- 实现 `POST /api/resume/upload` 接口
- 文件落盘至 `./uploads/resume/`，Tika 提取文本入库
- 返回 `id / fileName / fileType / fileSize / status / createTime`

**Commit**: `09ac27e`

### Round 3：JD 管理模块

**目标**：实现岗位 JD 的双模式录入。

- 创建 `job` 表
- `POST /api/job/upload`：上传 JD 文件，Tika 提取文本入库
- `POST /api/job/text`：提交 JSON（title + company + content）
- 提取公共校验逻辑，支持 PDF/DOC/DOCX/TXT

**Commit**: `1fddecd`

### Round 4：匹配分析 Agent

**目标**：接入 LLM 实现简历与 JD 的匹配分析。

- 实现 `DeepSeekClient`：OpenAI 兼容 API 适配器，使用 `SimpleClientHttpRequestFactory` 配置超时（连接 10s，读取 120s）
- 创建 `match_record` 表
- 实现 `POST /api/match/analyze` 接口
- 构建 HR 专家 System Prompt，要求 LLM 输出结构化 JSON
- 解析 LLM 响应，提取 matchScore / isMatched / strengths / gaps / suggestions

**Commit**: `ccc4d28`

### Round 5：前端单页应用

**目标**：提供可视化操作界面。

- 纯静态 HTML + CSS + JS，零框架依赖
- 三步骤卡片布局：上传简历 → 录入 JD → 匹配分析
- 拖拽上传、文本/文件双模式 JD 录入
- SVG 环形进度条动画（stroke-dashoffset 过渡）
- Toast 通知、加载状态、错误提示
- 响应式设计（860px / 660px 断点）

**Commit**: `f3a3e3f`

### Round 6：Token 优化

**目标**：解决 PDF 噪声导致 Token 浪费的问题。

此轮先**不修改代码**，进行完整的问题分析和方案设计：

1. **问题溯源**：跟踪 Tika 提取 → 存储 → LLM 调用全链路，确认 Token 浪费的三个根因
2. **方案设计**：提出四层防护架构
3. **用户确认**：询问清洗策略（保守/激进）、缓存策略（永久/过期）、是否结构化提取
4. **代码实现**：经过用户确认后实施

**实施内容**：
- `TextCleaner`：去除 C0 控制字符、全角转半角、移除页眉页脚行、合并连续空行
- `TextAnalyzer`：85+ 技能关键词词典 + 正则匹配经验/学历
- `Resume` / `Job` 实体新增 `cleanedText`、`skills`、`experienceYears`、`education` 字段
- `MatchServiceImpl`：缓存查询（LambdaQueryWrapper）+ `isCacheValid()` + `pickText()` + `truncateIfNeeded()`
- `application.yml` 新增 `match.prompt.max-input-chars: 6000`
- Token 估算减少 **55-70%**

**Commit**: `48f96d9`

### Round 7：Bug 修复

**目标**：修复简历上传接口报错和 init.sql 语法兼容性问题。

- **Bug 1**：`Unknown column 'cleaned_text'` — 数据库表缺少新列
  - 根因：`init.sql` 中 ALTER TABLE 使用了 MySQL 8.0 不支持的 `ADD COLUMN IF NOT EXISTS` 语法
  - 修复：替换为 `INFORMATION_SCHEMA` + `PREPARE/EXECUTE` 预处理语句实现安全幂等迁移
- **Bug 2**：执行 `init.sql` 报语法错误
  - 同一根因，MySQL 不支持 `IF NOT EXISTS` 修饰列添加

**Commit**: `9a24ff1`

---

## 提示词产生方法

本项目的所有开发提示词遵循**分阶段、角色化、设边界**的原则，由人工编写。

### 方法论

```
┌─────────────────────────────────────────────┐
│  1. 确定 Role（角色）                        │
│     "你是一名资深 Java 后端工程师..."         │
├─────────────────────────────────────────────┤
│  2. 定义任务范围（Scope）                    │
│     "本轮只做基础脚手架"                     │
├─────────────────────────────────────────────┤
│  3. 列出具体需求（Requirements）             │
│     编号列表，每项可验证、可测试              │
├─────────────────────────────────────────────┤
│  4. 设定约束边界（Constraints）              │
│     "不要自行安装依赖"                       │
│     "暂时不要引入 Spring AI、Redis"          │
│     "这次阶段不许修改代码"                   │
├─────────────────────────────────────────────┤
│  5. 预留决策空间                             │
│     "有不懂的询问我，得到我的指令再开始"      │
└─────────────────────────────────────────────┘
```

### 实践要点

| 原则 | 说明 | 示例 |
|---|---|---|
| **分阶段** | 每轮只聚焦一个独立模块，避免上下文过长 | Round 1 脚手架 → Round 2 简历 → Round 3 JD → ... |
| **角色化** | 在 System Prompt 中明确 Agent 角色 | "你是一位资深 HR 和职业规划专家" |
| **设边界** | 用否定句式明确禁止事项，防止 AI 自由发挥引入不必要依赖 | "不要引入 Spring AI、Redis、文件解析库" |
| **可验证** | 每轮需求可独立测试验收 | "mvn compile 必须通过"、"返回 id/fileName/fileType/status/createTime" |
| **留退路** | 关键决策前先分析方案，用户确认后再改代码 | Round 6："这次阶段不许修改代码，旨在找出问题解决的办法" |

### 为何不分阶段提供提示词而非一次性全给

1. **上下文窗口限制**：一次性给所有需求会超过有效上下文，导致 AI 遗漏细节
2. **迭代纠正**：每轮验证后可及时纠正偏差，而非最后才发现方向错误
3. **依赖关系**：后续轮次依赖前轮产物（如 Match 模块依赖 Resume 和 Job 的 entity），分阶段自然形成依赖链
4. **可回溯**：每轮一个 Git commit，方便定位问题和回滚

---

## 遇到的问题与解决方法

### 1. Tika 依赖编译失败

**现象**：`mvn compile` 报 Tika 相关类找不到。

**根因**：阿里云 Maven 镜像未正确传递 `tika-parsers-standard-package` 的 `tika-core` 传递性依赖。

**解决**：在 `pom.xml` 中将 `tika-core:2.9.2` 添加为显式依赖，与 `tika-parsers-standard-package` 并列声明。

### 2. RestTemplateBuilder 方法不存在

**现象**：`RestTemplateBuilder.connectTimeout(Duration)` 编译报错。

**根因**：Spring Boot 3.2.7 的 `RestTemplateBuilder` 不支持 `Duration` 参数版本的 `connectTimeout` 方法。

**解决**：改用 `SimpleClientHttpRequestFactory` 手动构建 `RestTemplate`，使用 `setConnectTimeout(int)` 和 `setReadTimeout(int)`（毫秒单位）。

### 3. MySQL 服务无法启动

**现象**：`net start MySQL` 报"系统错误 5: 拒绝访问"。

**根因**：Windows 安全策略要求以管理员权限启动系统服务。

**解决**：以管理员身份打开终端，再执行 `net start MySQL`。

### 4. init.sql 语法错误

**现象**：`ERROR 1064: near 'IF NOT EXISTS cleaned_text LONGTEXT NULL COMMENT' at line 2`。

**根因**：MySQL 8.0 **不支持** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 语法（该语法为 MariaDB 和 PostgreSQL 特性）。

**解决**：
- **短期**：直接执行无 `IF NOT EXISTS` 的 ALTER TABLE 语句
- **长期**：`init.sql` 改为使用 `INFORMATION_SCHEMA` 检查列是否存在 + `PREPARE/EXECUTE` 预处理语句，实现真正的幂等迁移

```sql
SET @sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'resume_analysis' AND TABLE_NAME = 'resume'
    AND COLUMN_NAME = 'cleaned_text') = 0,
    'ALTER TABLE resume ADD COLUMN cleaned_text LONGTEXT NULL COMMENT ''清洗后文本'' AFTER raw_text',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

### 5. PDF 文本噪声导致 Token 浪费

**现象**：同一 PDF 简历每次分析消耗大量 Token，且存在缓存穿透（无缓存机制）。

**解决**：设计并实施**四层 Token 防护架构**（详见上文架构图），Token 估算减少 55-70%。

### 6. Python 3 在 Windows 上的 exit code 49

**现象**：Git Bash 中执行 `python3` 报 exit code 49。

**根因**：Windows Store 版本的 Python 3 stub (`WindowsApps/python3.exe`) 在 Git Bash 中行为异常。

**解决**：使用 `python` 命令（指向 `Python312/python.exe`）替代 `python3`。

---

## JSONL 文件生成方式

[conversation_log.jsonl](conversation_log.jsonl) 记录了本项目完整的 7 轮交互数据。

### 字段说明

| 字段 | 类型 | 来源 | 说明 |
|---|---|---|---|
| `round_id` | number | `对话数据.txt` | 轮次序号 (1-7) |
| `prompt_content` | string | `对话数据.txt` | 该轮输入的完整自然语言指令 |
| `modify_diff` | string | `git diff/show` | 该轮代码变更的完整 unified diff |
| `commit_hash` | string | `git log` | 该轮对应的完整 commit SHA |
| `modify_time` | string | `git log --format=%ai` | 提交时间 (YYYY-MM-DD HH:MM:SS) |
| `agent_type` | string | 固定值 | `"Claude Code"` |
| `dev_language` | string | 人为标注 | 该轮使用的编程语言 |

### 生成流程

```
1. 从 对话数据.txt 提取每轮 round_id 和 prompt_content
       ↓
2. 手动关联每轮到对应的 Git commit
       ↓
3. git diff/show 获取每轮的完整 unified diff
       ↓
4. git log --format=%ai 获取每轮提交时间
       ↓
5. Python json.dumps() 进行 JSON 序列化
   确保 ensure_ascii=False（保留中文）
       ↓
6. 输出为 conversation_log.jsonl（每行一个 JSON 对象）
```

### 示例

```json
{"round_id":4,"prompt_content":"实现简历与 JD 匹配的 Agent 核心模块：\n\n1. 引入springai或Deepseek适配\n2. 新建 match 匹配相关：entity(MatchRecord)、service、controller\n3. 核心接口 POST /api/match/analyze\n   - 入参：resumeId、jobId\n   - Agent 流程：读取简历和JD文本之后LLM分析匹配度，最后输出结果\n4. 返回结构：\n   - matchScore (0-100)\n   - isMatched (是否匹配)\n   - strengths (优势列表)\n   - gaps (差距列表)\n   - suggestions (优化建议列表)\n5. 结果存入 match_record 表，init.sql 补充 DDL\n6. application.yml 配置 LLM API Key，用环境变量","modify_diff":"diff --git a/.gitignore b/.gitignore\nindex 438b0d8..9aa87db 100644\n--- a/.gitignore\n+++ b/.gitignore\n@@ -38,4 +38,5 @@ logs/\n .env\n .env.local\n \n-application-dev.yml\n\\ No newline at end of file\n+application-dev.yml\n+application.yml\n\\ No newline at end of file\ndiff --git a/sql/init.sql b/sql/init.sql\n...","commit_hash":"ccc4d285aecc08d169856bca660fe28d50360f3a","modify_time":"2026-07-09 23:37:49","agent_type":"Claude Code","dev_language":"Java"}
```

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 1. 初始化数据库

```bash
mysql -u root -p < sql/init.sql
```

### 2. 配置 LLM API Key

编辑 `src/main/resources/application.yml`：

```yaml
llm:
  api-key: "your-deepseek-api-key"
  base-url: "https://api.deepseek.com"
  model: "deepseek-v4-flash"
```

### 3. 编译启动

```bash
mvn clean compile spring-boot:run
```

### 4. 验证

```bash
# 健康检查
curl http://localhost:8080/api/health

# 打开前端
# 浏览器访问 http://localhost:8080/
```

### 5. 完整测试流程

```bash
# 上传简历
curl -F "file=@your_resume.pdf" http://localhost:8080/api/resume/upload

# 录入 JD（文本方式）
curl -X POST http://localhost:8080/api/job/text \
  -H "Content-Type: application/json" \
  -d '{"title":"Java后端开发","company":"ABC科技","content":"岗位要求：..."}'

# 匹配分析
curl -X POST http://localhost:8080/api/match/analyze \
  -H "Content-Type: application/json" \
  -d '{"resumeId":1,"jobId":1}'

# 验证缓存（第二次调用不消耗 Token）
curl -X POST http://localhost:8080/api/match/analyze \
  -H "Content-Type: application/json" \
  -d '{"resumeId":1,"jobId":1}'
```

---

## API 文档

### 健康检查

```
GET /api/health
→ {"code":200,"message":"OK","data":"OK"}
```

### 简历上传

```
POST /api/resume/upload
Content-Type: multipart/form-data
  file: (binary, pdf/doc/docx, max 10MB)

→ {
    "code": 200,
    "data": {
      "id": 1,
      "fileName": "简历.pdf",
      "fileType": "pdf",
      "fileSize": 207741,
      "status": "PARSED",
      "createTime": "2026-07-10T11:18:46"
    }
  }
```

### JD 录入

**文件上传：**
```
POST /api/job/upload
Content-Type: multipart/form-data
  file: (binary, pdf/doc/docx/txt)
```

**文本提交：**
```
POST /api/job/text
Content-Type: application/json
{
  "title": "Java开发工程师",
  "company": "ABC科技",
  "content": "岗位要求：..."
}

→ {
    "code": 200,
    "data": {
      "id": 1,
      "title": "Java开发工程师",
      "company": "ABC科技",
      "createTime": "2026-07-10T11:19:01"
    }
  }
```

### 匹配分析

```
POST /api/match/analyze
Content-Type: application/json
{
  "resumeId": 1,
  "jobId": 1
}

→ {
    "code": 200,
    "data": {
      "id": 1,
      "resumeId": 1,
      "jobId": 1,
      "matchScore": 92,
      "isMatched": true,
      "strengths": ["技术栈高度匹配...", "..."],
      "gaps": ["缺少Kafka经验..."],
      "suggestions": ["建议补充消息队列项目...", "..."],
      "createTime": "2026-07-10T11:19:17"
    }
  }
```

---

## 项目结构

```
resume-analysis-agent/
├── pom.xml                          # Maven 配置 (Spring Boot 3.2.7)
├── requirements.txt                 # 手动安装依赖清单
├── README.md                        # 本文件
├── test-job.json                    # JD 测试数据
│
├── sql/
│   └── init.sql                     # 数据库初始化 (resume + job + match_record)
│
├── resumeByme/                      # 测试用简历 PDF（不纳入版本控制）
│   ├── 示例Agent.pdf
│   └── 示例 后端+Agent.pdf
│
└── src/main/
    ├── java/com/resume/agent/
    │   ├── ResumeAnalysisAgentApplication.java   # 启动类
    │   ├── common/
    │   │   ├── Result.java                       # 统一响应
    │   │   ├── ErrorCode.java                    # 错误码枚举 (21个)
    │   │   ├── BusinessException.java            # 业务异常
    │   │   └── GlobalExceptionHandler.java       # 全局异常处理
    │   ├── config/
    │   │   └── DeepSeekClient.java               # LLM API 适配器
    │   ├── controller/
    │   │   ├── HealthController.java             # GET /api/health
    │   │   ├── ResumeController.java             # POST /api/resume/upload
    │   │   ├── JobController.java                # POST /api/job/upload, /api/job/text
    │   │   └── MatchController.java              # POST /api/match/analyze
    │   ├── entity/
    │   │   ├── Resume.java                       # resume 表实体
    │   │   ├── Job.java                          # job 表实体
    │   │   └── MatchRecord.java                  # match_record 表实体
    │   ├── mapper/
    │   │   ├── ResumeMapper.java                 # MyBatis-Plus BaseMapper
    │   │   ├── JobMapper.java
    │   │   └── MatchRecordMapper.java
    │   ├── model/
    │   │   ├── dto/
    │   │   │   └── JobTextRequest.java           # JD 文本提交请求体
    │   │   └── vo/
    │   │       ├── ResumeUploadVO.java            # 简历上传响应
    │   │       ├── JobUploadVO.java               # JD 录入响应
    │   │       └── MatchResultVO.java             # 匹配结果响应
    │   ├── service/
    │   │   ├── ResumeService.java                # 简历服务接口
    │   │   ├── JobService.java                   # JD 服务接口
    │   │   ├── MatchService.java                 # 匹配服务接口
    │   │   └── impl/
    │   │       ├── ResumeServiceImpl.java         # 简历上传 + 清洗 + 结构提取
    │   │       ├── JobServiceImpl.java            # JD 录入 + 清洗 + 结构提取
    │   │       └── MatchServiceImpl.java          # 缓存 + Token Guard + LLM 匹配
    │   └── util/
    │       ├── TextCleaner.java                   # Layer 1: 文本清洗
    │       └── TextAnalyzer.java                  # Layer 2: 结构化提取
    │
    └── resources/
        ├── application-template.yml                # 主配置模板 (需复制为 application.yml)
        ├── application-dev-template.yml            # 开发环境模板 (需复制为 application-dev.yml)
        ├── application-prod.yml                   # 生产环境 (环境变量)
        └── static/
            └── index.html                         # 前端单页应用
```
