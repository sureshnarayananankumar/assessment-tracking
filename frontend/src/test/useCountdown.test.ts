import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCountdown } from '../hooks/useCountdown';

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T10:00:00Z'));
  });
  afterEach(() => vi.useRealTimers());

  it('counts down to the server deadline and fires warnings and expiry once', () => {
    const onWarning = vi.fn();
    const onExpire = vi.fn();
    const { result } = renderHook(() =>
      useCountdown({
        deadlineAt: '2026-01-01T10:02:00Z',
        serverTime: '2026-01-01T10:00:00Z',
        warnings: [60],
        onWarning,
        onExpire,
      }),
    );
    expect(result.current.secondsLeft).toBe(120);

    act(() => { vi.advanceTimersByTime(61_000); });
    expect(result.current.secondsLeft).toBeLessThanOrEqual(60);
    expect(onWarning).toHaveBeenCalledTimes(1);
    expect(onExpire).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(60_000); });
    expect(result.current.secondsLeft).toBe(0);
    expect(result.current.expired).toBe(true);
    expect(onExpire).toHaveBeenCalledTimes(1);

    act(() => { vi.advanceTimersByTime(5_000); });
    expect(onExpire).toHaveBeenCalledTimes(1);
    expect(onWarning).toHaveBeenCalledTimes(1);
  });

  it('corrects for a skewed local clock using the server time', () => {
    // Local clock is 30 s behind the server: the remaining time must still be measured on server time.
    const { result } = renderHook(() =>
      useCountdown({ deadlineAt: '2026-01-01T10:01:30Z', serverTime: '2026-01-01T10:00:30Z' }),
    );
    expect(result.current.secondsLeft).toBe(60);
  });

  it('does not tick when disabled', () => {
    const onExpire = vi.fn();
    renderHook(() =>
      useCountdown({ deadlineAt: '2026-01-01T09:00:00Z', serverTime: '2026-01-01T10:00:00Z', onExpire, enabled: false }),
    );
    act(() => { vi.advanceTimersByTime(3_000); });
    expect(onExpire).not.toHaveBeenCalled();
  });
});
