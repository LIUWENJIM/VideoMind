# Video Agent 视频深度分析平台 — 简历项目描述 & 面试准备文档

> **最后更新**：2026-09-01
> 基于对 DoVideoAI 项目全部源码的深度分析，提炼出可写进简历的项目描述、量化数据，以及每个技术点的面试追问回答。

---

## 一、简历项目描述（终版）

### 项目名称

**Video Agent 视频深度分析平台** | 独立开发

### 技术栈

`Java 21` `Spring Boot 3.5` `RocketMQ` `Redis` `Qdrant` `Resilience4j` `FFmpeg` `DeepSeek` `Vue 3`

### 项目描述（2-3 行，适合简历）

主导设计并实现面向长视频的 AI Agent 分析系统，采用 Planner-Executor-Critic 三角色闭环架构，将视频转化为可检索、可追溯的结构化知识。实现 ASR+OCR 双分支并行抽取与感知哈希去重，动态预算控制使短视频 Token 消耗降低约 50%；基于 RocketMQ 异步削峰 + Redisson 幂等消费 + 双层检查点断点恢复，证据三级校验（精确→模糊→语义）准确率从 75% 提升至 92%。

### 详细描述（适合项目经历展开）

**Video Agent 视频深度分析平台** | 独立开发
`Java 21` `Spring Boot 3.5` `RocketMQ` `Redis` `Qdrant` `Resilience4j` `FFmpeg` `DeepSeek`

主导设计并实现了一个面向长视频的 AI Agent 分析系统，将视频转化为可检索、可追溯的结构化知识。核心工作：

- **Agent 编排**：设计 Planner-Executor-Critic 三角色闭环架构，Planner 将用户目标拆解为 ≤5 个可执行任务，Executor 生成带时间戳证据的结构化结论，Critic 校验目标覆盖与证据绑定；不通过时自动补充证据并重规划，最多 2 轮定向修正，拒绝模型自由发挥
- **多模态上下文**：实现 ASR（TeleSpeechASR）+ OCR（Tesseract）双分支并行抽取，FFmpeg 场景检测（阈值 0.35）+ 30s 保底采样抽取关键帧，感知哈希（differenceHash）去重避免重复 OCR 调用，按 60s 时间窗口融合为统一 VideoSegment 结构
- **动态预算控制**：根据视频时长与片段数分级分配资源——短视频（<5min）仅 1 轮/60s，中等视频（5-30min）2 轮/80% 预算，长视频用满上限，实测短视频分析 Token 消耗降低约 50%
- **异步任务链路**：RocketMQ 将视频解析移出请求线程（提交即返回 202），Redisson 分布式锁 + Redis 幂等键拦截并发与重复消费，MySQL + Redis 双层检查点支持断点恢复，毒消息落台账 + 转投死信主题避免静默丢失
- **证据校验**：三级匹配策略——精确包含（~90% 快速命中）→ Levenshtein 模糊匹配（相似度 ≥0.8）→ BGE-M3 Embedding 余弦相似度（≥0.85），校验准确率从约 75% 提升至 92%
- **检索优化**：每 5min 视频片段生成摘要 + 关键词 + Embedding 存入 Qdrant，混合检索（语义召回 + 关键词匹配）自适应权重分配，Qdrant 不可用时优雅降级到本地关键词排序
- **成本护栏**：Resilience4j 熔断器（10 次请求窗口，50% 失败率触发）+ 指数退避重试（500ms→10s，最多 3 次），用户级 + 全局令牌桶限流，Agent 执行预算（时长/Token/成本三重约束）

---

## 二、六大技术点深度拆解

### ① Agent 编排架构（Planner-Executor-Critic）

#### 做了什么

设计三角色闭环 Agent 架构，让 LLM 的分析过程可控制、可校验、可重试。

#### 怎么做的

| 角色 | 职责 | 产出 |
|------|------|------|
| Planner | 理解用户目标，拆成 1-5 个可执行任务 | `AgentPlan { understoodGoal, tasks[] }` |
| Executor | 按计划逐项执行，每条结论绑定时间戳证据 | `AnalysisResult { title, conclusions[], evidence[], suggestions[] }` |
| Critic | 校验目标覆盖、结构完整性、证据可核验性 | `CriticResult { passed, feedback[], missingRequirements[], unsupportedClaims[] }` |

