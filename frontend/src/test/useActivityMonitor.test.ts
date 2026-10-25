import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ActivityEventInput } from '../api/types';
import { useActivityMonitor } from '../hooks/useActivityMonitor';

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('useActivityMonitor', () => {
  afterEach(() => setVisibility('visible'));

  it('reports tab hidden / visible with the time spent away', () => {
    vi.useFakeTimers();
    const events: ActivityEventInput[] = [];
    const onReturn = vi.fn();
    renderHook(() => useActivityMonitor({ enabled: true, onEvent: (e) => events.push(e), onReturn }));

    setVisibility('hidden');
    vi.advanceTimersByTime(4000);
    setVisibility('visible');

    expect(events.map((e) => e.type)).toEqual(['TAB_HIDDEN', 'TAB_VISIBLE']);
    expect(events[1].durationMs).toBeGreaterThanOrEqual(4000);
    expect(onReturn).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it('reports window blur and focus', () => {
    const events: ActivityEventInput[] = [];
    renderHook(() => useActivityMonitor({ enabled: true, onEvent: (e) => events.push(e) }));
    window.dispatchEvent(new Event('blur'));
    window.dispatchEvent(new Event('focus'));
    expect(events.map((e) => e.type)).toEqual(['WINDOW_BLUR', 'WINDOW_FOCUS']);
    expect(events.every((e) => !Number.isNaN(Date.parse(e.occurredAt)))).toBe(true);
  });

  it('records nothing when monitoring is disabled and cleans up listeners', () => {
    const onEvent = vi.fn();
    const { unmount, rerender } = renderHook(({ enabled }) => useActivityMonitor({ enabled, onEvent }), {
      initialProps: { enabled: false },
    });
    window.dispatchEvent(new Event('blur'));
    expect(onEvent).not.toHaveBeenCalled();

    rerender({ enabled: true });
    window.dispatchEvent(new Event('blur'));
    expect(onEvent).toHaveBeenCalledTimes(1);

    unmount();
    window.dispatchEvent(new Event('blur'));
    expect(onEvent).toHaveBeenCalledTimes(1);
  });
});
