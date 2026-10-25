import { useEffect, useRef } from 'react';
import type { ActivityEventInput, ActivityEventType } from '../api/types';

interface Options {
  enabled: boolean;
  /** Receives every detected event; the caller batches them to the server. */
  onEvent: (event: ActivityEventInput) => void;
  /** Called when the candidate comes back after leaving (tab visible or window focused again). */
  onReturn?: (awayMs: number) => void;
}

/**
 * Detects tab visibility changes (Page Visibility API) and window focus loss.
 *
 * LIMITATIONS (by design of the web platform): a web page cannot see other applications, other
 * browsers, other devices, screen sharing, or a second monitor, and some OS/browser actions
 * (notifications, password managers, dev tools) also trigger blur. Events are therefore
 * indicators for human review - never proof of misconduct.
 */
export function useActivityMonitor({ enabled, onEvent, onReturn }: Options) {
  const hiddenAt = useRef<number | null>(null);
  const blurredAt = useRef<number | null>(null);
  const cb = useRef({ onEvent, onReturn });
  cb.current = { onEvent, onReturn };

  useEffect(() => {
    if (!enabled) return;
    const emit = (type: ActivityEventType, durationMs?: number, details?: string) =>
      cb.current.onEvent({ type, occurredAt: new Date().toISOString(), durationMs, details });

    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        hiddenAt.current = Date.now();
        emit('TAB_HIDDEN');
      } else if (hiddenAt.current !== null) {
        const away = Date.now() - hiddenAt.current;
        hiddenAt.current = null;
        emit('TAB_VISIBLE', away);
        cb.current.onReturn?.(away);
      }
    };
    const onBlur = () => {
      blurredAt.current = Date.now();
      emit('WINDOW_BLUR');
    };
    const onFocus = () => {
      if (blurredAt.current === null) return;
      const away = Date.now() - blurredAt.current;
      blurredAt.current = null;
      emit('WINDOW_FOCUS', away);
      // When the tab was hidden, the visibility handler already reported the return.
      if (document.visibilityState === 'visible' && hiddenAt.current === null && away > 300) {
        cb.current.onReturn?.(away);
      }
    };
    const onCopy = () => emit('COPY_ATTEMPT');
    const onPaste = () => emit('PASTE_ATTEMPT');

    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('blur', onBlur);
    window.addEventListener('focus', onFocus);
    document.addEventListener('copy', onCopy);
    document.addEventListener('paste', onPaste);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('blur', onBlur);
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('copy', onCopy);
      document.removeEventListener('paste', onPaste);
    };
  }, [enabled]);
}
