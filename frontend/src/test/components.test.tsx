import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { AttemptQuestion } from '../api/types';
import { CountdownTimer, timerLevel } from '../components/assessment/CountdownTimer';
import { emptyAnswer, isAnswered, QuestionView } from '../components/assessment/QuestionView';
import { StatusChip } from '../components/common/StatusChip';
import { parseInvitees } from '../pages/staff/InvitePage';
import { formatClock, formatPercent, formatScore } from '../utils/format';
import { renderWithProviders } from './utils';

describe('CountdownTimer', () => {
  it('escalates warning levels at 5 minutes and 1 minute', () => {
    expect(timerLevel(1200)).toBe('normal');
    expect(timerLevel(300)).toBe('warning');
    expect(timerLevel(45)).toBe('critical');
  });

  it('renders an accessible timer', () => {
    renderWithProviders(<CountdownTimer secondsLeft={3725} />);
    const timer = screen.getByRole('timer');
    expect(timer).toHaveTextContent('01:02:05');
    expect(timer).toHaveAttribute('data-level', 'normal');
  });
});

describe('QuestionView', () => {
  const mcq: AttemptQuestion = {
    questionId: 7, index: 1, type: 'MCQ_SINGLE', title: 'Pick one', body: 'Which?', marks: 2,
    options: [{ id: 11, text: 'Alpha' }, { id: 12, text: 'Beta' }],
  };

  it('renders options without any answer key and reports the selection', async () => {
    const onChange = vi.fn();
    renderWithProviders(<QuestionView question={mcq} answer={emptyAnswer()} onChange={onChange} />);
    expect(screen.getByText('Pick one')).toBeInTheDocument();
    expect(screen.queryByText(/correct/i)).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Beta'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ selectedOptionIds: [12] }));
  });

  it('supports multiple-select toggling', async () => {
    const onChange = vi.fn();
    const multi: AttemptQuestion = { ...mcq, type: 'MCQ_MULTI' };
    renderWithProviders(<QuestionView question={multi} answer={{ selectedOptionIds: [11] }} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('Beta'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ selectedOptionIds: [11, 12] }));
  });

  it('knows when a question is answered', () => {
    expect(isAnswered(emptyAnswer())).toBe(false);
    expect(isAnswered({ selectedOptionIds: [], textAnswer: '  ' })).toBe(false);
    expect(isAnswered({ selectedOptionIds: [], codeAnswer: 'x' })).toBe(true);
  });
});

describe('StatusChip', () => {
  it('humanises status values', () => {
    renderWithProviders(<StatusChip status="PENDING_REVIEW" />);
    expect(screen.getByText('Pending review')).toBeInTheDocument();
  });
});

describe('invitee parsing', () => {
  it('accepts plain and named addresses, de-duplicates and reports invalid entries', () => {
    const { valid, invalid } = parseInvitees('jane@acme.io, John Smith <JOHN@acme.io>\njane@acme.io\nnot-an-email');
    expect(valid).toEqual([{ email: 'jane@acme.io' }, { email: 'john@acme.io', fullName: 'John Smith' }]);
    expect(invalid).toEqual(['not-an-email']);
  });
});

describe('formatting', () => {
  it('formats clocks, scores and percentages', () => {
    expect(formatClock(65)).toBe('01:05');
    expect(formatClock(-3)).toBe('00:00');
    expect(formatScore(12.5, 16)).toBe('12.5 / 16');
    expect(formatPercent(66.67)).toBe('66.67%');
    expect(formatPercent(null)).toBe('—');
  });
});
