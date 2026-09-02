export const DEMO_ITEM = {
  id: 1001,
  filename: '数据结构课程 · 二叉树遍历.mp4',
  status: 'COMPLETED',
  uploadTime: '2026-07-10T14:30:00',
  transcriptText: '本节课介绍二叉树前序、中序和后序遍历，并对比递归与迭代实现。'
}

export const DEMO_PLAN = {
  understoodGoal: '理解视频核心内容，提炼关键结论，并给出带时间戳的证据和可执行建议',
  tasks: ['定位视频章节与核心主题', '融合 ASR 与关键帧 OCR 证据', '提炼结论并进行 Critic 校验']
}

export const DEMO_TRACE = {
  stageDurationMs: {
    VIDEO_CONTEXT: 12840,
    RETRIEVAL: 860,
    PLANNER: 1150,
    EXECUTOR: 3420,
    CRITIC: 1260
  }
}

export const DEMO_EVALUATION = {
  structuredValid: true,
  evidenceSupportRate: 0.92,
  criticPassed: true
}

export const DEMO_RESULT = `## 二叉树遍历课程分析

## 核心结论
- 前序遍历遵循”根节点、左子树、右子树”，适合构建树结构副本。
- 中序遍历二叉搜索树时可得到有序序列。
- 递归实现更直观，迭代实现通过栈显式保存访问状态。

## 视频证据
- [02:05] ASR：老师开始讲解前序遍历的访问顺序。
- [02:08] OCR：课件显示”根节点 → 左子树 → 右子树”。
- [08:42] ASR + OCR：对比递归调用栈与显式栈实现。

## 学习建议
- 用同一棵树手动推演三种遍历顺序。
- 分别实现递归版与迭代版，重点理解栈中保存的状态。`

