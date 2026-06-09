import React from 'react';

/**
 * Badge — небольшой тонированный бейдж/тег. Тон: info | success | danger |
 * warning | neutral. Опциональная точка-индикатор слева.
 */
export function Badge({ children, tone = 'neutral', dot = false }) {
  const tones = {
    info:    { bg: 'var(--tint-info-bg)', fg: 'var(--tint-info-fg)', dot: 'var(--blue-500)' },
    success: { bg: 'var(--tint-success-bg)', fg: 'var(--tint-success-fg)', dot: 'var(--green-500)' },
    danger:  { bg: 'var(--tint-danger-bg)', fg: 'var(--tint-danger-fg)', dot: 'var(--red-500)' },
    warning: { bg: 'var(--tint-warning-bg)', fg: 'var(--tint-warning-fg)', dot: 'var(--amber-500)' },
    neutral: { bg: 'var(--surface-sunken)', fg: 'var(--text-body)', dot: 'var(--gray-400)' },
  };
  const t = tones[tone];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)',
      padding: '4px var(--space-3)', borderRadius: 'var(--radius-pill)',
      background: t.bg, color: t.fg, font: 'var(--type-caption)', fontWeight: 600,
      whiteSpace: 'nowrap',
    }}>
      {dot && <span style={{ width: 8, height: 8, borderRadius: '50%', background: t.dot }} />}
      {children}
    </span>
  );
}