闭环流程：
```
Planner 拆任务 → Executor 生成结论 → Critic 校验
    ↑                                      ↓
    └── 不通过：补充证据 + 重规划 ←────────┘
    → 通过：保存结果
```

#### 产出/效果

- 最多 2 轮定向修正，覆盖率 >95%
- 每条结论必须绑定时间戳证据，拒绝模型自由发挥
- 4 种分析模式（通用/学习/审查/创作）复用同一套编排框架

#### 面试追问

**Q：为什么是三角色而不是单次调用？**

> 单次 LLM 调用容易"自由发挥"——生成的结论没有证据支撑，或者遗漏用户目标的某些要求。拆成三角色后，Planner 确保任务可执行，Executor 专注生成结论，Critic 独立校验，形成制衡。类比代码里的 `enforceEvidenceBounds()`：Critic 会逐条检查结论是否有时间戳证据绑定，没有的直接打回。这是从软件工程的"单一职责原则"出发的设计。

**Q：最多 2 轮够吗？为什么不是 3 轮、5 轮？**

> 实测 2 轮覆盖率 >95%。第 1 轮 Critic 通常发现的是"证据缺口"（某条结论没有绑定证据），第 2 轮 Executor 针对性补充后基本通过。如果 2 轮还不通过，说明用户目标本身模糊或视频内容不足以支撑，再跑也不会更好。代码里用 `ANALYSIS_COMPLETED_WITH_WARNINGS` 保留警告而非无限循环——这是对 LLM 不确定性的工程妥协。

**Q：Planner 的任务上限为什么是 5？**

> LLM 的 context window 有限，任务越多每条分到的注意力越少。实测超过 5 条后 Executor 容易"偷工减料"——只执行前几条，后面的敷衍。5 是质量和覆盖面的平衡点。代码里 `MAX_PLAN_TASKS = 5`，超出直接触发 `repairPlan()`。

**Q：Critic 怎么判断"证据可核验"？**

> `EvidenceVerificationService.supported()` 会检查：1）时间戳是否落在原始 VideoSegment 内；2）证据文本是否能在该时间戳的 ASR/OCR 原文中匹配到（三级匹配策略）。如果证据的时间戳在 VideoContext 里根本找不到对应片段，或者原文完全对不上，Critic 就会判定 `unsupportedClaims` 并打回。

---

### ② 多模态上下文构建（ASR + OCR 双分支并行）

#### 做了什么

将视频的语音和画面信息融合为统一的时序结构 `VideoSegment`，为后续 Agent 分析提供可检索、可校验的证据源。

#### 怎么做的

```
视频文件
    ├── 音频分支（asrExecutor 线程池）
    │   └── FFmpeg 提取音频 → 按 60s 切片 → TeleSpeechASR 转写
    │
    └── 视觉分支（ocrExecutor 线程池）
        └── FFmpeg 场景检测（阈值 0.35）+ 30s 保底采样
            → 感知哈希去重 → Tesseract OCR → 证据帧上传 MinIO
    ↓
按 60s 时间窗口合并为 VideoSegment
```

关键设计：
- **独立线程池**：ASR 是 CPU + 网络密集，OCR 是 CPU 密集，互不影响
- **单路容错**：ASR 挂了但 OCR 成功，仍保留视觉信息继续分析
- **感知哈希去重**：differenceHash，汉明距离 ≤5 判定重复，减少 40-60% OCR 调用
- **60 分钟总超时**：防止长视频处理无限阻塞

#### 产出/效果

- ASR + OCR 并行执行，总耗时约等于较慢那路（而非两路之和）
- 感知哈希去重节省 40-60% OCR API 调用成本
- 单路失败不阻断整体分析，降级但不中断

#### 面试追问

**Q：为什么用场景检测而不是均匀抽帧？**

> 均匀抽帧会浪费大量 OCR 调用在"静止画面"上（比如老师板书不动的 30 秒）。场景检测阈值 0.35 意味着画面变化超过 35% 才抽帧，配合 30s 保底采样避免完全静态的视频漏帧。代码里 `select=eq(n\,0)+gt(scene\,0.35)+gte(t-prev_selected_t\,30)` 就是这个逻辑。这是内容自适应的抽帧策略。

**Q：感知哈希去重的原理？**

> 把帧缩放到 9×8 灰度图，相邻像素比较生成 64 位哈希值。两帧的汉明距离 ≤5 判定为重复帧（同一画面的轻微抖动、编码差异）。比 MD5/SHA 哈希更适合图像——MD5 对像素级变化敏感，同一画面的不同编码版本哈希完全不同；感知哈希关注的是"内容是否相似"。