export const DEMO_DEEP_RESULT = {
  executiveSummary: '这个视频系统讲解了二叉树三种遍历顺序的核心原理，揭示了递归与迭代实现背后的统一思维模型——调用栈的本质是”保存待访问状态”，而遍历顺序的差异仅在于”何时处理当前节点”。',
  context: '二叉树遍历是数据结构课程的核心内容，也是理解递归思维、栈机制和树形结构算法的基础。理解三种遍历顺序的差异和统一性，对于后续学习图算法、表达式求值等高级话题至关重要。',
  keyInsights: [
    {
      insight: '前序遍历的”根-左-右”顺序天然适合构建树的序列化副本',
      reasoning: '因为前序遍历先访问根节点，所以序列化后的第一个元素就是树的根，这与反序列化时”第一个元素是根”的需求完美契合。这使得前序遍历成为树的序列化和反序列化的首选方案。',
      evidence: [
        { timestampMs: 125000, source: 'ASR', content: '前序遍历的第一个节点就是整棵树的根节点，这为我们构建树的序列化表示提供了天然的便利。', claim: '前序遍历适合序列化' },
        { timestampMs: 132000, source: 'OCR', content: '前序遍历：根 → 左子树 → 右子树', claim: '课件展示前序遍历顺序' }
      ],
      implications: '在实际工程中，当需要持久化或传输树结构时，应优先选择前序遍历。这一原理也广泛应用于文件系统遍历、DOM 树序列化等场景。',
      relatedConcepts: ['序列化与反序列化', 'DFS 优先搜索', '文件系统遍历']
    },
    {
      insight: '中序遍历在 BST 上产生有序序列，揭示了”遍历顺序”与”数据语义”的深层关联',
      reasoning: 'BST 的左子树 < 根 < 右子树的性质，与中序遍历”左-根-右”的访问顺序相结合，自然产生升序序列。这不是巧合，而是数据结构设计与遍历策略协同的结果。',
      evidence: [
        { timestampMs: 245000, source: 'ASR', content: '如果你对一棵二叉搜索树做中序遍历，你会得到一个排好序的序列，这是 BST 最重要的性质之一。', claim: 'BST 中序遍历得到有序序列' },
        { timestampMs: 252000, source: 'ASR+OCR', content: '中序遍历结果：2, 5, 8, 10, 12, 15 —— 严格的升序排列', claim: '课件展示有序遍历结果' }
      ],
      implications: '这一性质使得 BST 成为实现有序集合（如 Java 的 TreeSet）的首选数据结构。理解这个原理有助于在需要有序数据时选择合适的底层结构。',
      relatedConcepts: ['二叉搜索树', '有序集合', '平衡树', '红黑树']
    },
    {
      insight: '递归与迭代实现的本质统一：都依赖”栈”来保存待访问节点',
      reasoning: '递归调用栈和迭代显式栈在功能上是等价的——它们都在”记住回去的路”。递归写法更简洁是因为编译器自动管理了调用栈，而迭代写法更显式地展示了底层机制。',
      evidence: [
        { timestampMs: 522000, source: 'ASR+OCR', content: '迭代遍历使用显式栈保存待访问节点，时间复杂度仍为 O(n)，额外空间复杂度为 O(h)。', claim: '递归与迭代的空间复杂度分析' }
      ],
      implications: '理解这种统一性有助于在面试中灵活切换两种写法，也能帮助理解尾递归优化等编译器技术。在实际开发中，递归写法更推荐（除非栈深度可能超限）。',
      relatedConcepts: ['调用栈', '尾递归优化', '栈溢出', '空间复杂度']
    },
    {
      insight: '遍历顺序的选择取决于”何时需要处理当前节点”这一决策',
      reasoning: '三种遍历的差异本质上是一个问题：在处理子树之前还是之后处理当前节点？前序是”先处理当前”，后序是”先处理完所有子节点”，中序是”先处理左子树再处理当前再处理右子树”。',
      evidence: [
        { timestampMs: 380000, source: 'ASR', content: '选择哪种遍历方式，取决于你在什么时候需要”消费”当前节点的信息。', claim: '遍历选择的核心决策' }
      ],
      implications: '这种思维模型可以推广到任何树形结构的遍历决策——无论是目录树、组织架构树还是语法树。',
      relatedConcepts: ['访问者模式', '语法树遍历', '目录树遍历']
    }
  ],
  criticalAnalysis: {
    strengths: [
      '论证逻辑清晰，从三种遍历的定义出发，逐步深入到实现细节和复杂度分析',
      '使用了具体的树形示例来可视化遍历过程，降低了理解难度',
      '对比了递归与迭代两种实现，帮助学生建立统一的思维模型'
    ],
    limitations: [
      '未讨论平衡树（AVL、红黑树）的退化场景，实际应用中 BST 可能退化为链表',
      '缺少对大规模数据下遍历性能的实际测量数据',
      '未涉及 Morris 遍历（O(1) 空间复杂度的遍历方式）这一高级话题'
    ],
    alternativePerspectives: [
      '有学者主张从”函数式编程”角度理解遍历——将遍历视为 fold/reduce 操作',
      '也有教学设计认为应先讲后序遍历（表达式求值的直觉更强），再讲前序和中序'
    ]
  },
  actionableTakeaways: [
    {
      takeaway: '掌握”遍历顺序 = 何时处理当前节点”的决策模型',
      application: '遇到新的树形结构遍历需求时，先问自己”我需要在什么时机处理当前节点”，然后选择合适的遍历顺序。',
      nextSteps: ['用同一棵 5 节点的树手动推演三种遍历顺序', '尝试写出后序遍历的递归和迭代版本'],
      evidence: [
        { timestampMs: 380000, source: 'ASR', content: '选择哪种遍历方式，取决于你在什么时候需要消费当前节点的信息。', claim: '遍历决策模型' }
      ]
    },
    {
      takeaway: '理解 BST 中序遍历的有序性，在需要有序数据时优先考虑 TreeSet/TreeMap',
      application: '当你的业务需求是”维护一个有序集合，支持快速查找、插入、删除”时，直接使用 Java 的 TreeSet 或 TreeMap，它们底层基于红黑树（BST 的平衡变体）。',
      nextSteps: ['阅读 Java TreeSet 源码，理解其底层实现', '对比 HashMap 与 TreeMap 的性能特征'],
      evidence: [
        { timestampMs: 245000, source: 'ASR', content: '对 BST 做中序遍历会得到有序序列，这是 BST 最重要的性质之一。', claim: 'BST 有序性' }
      ]
    }
  ],
  keyTerms: [
    { term: 'BST', definition: 'Binary Search Tree，二叉搜索树。每个节点的左子树所有值 < 该节点值 < 右子树所有值。', context: '视频中用于演示中序遍历产生有序序列的数据结构。' },
    { term: '前序遍历', definition: '按"根→左子树→右子树"的顺序访问树中每个节点。适合树的序列化和副本构建。' },
    { term: '中序遍历', definition: '按"左子树→根→右子树"的顺序访问。在 BST 上产生升序序列。' },
    { term: '后序遍历', definition: '按"左子树→右子树→根"的顺序访问。适合表达式求值和资源释放。' },
    { term: '递归', definition: '函数直接或间接调用自身的编程技巧。需要基线条件防止无限递归。', context: '视频中对比了递归与迭代两种遍历实现。' },
    { term: '显式栈', definition: '用数据结构（如数组、链表）手动管理的栈，替代编译器自动管理的调用栈。', context: '迭代版遍历使用显式栈来模拟递归调用栈的行为。' },
    { term: 'ReAct 范式', definition: 'Reasoning + Acting 范式，Agent 交替进行推理和动作。是早期 Agent 设计的主流模式。' },
    { term: 'Reflection 范式', definition: '在 ReAct 基础上增加"结果自检"环节，Agent 会评估自己的输出并迭代改进。' }
  ]
}
