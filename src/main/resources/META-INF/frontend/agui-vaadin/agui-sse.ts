/**
 * AG-UI 运行客户端：POST 请求体，按 text/event-stream 逐条解析 data 帧。
 *
 * 不用 EventSource：它只支持 GET 且不能带请求体。这里用 fetch + ReadableStream，
 * 并通过 AbortSignal 实现"停止生成"。
 */
import type { AgUiEvent, RunAgentInput } from './agui-types.js';

export interface RunOptions {
  url: string;
  input: RunAgentInput;
  headers?: Record<string, string>;
  signal: AbortSignal;
  onEvent: (event: AgUiEvent) => void;
}

/**
 * 流正常结束时 resolve；HTTP 非 2xx 或帧解析失败时 reject；abort 时 reject(AbortError)。
 */
export async function runAgent({ url, input, headers, signal, onEvent }: RunOptions): Promise<void> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(headers ?? {}),
    },
    body: JSON.stringify(input),
    credentials: 'same-origin',
    signal,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`AG-UI endpoint responded ${response.status}${text ? `: ${text}` : ''}`);
  }
  if (!response.body) {
    throw new Error('AG-UI endpoint returned no body');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    // 在整个缓冲区上归一化 CRLF：\r 与 \n 可能被网络分块拆开，只处理单个分块会漏掉跨块的一对。
    // 缓冲区每帧即清空，所以整体 replace 的代价可以忽略。
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n');
    let separator: number;
    while ((separator = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, separator);
      buffer = buffer.slice(separator + 2);
      dispatchFrame(frame, onEvent);
    }
  }
  if (buffer.trim() !== '') {
    dispatchFrame(buffer, onEvent);
  }
}

/** SSE 帧以空行分隔；一帧可含多行 data:，按规范用换行拼接。event:/id:/retry: 对 AG-UI 无意义。 */
export function dispatchFrame(frame: string, onEvent: (event: AgUiEvent) => void): void {
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (dataLines.length === 0) {
    return;
  }
  const payload = dataLines.join('\n');
  if (payload === '' || payload === '[DONE]') {
    return;
  }
  onEvent(JSON.parse(payload) as AgUiEvent);
}
