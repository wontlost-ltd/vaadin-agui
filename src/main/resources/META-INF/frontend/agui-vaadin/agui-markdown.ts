/**
 * Markdown 渲染：marked 解析 + DOMPurify 消毒。
 *
 * 模型输出是不可信内容，任何 HTML 都必须经过消毒再插入 DOM。
 * 流式期间未闭合的代码块或列表由 marked 容错处理，不做额外修补。
 */
import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({ gfm: true, breaks: true, async: false });

const PURIFY_OPTIONS = {
  USE_PROFILES: { html: true },
  ADD_ATTR: ['target', 'rel'],
};

// 外链在新标签打开且不泄露 opener；由 DOMPurify 的钩子统一处理，不依赖 marked 的渲染器
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.hasAttribute('href')) {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

export function renderMarkdown(text: string | null | undefined): string {
  if (!text) {
    return '';
  }
  const html = marked.parse(text) as string;
  return DOMPurify.sanitize(html, PURIFY_OPTIONS);
}

/** 工具参数通常是 JSON；能解析就美化，不能就原样展示。 */
export function prettyJson(text: string | null | undefined): string {
  if (!text) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}
