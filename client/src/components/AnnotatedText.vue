<template>
  <span class="annotated-text">
    <template v-for="(segment, i) in segments" :key="i">
      <TermAnnotation
        v-if="segment.term"
        :term="segment.text"
        :definition="segment.definition"
        :context="segment.context || ''"
      />
      <template v-else>{{ segment.text }}</template>
    </template>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import TermAnnotation from './TermAnnotation.vue'

const props = defineProps({
  text: { type: String, required: true },
  terms: { type: Array, default: () => [] }
})

// 将文本拆分为纯文本和术语标注交替的片段
const segments = computed(() => {
  if (!props.text || !props.terms?.length) return [{ text: props.text || '' }]

  // 按术语长度降序排列，优先匹配更长的术语（避免"BST"被"二叉搜索树 BST"中的子串抢先匹配）
  const sorted = [...props.terms]
    .filter(t => t.term && t.definition)
    .sort((a, b) => b.term.length - a.term.length)

  if (!sorted.length) return [{ text: props.text }]

  // 在文本中找到所有术语出现的位置
  const matches = []
  const text = props.text

  for (const term of sorted) {
    const regex = new RegExp(escapeRegex(term.term), 'g')
    let match
    while ((match = regex.exec(text)) !== null) {
      const start = match.index
      const end = start + match[0].length
      // 检查是否与已有匹配重叠
      const overlaps = matches.some(m => start < m.end && end > m.start)
      if (!overlaps) {
        matches.push({
          start,
          end,
          text: match[0],
          term: term.term,
          definition: term.definition,
          context: term.context
        })
      }
    }
  }

  if (!matches.length) return [{ text: props.text }]

  // 按起始位置排序
  matches.sort((a, b) => a.start - b.start)

  // 拆分为交替的纯文本和术语片段
  const result = []
  let cursor = 0
  for (const m of matches) {
    if (m.start > cursor) {
      result.push({ text: text.slice(cursor, m.start) })
    }
    result.push({ text: m.text, term: m.term, definition: m.definition, context: m.context })
    cursor = m.end
  }
  if (cursor < text.length) {
    result.push({ text: text.slice(cursor) })
  }

  return result
})

function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<style scoped>
.annotated-text {
  display: inline;
}
</style>
