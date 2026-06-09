import React from 'react';

/**
 * QueueItem — строка очереди вызовов в приложении оператора.
 * Показывает источник (киоск), местоположение, время ожидания и статус.
 * active=true выделяет текущий обслуживаемый вызов.
 */
export function QueueItem({ kiosk, location, waiting, position, active = false, onClick }) {
  const [hover, setHover] = React.useState(false);
  return (
    <button
      type="button" onClick={onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', alignItems: 'center', gap: 'var(--space-4)', width: '100%', textAlign: 'left',
        padding: 'var(--space-4)',
        background: active ? 'var(--action-primary-soft)' : hover ? 'var(--surface-muted)' : 'var(--surface)',
        border: `1px solid ${active ? 'var(--blue-300)' : 'var(--border)'}`,
        borderRadius: 'var(--radius-md)', cursor: 'pointer',
        transition: 'background var(--dur-fast)',
      }}
    >
      <span style={{
        flexShrink: 0, width: 44, height: 44, borderRadius: 'var(--radius-sm)',
        background: active ? 'var(--blue-600)' : 'var(--surface-sunken)',
        color: active ? '#fff' : 'var(--text-muted)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        font: 'var(--type-h3)', fontWeight: 700,
      }}>{position}</span>
      <span style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ font: 'var(--type-label)', color: 'var(--text-strong)', fontWeight: 600 }}>{kiosk}</span>
        <span style={{ font: 'var(--type-caption)', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{location}</span>
      </span>
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)',
        font: 'var(--type-caption)', fontWeight: 600,
        color: active ? 'var(--blue-700)' : 'var(--amber-700)',
      }}>{active ? 'идёт разговор' : waiting}</span>
    </button>
  );
}
