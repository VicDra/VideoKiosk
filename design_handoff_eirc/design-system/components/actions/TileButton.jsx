import React from 'react';

/**
 * TileButton — крупная сенсорная плитка главного меню киоска.
 * Иконка в цветном кружке + крупный заголовок + поясняющая подпись.
 * Высота ≥ 180px, вся плитка — одна тач-цель. Press-состояние: scale(0.97).
 */
export function TileButton({
  title,
  subtitle,
  icon = null,
  tone = 'brand',
  onClick,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const [active, setActive] = React.useState(false);

  const tones = {
    brand:  { ring: 'var(--blue-50)', iconBg: 'var(--blue-600)', iconFg: '#fff', accent: 'var(--blue-600)' },
    green:  { ring: 'var(--green-50)', iconBg: 'var(--green-500)', iconFg: '#fff', accent: 'var(--green-600)' },
    amber:  { ring: 'var(--amber-50)', iconBg: 'var(--amber-500)', iconFg: '#fff', accent: 'var(--amber-700)' },
  };
  const t = tones[tone];

  const style = {
    display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 'var(--space-5)',
    textAlign: 'left', width: '100%', minHeight: '200px',
    padding: 'var(--space-7)',
    background: 'var(--surface)',
    border: `2px solid ${hover ? t.accent : 'var(--border)'}`,
    borderRadius: 'var(--radius-xl)',
    boxShadow: hover ? 'var(--shadow-lg)' : 'var(--shadow-sm)',
    cursor: 'pointer',
    transform: active ? 'scale(0.97)' : 'none',
    transition: 'transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base)',
  };

  const iconWrap = {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    width: '88px', height: '88px', borderRadius: 'var(--radius-lg)',
    background: t.iconBg, color: t.iconFg, flexShrink: 0,
  };

  return (
    <button
      type="button" style={style} onClick={onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => { setHover(false); setActive(false); }}
      onMouseDown={() => setActive(true)} onMouseUp={() => setActive(false)}
      {...rest}
    >
      <span style={iconWrap}>{icon}</span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        <span style={{ font: 'var(--type-kiosk-tile)', color: 'var(--text-strong)' }}>{title}</span>
        {subtitle && <span style={{ font: 'var(--type-kiosk-body)', color: 'var(--text-muted)' }}>{subtitle}</span>}
      </span>
    </button>
  );
}