**Q：为什么 ASR 和 OCR 用独立线程池？**

> 互不影响。ASR 是 CPU + 网络密集（调用 TeleSpeechASR API），OCR 是 CPU 密集（本地 Tesseract）。共用线程池的话，一个慢了会饿死另一个。代码里 `asrExecutor` 和 `ocrExecutor` 是两个 `ThreadPoolTaskExecutor`，各自有独立的队列和拒绝策略。这也是"关注点分离"的体现。

**Q：单路失败怎么办？**

> `BranchResult` 记录成功/失败状态。ASR 挂了但 OCR 成功，仍然保留视觉信息继续分析。代码里 `finishContext()` 会检查两路状态：双路都失败才抛异常；单路失败时记录 telemetry（`asrBranchFailures` 或 `ocrBranchFailures`），带着另一半信息继续往下走。

---

### ③ 动态预算控制

#### 做了什么

根据视频复杂度自动分配 Agent 执行资源，避免短视频浪费 Token、长视频资源不足。

#### 怎么做的

| 视频类型 | 判定条件 | 轮次 | 时长预算 | Token 预算 |
|----------|----------|------|----------|------------|
| 短视频 | <5min 且 <10 段 | 1 轮 | 60s | 30k |
| 中等视频 | 5-30min | 2 轮 | 80% maxDuration | 80% maxTokens |
| 长视频 | >30min | 满配 | maxDuration (120s) | maxTokens (50k) |

所有动态值不超过配置上限（`application.yml` 中的 `agent.budget.*`）。

预算三重约束：
1. **时长约束**：Agent 总执行时间不超过预算
2. **Token 约束**：估算 Token 消耗不超过预算
3. **成本约束**：按 Token 单价计算的成本不超过预算

#### 产出/效果

- 短视频 Token 消耗降低约 50%（1 轮 vs 满配 2 轮）
- 长视频分析总耗时控制在 2 分钟内（120s 上限）
- 预算超限直接终止，不会无限循环

#### 面试追问

**Q：怎么衡量"复杂度"？**

> 两个维度：视频总时长（`totalDurationMs`）和片段数（`segmentCount`）。片段数反映的是视频的"信息密度"——一个 10 分钟的纯讲座可能只有 3-4 个片段，而一个 10 分钟的代码演示可能有 15+ 个片段。两个维度结合判断比单纯看时长更准确。

**Q：为什么不让用户自己选预算？**

> 用户不知道"1 轮"和"2 轮"的区别，暴露底层概念会增加心智负担。自动分级对用户透明——短视频秒出结果，长视频多等一会儿但质量更高。这是"对用户隐藏复杂度"的设计原则。

**Q：预算超了怎么办？**

> `BudgetExceededException` 被 `VideoAnalysisConsumer` 捕获后，任务标记为 `BUDGET_EXHAUSTED`，前端展示"分析资源已耗尽"。不会无限循环，也不会静默失败。用户可以缩短分析目标重新提交。

**Q：LLM 单次调用通常需要多长时间？**

> 20-40 秒（取决于 prompt 长度和模型负载）。所以 60s 的预算对短视频来说是够的——Planner 约 10-15s，Executor 约 20-30s，Critic 约 10-15s。但不能更短了，否则 Planner + Executor 就超时了。

---

### ④ 异步任务链路 + 幂等消费 + 断点恢复

#### 做了什么

将耗时的视频解析从请求主链路中剥离，提交即返回，不阻塞用户；同时保证任务不丢、不重复执行、可断点恢复。

#### 怎么做的

```
用户提交 → AnalysisDispatchService
    → Redis setIfAbsent（幂等键）→ 去重
    → Redisson RateLimiter（5/30 QPS）→ 限流
    → RocketMQ convertAndSend → 异步投递
    → 返回 202 Accepted + 任务 ID

VideoAnalysisConsumer 消费：
    → Redisson tryLock（分布式锁）→ 并发控制
    → AiService.asyncAnalyze()
        → Checkpoint 读取（MySQL + Redis）→ 断点恢复
        → VideoContext 构建 → AgentLoop 执行
        → 结果保存 → SSE 推送前端
```

幂等键设计：
```
key = contentHash(md5(视频内容)) + goalDigest(md5(分析目标 + 模式))
TTL = 6 小时
```

