package com.example.server.service;

import com.example.server.dto.AgentState;
import com.example.server.dto.DeepAnalysisResult;
import com.example.server.dto.TaskStatus;
import com.example.server.dto.TaskStage;
import com.example.server.dto.VideoContext;
import com.example.server.service.mode.ModeProfile;
import com.example.server.utils.DeepSeekUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 深度分析 Agent：产出有洞察的分析，而非简单摘要。
 * 核心改进：
 * 1. 引导 LLM 进行深度思考（不只是总结）
 * 2. 产出结构化洞察（带推理、关联、影响）
 * 3. 包含批判性分析（局限性、替代观点）
 * 4. 提供可操作收获（如何应用）
 */
@Service
public class DeepAgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(DeepAgentLoopService.class);

    private final DeepSeekUtils deepSeekUtils;
    private final LongVideoContextService longVideoContextService;
    private final AgentCheckpointService checkpointService;
    private final AgentTelemetry telemetry;
    private final EvidenceVerificationService evidenceVerificationService;
    private final TaskEventService taskEventService;

    public DeepAgentLoopService(DeepSeekUtils deepSeekUtils,
                                 LongVideoContextService longVideoContextService,
                                 AgentCheckpointService checkpointService,
                                 AgentTelemetry telemetry,
                                 EvidenceVerificationService evidenceVerificationService,
                                 TaskEventService taskEventService) {
        this.deepSeekUtils = deepSeekUtils;
        this.longVideoContextService = longVideoContextService;
        this.checkpointService = checkpointService;
        this.telemetry = telemetry;
        this.evidenceVerificationService = evidenceVerificationService;
        this.taskEventService = taskEventService;
    }

    /**
     * 执行深度分析：产出有洞察的分析结果
     */
    public DeepAnalysisResult analyze(Long mediaId, VideoContext context, String goal, ModeProfile profile) {
        log.info("deep_analysis_start mediaId={} goal={}", mediaId, goal);

        // Step 1: 选择相关上下文（与原来一样）
        VideoContext relevantContext = longVideoContextService.selectRelevant(mediaId, context);

        // Step 2: 规划分析任务（使用增强版 Planner）
        AgentState.AgentPlan plan = deepSeekUtils.plan(context, getDeepPlanningInstruction());
        publishStage(mediaId, goal, profile, "Planner 已完成深度分析规划", TaskStage.PLAN_COMPLETED);

        // Step 3: 执行深度分析（使用增强版 Executor）
        DeepAnalysisResult result = deepSeekUtils.executeDeepAnalysis(relevantContext, plan, null, "");
        publishStage(mediaId, goal, profile, "Executor 已完成深度分析", TaskStage.EXECUTOR_COMPLETED);

        // Step 4: 验证证据（与原来一样）
        DeepAnalysisResult verifiedResult = verifyEvidence(relevantContext, result);

        log.info("deep_analysis_complete mediaId={} insights={} takeaways={}",
                mediaId, verifiedResult.keyInsights().size(), verifiedResult.actionableTakeaways().size());

        return verifiedResult;
    }

    /**
     * 增强的规划指令：引导 LLM 进行深度思考
     */
    private String getDeepPlanningInstruction() {
        return """
                本次分析需要进行深度思考，不只是总结视频内容。任务应该包括：
                1. 识别视频的核心论点和隐含假设
                2. 分析论证的逻辑链条和证据强度
                3. 评估论证的优点和局限性
                4. 关联到更广泛的知识背景
                5. 提炼可操作的洞察和应用建议

                每个任务都应该产出有深度的洞察，而非简单的事实陈述。
                """;
    }

    /**
     * 验证证据：确保所有洞察都有证据支撑
     */
    private DeepAnalysisResult verifyEvidence(VideoContext context, DeepAnalysisResult result) {
        // 验证核心洞察的证据
        var verifiedInsights = result.keyInsights().stream()
                .map(insight -> {
                    var verifiedEvidence = insight.evidence().stream()
                            .filter(e -> evidenceVerificationService.supported(context,
                                    new com.example.server.dto.AnalysisResult.Evidence(
                                            e.timestampMs(), e.source(), e.content(), e.claim())))
                            .toList();
                    return new DeepAnalysisResult.KeyInsight(
                            insight.insight(),
                            insight.reasoning(),
                            verifiedEvidence,
                            insight.implications(),
                            insight.relatedConcepts()
                    );
                })
                .toList();

        // 验证可操作收获的证据
        var verifiedTakeaways = result.actionableTakeaways().stream()
                .map(takeaway -> {
                    var verifiedEvidence = takeaway.evidence().stream()
                            .filter(e -> evidenceVerificationService.supported(context,
                                    new com.example.server.dto.AnalysisResult.Evidence(
                                            e.timestampMs(), e.source(), e.content(), e.claim())))
                            .toList();
                    return new DeepAnalysisResult.ActionableTakeaway(
                            takeaway.takeaway(),
                            takeaway.application(),
                            takeaway.nextSteps(),
                            verifiedEvidence
                    );
                })
                .toList();

        return new DeepAnalysisResult(
                result.executiveSummary(),
                result.context(),
                verifiedInsights,
                result.criticalAnalysis(),
                verifiedTakeaways,
                result.supportingEvidence(),
                result.sections(),
                result.keyTerms()
        );
    }

    private void publishStage(Long mediaId, String goal, ModeProfile profile,
                               String message, TaskStage stage) {
        if (mediaId == null) return;
        taskEventService.publishAnalysis(mediaId, goal,
                profile == null ? com.example.server.dto.AnalysisMode.GENERAL : profile.mode(),
                TaskStatus.of(TaskStatus.State.PROCESSING, message), stage);
    }
}
