import React from 'react';

/**
 * Button — основная кнопка действия (десктоп оператора и формы).
 * Варианты: primary | secondary | ghost | accept | danger.
 * Размеры: md | lg. Полностью самодостаточна (инлайновые стили + CSS-переменные).
 */
export function Button({
  children,
  variant = 'primary',
  size = 'md',
  icon = null,
  iconRight = null,
  fullWidth = false,
  disabled = false,
  onClick,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const [active, setActive] = React.useState(false);

  const sizes = {
    md: { padding: '0 var(--space-5)', height: '44px', font: 'var(--type-label)', radius: 'var(--radius-md)', gap: 'var(--space-2)' },
    lg: { padding: '0 var(--space-6)', height: '52px', font: 'var(--type-h3)', radius: 'var(--radius-md)', gap: 'var(--space-3)' },
  };

  const palette = {
    primary: { bg: 'var(--action-primary)', bgH: 'var(--action-primary-hover)', bgA: 'var(--action-primary-active)', fg: '#fff', border: 'transparent', shadow: 'var(--shadow-brand)' },
    accept:  { bg: 'var(--action-accept)', bgH: 'var(--action-accept-hover)', bgA: 'var(--green-700)', fg: '#fff', border: 'transparent', shadow: '0 8px 22px rgba(31,164,99,.30)' },
    danger:  { bg: 'var(--action-danger)', bgH: 'var(--action-danger-hover)', bgA: 'var(--red-700)', fg: '#fff', border: 'transparent', shadow: '0 8px 22px rgba(224,56,45,.28)' },
    secondary:{ bg: 'var(--surface)', bgH: 'var(--surface-muted)', bgA: 'var(--surface-sunken)', fg: 'var(--text-strong)', border: 'var(--border-strong)', shadow: 'var(--shadow-xs)' },
    ghost:   { bg: 'transparent', bgH: 'var(--surface-muted)', bgA: 'var(--surface-sunken)', fg: 'var(--action-primary)', border: 'transparent', shadow: 'none' },
  };

  const s = sizes[size];
  const p = palette[variant];
  const bg = disabled ? 'var(--gray-200)' : (active ? p.bgA : hover ? p.bgH : p.bg);

  const style = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    gap: s.gap, padding: s.padding, height: s.height, width: fullWidth ? '100%' : 'auto',
    font: s.font, color: disabled ? 'var(--gray-400)' : p.fg,
    background: bg,
    border: `1px solid ${disabled ? 'var(--border)' : p.border}`,
    borderRadius: s.radius,
    boxShadow: disabled || variant === 'ghost' ? 'none' : (hover ? p.shadow : 'var(--shadow-xs)'),
    cursor: disabled ? 'not-allowed' : 'pointer',
    transform: active && !disabled ? 'translateY(1px)' : 'none',
    transition: 'background var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), transform var(--dur-fast)',
    whiteSpace: 'nowrap',
  };

  return (
    <button
      type="button" style={style} disabled={disabled} onClick={onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => { setHover(false); setActive(false); }}
      onMouseDown={() => setActive(true)} onMouseUp={() => setActive(false)}
      {...rest}
    >
      {icon}
      {children}
      {iconRight}
    </button>
  );
}
