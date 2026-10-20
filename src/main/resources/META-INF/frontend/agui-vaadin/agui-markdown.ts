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

/** 代码块动作：copy | insert | replace */
export type CodeAction = 'copy' | 'insert' | 'replace';

/**
 * 给已渲染气泡中的每个 <pre> 加一行动作工具栏（幂等：已处理的块跳过）。
 * 在 DOMPurify 之后执行，按钮不进入消毒流程；点击由宿主元素委托处理。
 */
export function decorateCodeBlocks(
  root: ParentNode,
  actions: CodeAction[],
  labels: Record<CodeAction, string>,
): void {
  if (actions.length === 0) return;
  root.querySelectorAll<HTMLPreElement>('.bubble pre:not([data-code-decorated])').forEach((pre) => {
    pre.setAttribute('data-code-decorated', '');
    const bar = document.createElement('div');
    bar.className = 'code-actions';
    bar.setAttribute('part', 'code-actions');
    for (const action of actions) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.dataset.codeAction = action;
      btn.textContent = labels[action];
      bar.appendChild(btn);
    }
    pre.insertAdjacentElement('afterend', bar);
  });
}

/** 从 <pre><code class="language-x"> 取语言名。 */
export function codeLanguage(pre: HTMLPreElement): string {
  const cls = pre.querySelector('code')?.className ?? '';
  return /language-(\S+)/.exec(cls)?.[1] ?? '';
}
