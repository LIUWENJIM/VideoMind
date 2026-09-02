package com.example.server.dto;

import java.util.List;

/**
 * 深度分析产物结构：支持洞察、推理、批判性思考，而非简单的"结论+证据"。
 */
public record DeepAnalysisResult(
        String executiveSummary,      // 一句话核心洞察
        String context,               // 背景与重要性
        List<KeyInsight> keyInsights, // 核心洞察（带推理）
        CriticalAnalysis criticalAnalysis,  // 批判性评价
        List<ActionableTakeaway> actionableTakeaways,  // 可操作收获
        List<Evidence> supportingEvidence,  // 支撑证据
        List<Section> sections,       // 模式化段落
        List<KeyTerm> keyTerms        // 关键术语/难点解释
) {
    public DeepAnalysisResult {
        executiveSummary = executiveSummary == null ? "" : executiveSummary.trim();
        context = context == null ? "" : context.trim();
        keyInsights = keyInsights == null ? List.of() : List.copyOf(keyInsights);
        criticalAnalysis = criticalAnalysis == null ? new CriticalAnalysis(List.of(), List.of(), List.of()) : criticalAnalysis;
        actionableTakeaways = actionableTakeaways == null ? List.of() : List.copyOf(actionableTakeaways);
        supportingEvidence = supportingEvidence == null ? List.of() : List.copyOf(supportingEvidence);
        sections = sections == null ? List.of() : List.copyOf(sections);
        keyTerms = keyTerms == null ? List.of() : List.copyOf(keyTerms);
    }

    /**
     * 核心洞察：不只是"视频说了什么"，而是"这意味着什么"
     */
    public record KeyInsight(
            String insight,           // 洞察本身
            String reasoning,         // 为什么这是重要的
            List<Evidence> evidence,  // 支撑证据
            String implications,      // 这意味着什么（影响/启示）
            List<String> relatedConcepts  // 相关概念/知识关联
    ) {
        public KeyInsight {
            insight = insight == null ? "" : insight.trim();
            reasoning = reasoning == null ? "" : reasoning.trim();
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            implications = implications == null ? "" : implications.trim();
            relatedConcepts = relatedConcepts == null ? List.of() : List.copyOf(relatedConcepts);
        }
    }

    /**
     * 批判性分析：不只是"视频说了什么"，还有"局限性和替代观点"
     */
    public record CriticalAnalysis(
            List<String> strengths,              // 论证的优点
            List<String> limitations,            // 局限性和假设
            List<String> alternativePerspectives // 替代观点
    ) {
        public CriticalAnalysis {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            alternativePerspectives = alternativePerspectives == null ? List.of() : List.copyOf(alternativePerspectives);
        }
    }

    /**
     * 可操作收获：不只是"建议"，而是"如何应用"
     */
    public record ActionableTakeaway(
            String takeaway,        // 收获本身
            String application,     // 如何应用
            List<String> nextSteps, // 下一步做什么
            List<Evidence> evidence // 支撑证据
    ) {
        public ActionableTakeaway {
            takeaway = takeaway == null ? "" : takeaway.trim();
            application = application == null ? "" : application.trim();
            nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record Evidence(
            long timestampMs,
            String source,
            String content,
            String claim
    ) {
        public Evidence {
            if (timestampMs < 0) throw new IllegalArgumentException("evidence timestamp cannot be negative");
            source = source == null ? "UNKNOWN" : source.trim();
            content = content == null ? "" : content.trim();
            claim = claim == null ? "" : claim.trim();
        }
    }

    public record Section(String key, String title, List<String> items) {
        public Section {
            key = key == null ? "" : key.trim();
            title = title == null ? "" : title.trim();
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /**
     * 关键术语/难点解释：在分析文本中出现的专业术语，提供简短解释。
     */
    public record KeyTerm(
            String term,           // 术语名称（如 "ReAct 范式"、"显式栈"）
            String definition,     // 简短定义/解释
            String context         // 在当前视频语境下的具体含义（可选）
    ) {
        public KeyTerm {
            term = term == null ? "" : term.trim();
            definition = definition == null ? "" : definition.trim();
            context = context == null ? "" : context.trim();
        }
    }

    /**
     * 转换为 Markdown 格式（向后兼容）
     */
    public String toMarkdown() {
        StringBuilder result = new StringBuilder();

        // 执行摘要
        if (!executiveSummary.isEmpty()) {
            result.append("## 📌 核心洞察\n\n");
            result.append(executiveSummary).append("\n\n");
        }

        // 背景与重要性
        if (!context.isEmpty()) {
            result.append("## 🎯 背景与重要性\n\n");
            result.append(context).append("\n\n");
        }

        // 核心洞察
        if (!keyInsights.isEmpty()) {
            result.append("## 💡 核心洞察\n\n");
            for (int i = 0; i < keyInsights.size(); i++) {
                KeyInsight insight = keyInsights.get(i);
                result.append("### 洞察 ").append(i + 1).append("\n\n");
                result.append("**").append(insight.insight()).append("**\n\n");

                if (!insight.reasoning().isEmpty()) {
                    result.append("**为什么重要**：").append(insight.reasoning()).append("\n\n");
                }

                if (!insight.implications().isEmpty()) {
                    result.append("**意味着什么**：").append(insight.implications()).append("\n\n");
                }

                if (!insight.evidence().isEmpty()) {
                    result.append("**视频证据**：\n");
                    for (Evidence e : insight.evidence()) {
                        result.append("- [").append(formatTime(e.timestampMs())).append("] ")
                              .append(e.source()).append("：").append(e.content()).append("\n");
                    }
                    result.append("\n");
                }

                if (!insight.relatedConcepts().isEmpty()) {
                    result.append("**相关概念**：").append(String.join(", ", insight.relatedConcepts())).append("\n\n");
                }
            }
        }

        // 批判性分析
        if (!criticalAnalysis.strengths().isEmpty() ||
            !criticalAnalysis.limitations().isEmpty() ||
            !criticalAnalysis.alternativePerspectives().isEmpty()) {
            result.append("## 🔍 批判性分析\n\n");

            if (!criticalAnalysis.strengths().isEmpty()) {
                result.append("### 论证优点\n");
                criticalAnalysis.strengths().forEach(s -> result.append("- ").append(s).append("\n"));
                result.append("\n");
            }

            if (!criticalAnalysis.limitations().isEmpty()) {
                result.append("### 局限性与假设\n");
                criticalAnalysis.limitations().forEach(s -> result.append("- ").append(s).append("\n"));
                result.append("\n");
            }

            if (!criticalAnalysis.alternativePerspectives().isEmpty()) {
                result.append("### 替代观点\n");
                criticalAnalysis.alternativePerspectives().forEach(s -> result.append("- ").append(s).append("\n"));
                result.append("\n");
            }
        }

        // 可操作收获
        if (!actionableTakeaways.isEmpty()) {
            result.append("## 🎯 可操作收获\n\n");
            for (int i = 0; i < actionableTakeaways.size(); i++) {
                ActionableTakeaway takeaway = actionableTakeaways.get(i);
                result.append("### 收获 ").append(i + 1).append("\n\n");
                result.append("**").append(takeaway.takeaway()).append("**\n\n");

                if (!takeaway.application().isEmpty()) {
                    result.append("**如何应用**：").append(takeaway.application()).append("\n\n");
                }

                if (!takeaway.nextSteps().isEmpty()) {
                    result.append("**下一步做什么**：\n");
                    takeaway.nextSteps().forEach(s -> result.append("- ").append(s).append("\n"));
                    result.append("\n");
                }

                if (!takeaway.evidence().isEmpty()) {
                    result.append("**视频证据**：\n");
                    for (Evidence e : takeaway.evidence()) {
                        result.append("- [").append(formatTime(e.timestampMs())).append("] ")
                              .append(e.source()).append("：").append(e.content()).append("\n");
                    }
                    result.append("\n");
                }
            }
        }

        // 模式化段落
        for (Section section : sections) {
            result.append("\n## ").append(section.title()).append("\n");
            section.items().forEach(item -> result.append("- ").append(item).append("\n"));
        }

        return result.toString();
    }

    private static String formatTime(long timestampMs) {
        long seconds = timestampMs / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
