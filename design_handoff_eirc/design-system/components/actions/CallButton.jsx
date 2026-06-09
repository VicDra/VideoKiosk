import React from 'react';

/**
 * CallButton — круглая кнопка управления видеозвонком (≥ 88px).
 * Варианты: accept (зелёная) | end (красная) | neutral (стекло на тёмном фоне).
 * Опциональная подпись под кнопкой.
 */
export function CallButton({
  variant = 'neutral',
  icon = null,
  label = null,
  size = 88,
  onClick,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const [active, setActive] = React.useState(false);

  const palette = {
    accept:  { bg: 'var(--action-accept)', bgH: 'var(--action-accept-hover)', fg: '#fff', shadow: '0 10px 28px rgba(31,164,99,.45)' },
    end:     { bg: 'var(--action-danger)', bgH: 'var(--action-danger-hover)', fg: '#fff', shadow: '0 10px 28px rgba(224,56,45,.45)' },
    neutral: { bg: 'rgba(255,255,255,0.14)', bgH: 'rgba(255,255,255,0.26)', fg: '#fff', shadow: 'none' },
  };
  const p = palette[variant];

  const btn = {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    width: `${size}px`, height: `${size}px`, borderRadius: 'var(--radius-pill)',
    background: hover ? p.bgH : p.bg, color: p.fg,
    border: variant === 'neutral' ? '1px solid rgba(255,255,255,0.25)' : 'none',
    boxShadow: p.shadow, cursor: 'pointer',
    transform: active ? 'scale(0.92)' : 'none',
    transition: 'background var(--dur-fast) var(--ease-out), transform var(--dur-fast)',
    backdropFilter: variant === 'neutral' ? 'blur(8px)' : 'none',
  };

  return (
    <span style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)' }}>
      <button
        type="button" style={btn} onClick={onClick} aria-label={label || undefined}
        onMouseEnter={() => setHover(true)} onMouseLeave={() => { setHover(false); setActive(false); }}
        onMouseDown={() => setActive(true)} onMouseUp={() => setActive(false)}
        {...rest}
      >
        {icon}
      </button>
      {label && <span style={{ font: 'var(--type-label)', color: 'var(--text-on-dark)' }}>{label}</span>}
    </span>
  );
}
