package com.example.server.service;

import com.example.server.dto.AgentState;
import com.example.server.dto.AnalysisMode;
import com.example.server.dto.AnalysisResult;
import com.example.server.dto.TaskStatus;
import com.example.server.dto.TaskStage;
import com.example.server.dto.VideoContext;
import com.example.server.service.mode.ModeProfile;
import com.example.server.utils.DeepSeekUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 受控 Agent 编排器：恢复状态、执行一轮分析、校验证据，再决定结束还是补跑。 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);
    private static final int MAX_PLAN_TASKS = 5;

    private final DeepSeekUtils deepSeekUtils;
    private final LongVideoContextService longVideoContextService;
    private final AgentCheckpointService checkpointService;
    private final AgentTelemetry telemetry;
    private final EvidenceVerificationService evidenceVerificationService;
    private final TaskEventService taskEventService;

    /** Config ceiling: dynamic budget never exceeds these values. */
    private final int maxRounds;
    private final long maxDurationMs;
    private final long maxEstimatedTokens;
    private final double maxEstimatedCost;

    /** Per-run budget computed from video complexity. */
    record BudgetConfig(int maxRounds, long durationMs, long maxTokens) {}

    public AgentLoopService(DeepSeekUtils deepSeekUtils,
                            LongVideoContextService longVideoContextService,
                            AgentCheckpointService checkpointService,
                            AgentTelemetry telemetry,
                            EvidenceVerificationService evidenceVerificationService,
                            TaskEventService taskEventService,
                            @Value("${agent.budget.max-rounds:2}") int maxRounds,
                            @Value("${agent.budget.max-duration-ms:120000}") long maxDurationMs,
                            @Value("${agent.budget.max-estimated-tokens:50000}") long maxEstimatedTokens,
                            @Value("${agent.budget.max-estimated-cost:0}") double maxEstimatedCost) {
        this.deepSeekUtils = deepSeekUtils;
        this.longVideoContextService = longVideoContextService;
        this.checkpointService = checkpointService;
        this.telemetry = telemetry;
        this.evidenceVerificationService = evidenceVerificationService;
        this.taskEventService = taskEventService;
        if (maxRounds < 1 || maxRounds > 5) {
            throw new IllegalArgumentException("max-rounds 必须在 1-5 之间，当前值: " + maxRounds);
        }
        if (maxDurationMs < 10_000L || maxDurationMs > 600_000L) {
            throw new IllegalArgumentException(
                    "max-duration-ms 必须在 10s-600s 之间，当前值: " + maxDurationMs);
        }
        if (maxEstimatedTokens < 10_000L || maxEstimatedTokens > 200_000L) {
            throw new IllegalArgumentException(
                    "max-estimated-tokens 必须在 10000-200000 之间，当前值: " + maxEstimatedTokens);
        }
        if (maxEstimatedCost < 0) {
            throw new IllegalArgumentException("max-estimated-cost 不能为负数");
        }
        this.maxRounds = maxRounds;
        this.maxDurationMs = maxDurationMs;
        this.maxEstimatedTokens = maxEstimatedTokens;
        this.maxEstimatedCost = maxEstimatedCost;
    }

    public AgentState run(VideoContext context) {
        return run(null, context, null);
    }

    /** 兼容旧调用方:无模式 = GENERAL(空指令 Profile)。 */
    public AgentState run(Long mediaId, VideoContext context) {
        return run(mediaId, context, null);
    }

    /**
     * 执行一轮受控 Agent 分析。{@code profile} 为空时等价于 GENERAL——三段模式指令均为空串,
     * 拼接后 prompt 与引入模式体系前完全一致,因此默认行为不变。
     */
    public AgentState run(Long mediaId, VideoContext context, ModeProfile profile) {
        validateContext(context);
        // 清除上一个任务残留的 ThreadLocal deadline，防止线程池复用时预算串号
        AgentExecutionBudget.resetThreadLocal();
        BudgetConfig budget = calculateDynamicBudget(context);
        try (AgentExecutionBudget.Scope ignored = AgentExecutionBudget.open(budget.durationMs())) {
            return runWithinBudget(mediaId, context, profile, budget);
        } catch (BudgetExceededException e) {
            throw e;
        } catch (RuntimeException e) {
            AgentExecutionBudget.DeadlineExceededException deadline = findDeadline(e);
            if (deadline == null) throw e;
            telemetry.incrementCurrent("budgetTerminations", 1);
            throw new BudgetExceededException(deadline.getMessage(), e);
        }
    }

    /**
     * 根据视频复杂度动态调整预算。简单视频（< 5 分钟、< 10 段）只给 1 轮、60s；
     * 中等视频（5–30 分钟）给 2 轮、120s；复杂长视频给满配置上限。
     * 所有动态值都不超过 application.yml 中配置的上限。
     *
     * <p>注意：LLM 单次调用通常需要 20-40s，加上 VideoContext 构建和 Planner，
     * 总耗时很容易超过 60s。预算必须留够余量。
     */
    private BudgetConfig calculateDynamicBudget(VideoContext context) {
        long totalDurationMs = context.segments().stream()
                .mapToLong(s -> s.endMs() - s.startMs())
                .sum();
        int segmentCount = context.segments().size();

        int dynamicRounds;
        long dynamicDurationMs;
        long dynamicTokens;

        if (totalDurationMs < 300_000L && segmentCount < 10) {
            // Simple: short video with few segments — 1 round, 60s
            dynamicRounds = 1;
            dynamicDurationMs = 60_000L;
            dynamicTokens = 30_000L;
        } else if (totalDurationMs < 1_800_000L) {
            // Medium: up to 30 minutes — 2 rounds, use 80% of max budget
            dynamicRounds = 2;
            dynamicDurationMs = (long) (maxDurationMs * 0.8);
            dynamicTokens = (long) (maxEstimatedTokens * 0.8);
        } else {
            // Complex: longer than 30 minutes — use full config budget
            dynamicRounds = maxRounds;
            dynamicDurationMs = maxDurationMs;
            dynamicTokens = maxEstimatedTokens;
        }

        // Never exceed configured ceiling
        return new BudgetConfig(
                Math.min(dynamicRounds, maxRounds),
                Math.min(dynamicDurationMs, maxDurationMs),
                Math.min(dynamicTokens, maxEstimatedTokens));
    }

    private AgentState runWithinBudget(Long mediaId, VideoContext context, ModeProfile profile,
                                       BudgetConfig budget) {
        long runStartedNanos = System.nanoTime();
        AgentState savedState = mediaId == null ? null
                : checkpointService.loadCriticState(mediaId, context.userGoal(), modeOf(profile));

        VideoContext relevantContext = longVideoContextService.selectRelevant(mediaId, context);
        AgentState.AgentPlan plan = resolvePlan(mediaId, relevantContext, savedState, profile);
        checkBudget(runStartedNanos, "Planner", budget);
        if (mediaId != null) {
            taskEventService.publishAnalysis(mediaId, relevantContext.userGoal(), modeOf(profile),
                    TaskStatus.of(TaskStatus.State.PROCESSING, "Planner 已完成任务拆解"),
                    TaskStage.PLAN_COMPLETED);
        }

        // Determine resume strategy from saved state
        AgentState state;
        ResumeDecision resumeDecision;
        if (savedState == null) {
            state = new AgentState(relevantContext.userGoal(), plan, null, null, 0);
            resumeDecision = ResumeDecision.CONTINUE_FROM_ROUNDS;
        } else {
            resumeDecision = resolveResumeDecision(savedState, profile, budget);
            state = switch (resumeDecision) {
                case RETURN_IMMEDIATELY -> {
                    checkpointService.saveResult(mediaId, savedState, modeOf(profile));
                    telemetry.incrementCurrent("terminalCheckpointHits", 1);
                    yield savedState;
                }
                case REPAIR_AND_CONTINUE -> new AgentState(
                        savedState.goal(), savedState.plan(), savedState.result(), savedState.critique(), 0);
                case CRITIC_CHECKPOINT -> {
                    telemetry.incrementCurrent("criticCheckpointResumes", 1);
                    checkBudget(runStartedNanos, "Executor Checkpoint", budget);
                    yield new AgentState(savedState.goal(), plan,
                            savedState.result(), savedState.critique(), savedState.round());
                }
                case CONTINUE_FROM_ROUNDS -> new AgentState(
                        savedState.goal(), plan, savedState.result(), savedState.critique(), savedState.round());
            };
            if (resumeDecision == ResumeDecision.RETURN_IMMEDIATELY) {
                return state;
            }
        }

        // Pre-loop: if resuming with failed critique, refresh evidence and revise plan
        if (resumeDecision == ResumeDecision.CONTINUE_FROM_ROUNDS
                || resumeDecision == ResumeDecision.REPAIR_AND_CONTINUE) {
            if (state.critique() != null && !state.critique().passed()) {
                relevantContext = contextForRetry(
                        mediaId, context, relevantContext, state.critique(), profile);
                plan = revisePlanForRetry(mediaId, relevantContext, plan, state.critique(), profile);
            }
        }

        // Critic checkpoint resume: skip executor, go directly to critique
        if (resumeDecision == ResumeDecision.CRITIC_CHECKPOINT) {
            state = critiqueRound(mediaId, relevantContext, plan, state.result(), state.round(), profile, budget);
            if (state.critique().passed() || state.round() >= budget.maxRounds()) {
                return finalizeState(mediaId, state, profile);
            }
            relevantContext = contextForRetry(
                    mediaId, context, relevantContext, state.critique(), profile);
            plan = revisePlanForRetry(mediaId, relevantContext, plan, state.critique(), profile);
        }

        // Main execute-critique-retry loop
        for (int round = state.round() + 1; round <= budget.maxRounds(); round++) {
            checkBudget(runStartedNanos, "Agent Round " + round, budget);
            state = executeRound(
                    mediaId, relevantContext, plan, state.critique(), round, runStartedNanos, profile, budget);
            if (state.critique().passed()) break;
            if (round < budget.maxRounds()) {
                relevantContext = contextForRetry(
                        mediaId, context, relevantContext, state.critique(), profile);
                plan = revisePlanForRetry(mediaId, relevantContext, plan, state.critique(), profile);
            }
        }

        return finalizeState(mediaId, state, profile);
    }

    /** Classify the saved checkpoint into one of four resume strategies. */
    private ResumeDecision resolveResumeDecision(AgentState savedState, ModeProfile profile,
                                                 BudgetConfig budget) {
        boolean terminal = savedState.result() != null
                && savedState.critique() != null
                && (savedState.round() >= budget.maxRounds() || savedState.critique().passed());

        if (terminal) {
            if (isPlanValid(savedState.plan()) && isResultValid(savedState.result(), profile)) {
                return ResumeDecision.RETURN_IMMEDIATELY;
            }
            telemetry.incrementCurrent("invalidTerminalCheckpointRepairs", 1);
            return ResumeDecision.REPAIR_AND_CONTINUE;
        }

        // Executor draft was persisted but Critic hasn't run yet → skip executor, resume from Critic
        if (savedState.result() != null && savedState.critique() == null && savedState.round() > 0) {
            return ResumeDecision.CRITIC_CHECKPOINT;
        }

        return ResumeDecision.CONTINUE_FROM_ROUNDS;
    }

    private AgentState finalizeState(Long mediaId, AgentState state, ModeProfile profile) {
        validateResult(state.result(), profile);
        if (mediaId != null) checkpointService.saveResult(mediaId, state, modeOf(profile));
        return state;
    }

    private enum ResumeDecision {
        /** Terminal checkpoint is valid — return immediately without re-execution. */
        RETURN_IMMEDIATELY,
        /** Terminal checkpoint has invalid structure — reset round to 0 and re-execute. */
        REPAIR_AND_CONTINUE,
        /** Executor draft persisted, Critic not yet run — skip to Critic phase. */
        CRITIC_CHECKPOINT,
        /** Normal resume: continue from saved round with refreshed evidence. */
        CONTINUE_FROM_ROUNDS
    }

    private AgentState.AgentPlan resolvePlan(Long mediaId,
                                             VideoContext context,
                                             AgentState savedState,
                                             ModeProfile profile) {
        AgentState.AgentPlan plan = mediaId == null
                ? null
                : checkpointService.loadPlan(mediaId, context.userGoal(), modeOf(profile));
        if (plan == null && savedState != null) plan = savedState.plan();
        boolean shouldPersist = false;
        if (plan == null) {
            plan = deepSeekUtils.plan(context, planInstruction(profile));
            shouldPersist = true;
        }
        if (!isPlanValid(plan)) {
            plan = deepSeekUtils.repairPlan(context, plan, planInstruction(profile));
            telemetry.incrementCurrent("planStructureRepairs", 1);
            shouldPersist = true;
        }
        validatePlan(plan);
        if (mediaId != null && shouldPersist) {
            checkpointService.savePlan(mediaId, context.userGoal(), modeOf(profile), plan);
        }
        return plan;
    }

    private AgentState executeRound(Long mediaId,
                                    VideoContext context,
                                    AgentState.AgentPlan plan,
                                    AgentState.CriticResult previousCritique,
                                    int round,
                                    long runStartedNanos,
                                    ModeProfile profile,
                                    BudgetConfig budget) {
        publishStage(mediaId, context.userGoal(), modeOf(profile),
                "Executor 正在按计划生成结构化产物", TaskStage.EXECUTOR_STARTED);
        AnalysisResult result = deepSeekUtils.execute(context, plan, previousCritique, executeInstruction(profile));
        AgentState draft = new AgentState(context.userGoal(), plan, result, null, round);
        if (mediaId != null) {
            checkpointService.saveExecutionState(mediaId, draft, modeOf(profile));
            publishStage(mediaId, context.userGoal(), modeOf(profile),
                    "Executor 草稿已保存，开始校验证据", TaskStage.EXECUTOR_COMPLETED);
        }
        checkBudget(runStartedNanos, "Executor", budget);
        return critiqueRound(mediaId, context, plan, result, round, profile, budget);
    }

    private AgentState critiqueRound(Long mediaId,
                                     VideoContext context,
                                     AgentState.AgentPlan plan,
                                     AnalysisResult result,
                                     int round,
                                     ModeProfile profile,
                                     BudgetConfig budget) {
        publishStage(mediaId, context.userGoal(), modeOf(profile),
                "Critic 正在核验目标覆盖与时间戳证据", TaskStage.CRITIC_STARTED);
        AgentState.CriticResult critique = normalizeCritique(
                deepSeekUtils.critique(context, plan, result, criticInstruction(profile)));
        critique = enforceStructureBounds(result, critique, profile);
        critique = enforceEvidenceBounds(context, result, critique);
        telemetry.incrementCurrent("criticRounds", 1);
        if (critique.passed()) telemetry.incrementCurrent("criticPassed", 1);

        AgentState state = new AgentState(context.userGoal(), plan, result, critique, round);
        if (mediaId != null) {
            checkpointService.saveCriticState(mediaId, state, modeOf(profile));
            String message;
            TaskStage stage;
            if (critique.passed()) {
                message = "Critic 校验通过，正在整理结构化结果";
                stage = TaskStage.CRITIC_PASSED;
            } else if (round >= budget.maxRounds()) {
                message = "Critic 达到最大校验轮次，正在保留警告并生成结果";
                stage = TaskStage.ANALYSIS_COMPLETED_WITH_WARNINGS;
            } else if (requiresEvidenceRefresh(critique)) {
                message = "Critic 发现证据缺口，正在定向补充证据";
                stage = TaskStage.CRITIC_RETRY_REQUIRED;
            } else {
                message = "Critic 发现目标覆盖或结构问题，正在按反馈重写";
                stage = TaskStage.CRITIC_RETRY_REQUIRED;
            }
            publishStage(mediaId, context.userGoal(), modeOf(profile), message, stage);
        }
        return state;
    }

    private void validateContext(VideoContext context) {
        if (context == null || context.userGoal() == null || context.userGoal().isBlank()
                || context.segments() == null || context.segments().isEmpty()
                || context.segments().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Agent 需要目标和至少一个视频片段");
        }
    }

    private void validatePlan(AgentState.AgentPlan plan) {
        if (!isPlanValid(plan)) {
            throw new IllegalStateException("Planner 返回了无效任务列表");
        }
    }

    private boolean isPlanValid(AgentState.AgentPlan plan) {
        return plan != null && plan.understoodGoal() != null && !plan.understoodGoal().isBlank()
                && plan.tasks() != null && !plan.tasks().isEmpty()
                && plan.tasks().size() <= MAX_PLAN_TASKS
                && plan.tasks().stream().noneMatch(
                task -> task == null || task.isBlank() || task.length() > 500);
    }

    private void validateResult(AnalysisResult result, ModeProfile profile) {
        if (!isResultValid(result, profile)) {
            throw new IllegalStateException("Executor 未生成完整结构化结果");
        }
    }

    private boolean isResultValid(AnalysisResult result, ModeProfile profile) {
        boolean commonFieldsValid = result != null
                && result.title() != null && !result.title().isBlank()
                && result.conclusions() != null && !result.conclusions().isEmpty()
                && result.evidence() != null && !result.evidence().isEmpty();
        if (!commonFieldsValid || profile == null || profile.requiredSectionKeys().isEmpty()) {
            return commonFieldsValid;
        }
        List<String> presentKeys = result.sections().stream()
                .filter(section -> section != null && section.key() != null
                        && !section.key().isBlank() && !section.items().isEmpty())
                .map(section -> section.key().trim())
                .distinct()
                .toList();
        return presentKeys.containsAll(profile.requiredSectionKeys());
    }

    private AgentState.CriticResult enforceEvidenceBounds(VideoContext context,
                                                           AnalysisResult result,
                                                           AgentState.CriticResult critique) {
        critique = normalizeCritique(critique);
        boolean hasDeclaredProblems = !critique.feedback().isEmpty()
                || !critique.missingRequirements().isEmpty()
                || !critique.unsupportedClaims().isEmpty()
                || !critique.requiredTimestamps().isEmpty();
        if (critique.passed() && hasDeclaredProblems) {
            critique = new AgentState.CriticResult(
                    false,
                    critique.feedback(),
                    critique.missingRequirements(),
                    critique.unsupportedClaims(),
                    critique.requiredTimestamps());
        }
        if (!critique.passed()
                && critique.feedback().isEmpty()
                && critique.missingRequirements().isEmpty()
                && critique.unsupportedClaims().isEmpty()
                && critique.requiredTimestamps().isEmpty()) {
            critique = new AgentState.CriticResult(
                    false,
                    List.of("重新检查目标覆盖、结构完整性和证据绑定"),
                    List.of(), List.of(), List.of());
        }
        if (result == null || result.evidence() == null || result.evidence().isEmpty()) return critique;
        List<AnalysisResult.Evidence> invalidEvidence = result.evidence().stream()
                .filter(evidence -> !evidenceVerificationService.supported(context, evidence))
                .toList();
        List<String> unsupportedClaims = result.conclusions().stream()
                .filter(claim -> result.evidence().stream().noneMatch(
                        evidence -> evidenceVerificationService.supportsClaim(context, claim, evidence)))
                .toList();
        if (invalidEvidence.isEmpty() && unsupportedClaims.isEmpty()) return critique;

        List<String> unsupported = new ArrayList<>(critique.unsupportedClaims());
        unsupportedClaims.stream()
                .filter(claim -> !unsupported.contains(claim))
                .forEach(unsupported::add);
        invalidEvidence.stream()
                .map(evidence -> "证据无法在原始 ASR/OCR 中核验: " + evidence.timestampMs())
                .forEach(unsupported::add);
        List<String> feedback = new ArrayList<>(critique.feedback());
        feedback.add("为每条结论重新检索并绑定可核验的时间戳证据");
        List<Long> requiredTimestamps = new ArrayList<>(critique.requiredTimestamps());
        invalidEvidence.stream()
                .map(AnalysisResult.Evidence::timestampMs)
                .filter(timestamp -> !requiredTimestamps.contains(timestamp))
                .forEach(requiredTimestamps::add);
        return new AgentState.CriticResult(
                false,
                feedback,
                critique.missingRequirements(),
                unsupported,
                requiredTimestamps);
    }

    private AgentState.CriticResult enforceStructureBounds(AnalysisResult result,
                                                            AgentState.CriticResult critique,
                                                            ModeProfile profile) {
        critique = normalizeCritique(critique);
        List<String> feedback = new ArrayList<>(critique.feedback());
        if (result == null || result.title() == null || result.title().isBlank()) {
            feedback.add("补充明确的产物标题");
        }
        if (result == null || result.conclusions() == null || result.conclusions().isEmpty()) {
            feedback.add("补充覆盖 Planner 任务的核心结论");
        }
        if (result == null || result.evidence() == null || result.evidence().isEmpty()) {
            feedback.add("为核心结论补充带时间戳的 ASR 或 OCR 证据");
        }
        List<String> missingSections = missingSectionKeys(result, profile);
        if (!missingSections.isEmpty()) {
            feedback.add("补充当前分析模式要求的结构化段落: " + String.join(", ", missingSections));
        }
        if (feedback.equals(critique.feedback())) return critique;
        return new AgentState.CriticResult(
                false,
                feedback,
                critique.missingRequirements(),
                critique.unsupportedClaims(),
                critique.requiredTimestamps());
    }

    private VideoContext contextForRetry(Long mediaId,
                                         VideoContext fullContext,
                                         VideoContext selectedContext,
                                         AgentState.CriticResult critique,
                                         ModeProfile profile) {
        if (!requiresEvidenceRefresh(critique)) {
            telemetry.incrementCurrent("criticRewriteOnlyRetries", 1);
            return selectedContext;
        }
        telemetry.incrementCurrent("criticEvidenceRefreshes", 1);
        VideoContext refined = longVideoContextService.refineForCritique(
                mediaId, fullContext, selectedContext, critique);
        publishStage(mediaId, fullContext.userGoal(), modeOf(profile),
                "已按 Critic 反馈补充定向证据", TaskStage.EVIDENCE_REFRESHED);
        return refined;
    }

    private boolean requiresEvidenceRefresh(AgentState.CriticResult critique) {
        return critique != null
                && (!safeList(critique.requiredTimestamps()).isEmpty()
                || !safeList(critique.missingRequirements()).isEmpty()
                || !safeList(critique.unsupportedClaims()).isEmpty());
    }

    private AgentState.AgentPlan revisePlanForRetry(Long mediaId,
                                                    VideoContext context,
                                                    AgentState.AgentPlan currentPlan,
                                                    AgentState.CriticResult critique,
                                                    ModeProfile profile) {
        if (critique == null || safeList(critique.missingRequirements()).isEmpty()) return currentPlan;

        try {
            AgentState.AgentPlan revisedPlan = deepSeekUtils.replan(
                    context, currentPlan, critique, planInstruction(profile));
            validatePlan(revisedPlan);
            telemetry.incrementCurrent("planRevisions", 1);
            if (mediaId != null) {
                checkpointService.savePlan(mediaId, context.userGoal(), modeOf(profile), revisedPlan);
                taskEventService.publishAnalysis(mediaId, context.userGoal(), modeOf(profile),
                        TaskStatus.of(TaskStatus.State.PROCESSING, "Planner 根据 Critic 反馈补充了遗漏任务"),
                        TaskStage.PLAN_COMPLETED);
            }
            return revisedPlan;
        } catch (RuntimeException e) {
            telemetry.incrementCurrent("planRevisionFallbacks", 1);
            log.warn("agent_replan_failed mediaId={}, fallback to current plan", mediaId, e);
            return currentPlan;
        }
    }

    private void publishStage(Long mediaId,
                              String goal,
                              AnalysisMode mode,
                              String message,
                              TaskStage stage) {
        if (mediaId == null) return;
        taskEventService.publishAnalysis(mediaId, goal, mode,
                TaskStatus.of(TaskStatus.State.PROCESSING, message), stage);
    }

    private List<String> missingSectionKeys(AnalysisResult result, ModeProfile profile) {
        if (profile == null || profile.requiredSectionKeys().isEmpty()) return List.of();
        List<String> presentKeys = result == null || result.sections() == null
                ? List.of()
                : result.sections().stream()
                        .filter(section -> section != null
                                && section.key() != null
                                && !section.key().isBlank()
                                && section.items() != null
                                && !section.items().isEmpty())
                        .map(section -> section.key().trim())
                        .distinct()
                        .toList();
        return profile.requiredSectionKeys().stream()
                .filter(required -> !presentKeys.contains(required))
                .toList();
    }

    private void checkBudget(long startedNanos, String completedStage, BudgetConfig budget) {
        AgentExecutionBudget.check(completedStage);
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
        AgentTelemetry.BudgetUsage usage = telemetry.currentUsage();
        String reason = null;
        if (elapsedMs > budget.durationMs()) {
            reason = "Agent 超过最大执行时长 " + budget.durationMs() + "ms";
        } else if (usage.estimatedTokens() > budget.maxTokens()) {
            reason = "Agent 超过最大 Token 预算 " + budget.maxTokens();
        } else if (maxEstimatedCost > 0 && usage.estimatedCost() > maxEstimatedCost) {
            reason = "Agent 超过最大成本预算 " + maxEstimatedCost;
        }
        if (reason == null) return;
        telemetry.incrementCurrent("budgetTerminations", 1);
        throw new BudgetExceededException(completedStage + " 后终止：" + reason);
    }

    public static class BudgetExceededException extends IllegalStateException {
        public BudgetExceededException(String message) {
            super(message);
        }

        public BudgetExceededException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private AgentExecutionBudget.DeadlineExceededException findDeadline(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof AgentExecutionBudget.DeadlineExceededException deadline) {
                return deadline;
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    private AgentState.CriticResult normalizeCritique(AgentState.CriticResult critique) {
        if (critique == null) {
            return new AgentState.CriticResult(
                    false, List.of("Critic 未返回有效结果"),
                    List.of(), List.of(), List.of());
        }
        return new AgentState.CriticResult(
                critique.passed(),
                safeList(critique.feedback()),
                safeList(critique.missingRequirements()),
                safeList(critique.unsupportedClaims()),
                safeList(critique.requiredTimestamps()));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    // profile 为空(GENERAL)时返回空指令/GENERAL 模式,使 prompt 与 checkpoint 键都与引入模式前一致。
    private static AnalysisMode modeOf(ModeProfile profile) {
        return profile == null ? AnalysisMode.GENERAL : profile.mode();
    }

    private static String planInstruction(ModeProfile profile) {
        return profile == null ? "" : profile.planInstruction();
    }

    private static String executeInstruction(ModeProfile profile) {
        return profile == null ? "" : profile.executeInstruction();
    }

    private static String criticInstruction(ModeProfile profile) {
        return profile == null ? "" : profile.criticInstruction();
    }
}
