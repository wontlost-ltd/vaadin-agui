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
      // 每个按钮单独暴露 part，宿主可用 ::part(code-action-copy) 等精确定制
      btn.setAttribute('part', `code-action code-action-${action}`);
      btn.innerHTML = CODE_ACTION_ICONS[action];
      const label = document.createElement('span');
      label.textContent = labels[action];
      btn.appendChild(label);
      bar.appendChild(btn);
    }
    pre.insertAdjacentElement('afterend', bar);
  });
}

/** 代码块动作的内置图标（静态可信 SVG，不含用户内容）。 */
const CODE_ACTION_ICONS: Record<CodeAction, string> = {
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>',
  insert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>',
  replace: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5"/></svg>',
};

/** 从 <pre><code class="language-x"> 取语言名。 */
export function codeLanguage(pre: HTMLPreElement): string {
  const cls = pre.querySelector('code')?.className ?? '';
  return /language-(\S+)/.exec(cls)?.[1] ?? '';
}