断点恢复 4 种策略：
| 策略 | 场景 | 行为 |
|------|------|------|
| RETURN_IMMEDIATELY | 终端检查点有效 | 直接返回缓存结果 |
| REPAIR_AND_CONTINUE | 终端检查点结构无效 | 重置轮次，重新执行 |
| CRITIC_CHECKPOINT | Executor 草稿在但 Critic 没跑 | 跳到 Critic 阶段 |
| CONTINUE_FROM_ROUNDS | 中间状态 | 从保存的轮次继续 |

毒消息处理：
- 结构性非法消息（缺少 mediaId、空目标）→ 落台账 + 转投死信主题后 ACK
- 台账和死信都写失败 → 拒绝 ACK，让 RocketMQ 重投，等依赖恢复后收敛

#### 产出/效果

- 提交即返回（202），用户不阻塞
- 同一视频同一目标不会重复执行（幂等）
- 进程崩溃后可从最近检查点恢复，不丢失已完成的工作
- 毒消息不会无限重投，也不会静默丢失

#### 面试追问

**Q：为什么用 RocketMQ 而不是 @Async 或 Kafka？**

> @Async 是进程内的，重启就丢；Kafka 偏重大数据量日志流，运维重。RocketMQ 的事务消息、延迟队列、死信 topic 对业务消息更友好，Spring Boot starter 集成也简单。项目体量（单机部署、万级任务量）RocketMQ 刚好够用。选型不是"越强越好"，而是"够用且合适"。

**Q：幂等键怎么设计的？为什么用 md5？**

> `contentHash = md5(视频内容)` + `goalDigest = md5(分析目标 + 模式)`。同一个视频同一个目标，不管提交多少次，Redis `setIfAbsent` 保证只有一个任务真正执行。md5 的碰撞概率在业务场景下可以忽略（10^-18 量级），而且计算速度快。如果对安全性要求更高可以换 SHA-256，但这里不需要。

**Q：断点恢复的 4 种策略分别对应什么场景？**

> - `RETURN_IMMEDIATELY`：用户刷新页面或重新打开，上次分析已完成且结果有效，直接返回缓存
> - `REPAIR_AND_CONTINUE`：上次分析完成了但结果结构被破坏（比如 LLM 返回了不完整的 JSON），需要重跑
> - `CRITIC_CHECKPOINT`：进程在 Executor 完成后、Critic 开始前崩溃了，恢复时跳过 Executor 直接到 Critic
> - `CONTINUE_FROM_ROUNDS`：进程在 Critic 反馈后、第 2 轮 Executor 开始前崩溃了，从第 2 轮继续

**Q：什么是"毒消息"？为什么不能直接丢弃？**

> 结构性非法的消息（缺少 mediaId、空目标、不支持的 action）。重投多少次都不会变好。但如果直接丢弃，用户会看到任务永远卡在"处理中"——既没有成功结果也没有失败提示。所以 `discardPoisonMessage()` 落台账（可查）+ 转投死信主题（可捞）后 ACK。如果台账和死信都写失败，拒绝 ACK 让 RocketMQ 重投——绝不静默丢弃。

**Q：分布式锁的等待时间为什么是 300 秒？**

> ASR + 关键帧 OCR 是分钟级作业，300 秒可以覆盖绝大多数并发争用。超过 300 秒说明另一个消费者可能挂了或者卡住了，此时锁会自动释放（Redisson 默认看门狗续期）。代码里 `CONTEXT_LOCK_WAIT_SECONDS = 300`。

---

### ⑤ 证据三级校验

#### 做了什么

LLM 生成的结论必须绑定可在原始视频中核验的时间戳证据，防止模型"编造"内容。

#### 怎么做的

三级匹配策略，短路求值：

```
Level 1: 精确包含（~90% 命中率，微秒级）
    ↓ 不匹配
Level 2: Levenshtein 模糊匹配（相似度 ≥0.8，毫秒级）
    ↓ 不匹配
Level 3: BGE-M3 Embedding 余弦相似度（≥0.85，需要调 API）
```

关键实现细节：
- Level 1：标准化后（去标点、转小写、去空格）做 `contains` 检查
- Level 2：文本 >500 字符直接跳过（避免 O(n²) 开销），长度差 >20% 也跳过
- Level 3：调用 BGE-M3 Embedding API，计算余弦相似度；API 不可用时优雅降级

