import { useEffect, useRef, useState } from 'react';

export interface CountdownOptions {
  /** Server-computed deadline (ISO instant). The server remains the source of truth. */
  deadlineAt: string;
  /** Server time when the view was loaded; used to correct for local clock skew. */
  serverTime: string;
  /** Seconds-remaining thresholds that trigger onWarning once each (e.g. [300, 60]). */
  warnings?: number[];
  onWarning?: (secondsLeft: number) => void;
  onExpire?: () => void;
  enabled?: boolean;
}

/**
 * Display-only countdown synchronised to the server clock. It never decides whether an answer is
 * accepted - the backend enforces the deadline - but it triggers warnings and auto-submission UX.
 */
export function useCountdown({ deadlineAt, serverTime, warnings = [], onWarning, onExpire, enabled = true }: CountdownOptions) {
  const offsetRef = useRef(0);
  const firedRef = useRef(new Set<number>());
  const expiredRef = useRef(false);
  const callbacks = useRef({ onWarning, onExpire });
  callbacks.current = { onWarning, onExpire };

  // offset = server clock - local clock, measured once when the attempt is loaded
  useEffect(() => {
    offsetRef.current = new Date(serverTime).getTime() - Date.now();
  }, [serverTime]);

  const compute = () => Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - (Date.now() + offsetRef.current)) / 1000));
  const [secondsLeft, setSecondsLeft] = useState(() => {
    offsetRef.current = new Date(serverTime).getTime() - Date.now();
    return compute();
  });

  useEffect(() => {
    firedRef.current = new Set(warnings.filter((w) => compute() <= w)); // don't re-fire after a reload
    expiredRef.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadlineAt]);

  useEffect(() => {
    if (!enabled) return;
    const tick = () => {
      const left = compute();
      setSecondsLeft(left);
      for (const w of warnings) {
        if (left <= w && left > 0 && !firedRef.current.has(w)) {
          firedRef.current.add(w);
          callbacks.current.onWarning?.(left);
        }
      }
      if (left <= 0 && !expiredRef.current) {
        expiredRef.current = true;
        callbacks.current.onExpire?.();
      }
    };
    tick();
    const id = window.setInterval(tick, 500);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadlineAt, enabled, warnings.join(',')]);

  return { secondsLeft, expired: secondsLeft <= 0 };
}
