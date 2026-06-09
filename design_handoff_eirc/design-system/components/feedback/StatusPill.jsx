import React from 'react';

/**
 * StatusPill — индикатор статуса с пульсирующей точкой.
 * status: online | busy | waiting | offline. Текст задаётся через label
 * или берётся по умолчанию для статуса.
 */
export function StatusPill({ status = 'offline', label }) {
  const map = {
    online:  { color: 'var(--status-online)', text: 'На связи' },
    busy:    { color: 'var(--status-busy)', text: 'Занят' },
    waiting: { color: 'var(--status-waiting)', text: 'Ожидание' },
    offline: { color: 'var(--status-offline)', text: 'Не в сети' },
  };
  const s = map[status];
  const pulse = status === 'online' || status === 'waiting';

  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)',
      padding: '6px var(--space-3) 6px var(--space-3)', borderRadius: 'var(--radius-pill)',
      background: 'var(--surface)', border: '1px solid var(--border)',
      font: 'var(--type-label)', color: 'var(--text-body)',
    }}>
      <span style={{ position: 'relative', width: 10, height: 10, display: 'inline-flex' }}>
        {pulse && (
          <span style={{
            position: 'absolute', inset: 0, borderRadius: '50%', background: s.color, opacity: 0.5,
            animation: 'irc-pulse 1.6s var(--ease-out) infinite',
          }} />
        )}
        <span style={{ position: 'relative', width: 10, height: 10, borderRadius: '50%', background: s.color }} />
      </span>
      {label || s.text}
      <style>{`@keyframes irc-pulse { 0% { transform: scale(1); opacity: .5 } 70% { transform: scale(2.4); opacity: 0 } 100% { opacity: 0 } }
        @media (prefers-reduced-motion: reduce) { [style*="irc-pulse"] { animation: none !important } }`}</style>
    </span>
  );
}