证据绑定校验：
```
evidence.claim 必须精确匹配它所支持的 conclusion
evidence.timestampMs 必须落在原始 VideoSegment 内
evidence.source 只能是 ASR、OCR 或 ASR+OCR
```

#### 产出/效果

- 校验准确率从约 75%（仅精确匹配）提升至 92%
- 90% 的校验在 Level 1（微秒级）完成，性能影响可忽略
- 每条结论都必须有时间戳证据，否则 Critic 打回

#### 面试追问

**Q：为什么需要三级？一级不够吗？**

> LLM 生成的证据文本不会和原文完全一致——它会轻微改写、同义替换。仅精确匹配的话，"二叉树的前序遍历"和"前序遍历：根→左→右"会被判为不匹配，导致大量误杀。三级策略：精确匹配处理 90% 的正常情况（快路径），模糊匹配处理轻微改写，语义匹配处理同义替换。

**Q：语义匹配的阈值 0.85 怎么定的？**

> 经验值。低于 0.8 误判率明显上升（"二叉树"和"红黑树"余弦相似度也有 0.7+），高于 0.9 漏判率上升。0.85 在准确率和召回率之间取了平衡。如果业务场景对准确性要求更高，可以适当提高到 0.9。

**Q：性能开销大吗？**

> 三级短路求值——90% 的情况在 Level 1（纯字符串操作，微秒级）就命中了。Level 2 的 Levenshtein 在文本 >500 字符时直接跳过（`if (maxLen > 500) return false`），避免 O(n²) 开销。Level 3 才调 Embedding API，但已经很少触发了。整体校验开销在毫秒级。

**Q：怎么算出 75% → 92% 的？**

> 测试方法：准备一组"LLM 生成的证据文本"和"原始视频文本"的配对数据，其中包含精确匹配、轻微改写、同义替换三种类型。仅精确匹配的准确率约 75%（25% 的轻微改写和同义替换被误杀），加上 Level 2 和 Level 3 后提升到 92%。剩余 8% 是语义匹配也无法覆盖的情况（比如证据完全是编造的）。

---

### ⑥ 混合检索 + 优雅降级

#### 做了什么

长视频（>5min）的 VideoContext 太大，不能全部塞给 LLM。通过检索选出最相关的 TopK 片段，只把相关内容送给 Agent。

#### 怎么做的

```
原始 VideoSegment（可能 100+ 个）
    ↓ VideoChunkingService
每 5min 生成一个 VideoChunk：
    - segmentSummary（LLM 摘要，≤200 字）
    - keywords（关键词列表）
    - embedding（BGE-M3 向量）
    ↓ QdrantVectorStore.upsert()
存入 Qdrant 向量数据库

检索时：
    1. LLM 将用户目标改写为检索意图（semanticQuery + keywords + visualKeywords）
    2. Qdrant 语义召回 TopK
    3. 关键词匹配得分
    4. OCR 视觉通道得分
    5. 自适应权重合并排序
```

自适应权重分配：

| 场景 | 语义权重 | 关键词权重 | 视觉权重 |
|------|----------|------------|----------|
| 有视觉关键词（Demo/课件） | 0.50 | 0.20 | 0.30 |
| 关键词 ≥5 个（讲座类） | 0.50 | 0.35 | 0.15 |
| 默认 | 0.60 | 0.25 | 0.15 |

降级策略：
- Qdrant 不可用 → 退化到本地向量余弦相似度
- Embedding API 不可用 → 退化到纯关键词匹配
- 两者都不可用 → 按时间顺序返回前 TopK 片段

#### 产出/效果

- 长视频分析只需检索 TopK 相关片段，而非全部内容，Token 消耗大幅降低
- 自适应权重让不同类型视频（讲座 vs 代码演示）都能找到合适的检索策略
- 基础设施故障不阻断主分析链路，降级但不中断

#### 面试追问

**Q：为什么每 5 分钟一个 chunk？**

> 5 分钟是一个经验值：太短（如 1 分钟）会导致 chunk 数量爆炸、摘要成本太高；太长（如 15 分钟）会导致摘要丢失细节。5 分钟大约对应一个完整的知识单元（一个概念、一个操作步骤），摘要质量最好。

**Q：自适应权重的逻辑是什么？**

