-- 添加深度分析结果 JSON 字段
-- 用于存储结构化的 DeepAnalysisResult，支持前端展示洞察卡片等组件
ALTER TABLE media_files
ADD COLUMN deep_analysis_json JSON NULL COMMENT '深度分析结果的结构化 JSON 数据';