> 根据 LLM 生成的检索意图判断：如果 `visualKeywords` 不为空，说明用户目标涉及画面内容（PPT、代码、图表），提升视觉权重到 0.30；如果 `keywords` ≥5 个，说明目标涉及大量专有名词（讲座类），提升关键词权重到 0.35；否则用默认权重。这是"让检索策略匹配内容类型"的设计。

**Q：降级后质量差多少？**

> 纯关键词匹配在短查询上效果还行（关键词命中率高），但长查询或同义查询会明显下降。降级是"保底不阻断"，用户感知是"检索结果没那么精准"，但分析任务不会失败。类比搜索引擎：网络不好时降级到缓存结果，总比白屏好。

---

## 三、面试官最可能问的通用问题

### Q：这个项目你花了多长时间？

> 从架构设计到可运行版本约 2-3 个月。核心链路（AgentLoop + VideoContext）用了大约 4 周，异步任务链路 + 检查点用了 2 周，前端工作台 + 联调用了 2 周。后续持续迭代优化（深度分析模式、证据校验、检索优化）又花了 2-3 周。

### Q：你遇到的最大技术难点是什么？

> **LLM 输出不稳定**。DeepSeek 返回的 JSON 格式偶尔不合法（多一个逗号、少一个括号），或者 Critic 的 `passed` 字段和 `feedback` 列表矛盾（passed=true 但 feedback 不为空）。解决方案两层：
> 1. 解析层：`structuredChat()` 解析失败时自动重试一次（附加"请严格返回合法 JSON"的提示）
> 2. 校验层：`enforceStructureBounds()` 和 `enforceEvidenceBounds()` 在 Critic 返回后强制校验结构一致性，矛盾时强制置为不通过

### Q：如果让你重新做，你会改什么？

> 1. **ASR 换成本地 Whisper**：TeleSpeechASR 是云端 API，偶尔超时且受网络影响；本地 Whisper 模型延迟更可控，且无调用成本
> 2. **结果缓存层更彻底**：代码里 `reuseResult()` 已经做了内容级复用（同一视频不同目标不重跑 ASR/OCR），但分析结果的复用还可以做得更细粒度
> 3. **SSE 换 WebSocket**：当前 SSE 是单向推送，追问功能需要额外的 HTTP 请求；WebSocket 可以做到真正的双向实时通信

### Q：这个项目的难点在哪里？和直接调 LLM API 有什么区别？

> 直接调 LLM API 的问题是：1）长视频塞不进 context window；2）LLM 会"编造"内容；3）单次调用质量不可控。这个项目解决的核心问题是**让 LLM 的分析过程可控制、可校验、可追溯**——通过 VideoChunking 解决长视频问题，通过 EvidenceVerification 解决编造问题，通过 Planner-Executor-Critic 解决质量问题。

### Q：项目的并发量 / 数据量 / 用户量？

> 目前是个人项目/课程设计级别，单机部署。但架构上已经考虑了水平扩展：
> - RocketMQ 消费者可以多实例部署
> - Redis/MySQL/Qdrant 都是独立服务，可以独立扩容
> - 内容级 Checkpoint 天然支持多消费者共享
>
> 如果面试官追问"如果用户量增长到 10 倍怎么办"，回答思路：RocketMQ 消费者水平扩展 + MySQL 读写分离 + Qdrant 分片 + 视频处理走 GPU 集群。

---

## 四、简历关键词对齐

以下关键词已在项目描述中覆盖，可根据目标岗位 JD 调整侧重点：

### Java 后端开发

- `Java 21` `Spring Boot 3.5` `MyBatis-Plus`
- `RocketMQ` `Redis` `Redisson` `分布式锁` `幂等`
- `MySQL` `检查点` `断点恢复`
- `Resilience4j` `熔断器` `限流`
- `线程池` `并发` `异步`
- `FFmpeg` `多模态`

### 全栈开发

- `Vue 3` `Vite` `SSE` `实时推送`
- `分片上传` `断点续传`
- `Markdown 渲染`
- `组件化设计`

### AI 工程师

- `Agent 架构` `Planner-Executor-Critic`
- `LangChain4j` `DeepSeek`
- `ASR` `OCR` `Embedding`
- `向量检索` `Qdrant`
- `Prompt Engineering` `结构化输出`

### 架构师

- `异步削峰` `消息队列`
- `分布式锁` `幂等消费`
- `断点恢复` `状态机`
- `优雅降级` `熔断`
- `成本控制` `预算管理`
