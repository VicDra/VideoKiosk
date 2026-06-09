import React from 'react';

/**
 * Input — текстовое поле ввода с меткой, подсказкой и состоянием ошибки.
 * Используется в настройках подключения оператора (IP, порт, ICE-серверы).
 */
export function Input({
  label,
  hint,
  error,
  value,
  placeholder,
  type = 'text',
  prefix = null,
  disabled = false,
  onChange,
  id,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const inputId = id || React.useId();

  const wrap = { display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' };
  const labelStyle = { font: 'var(--type-label)', color: 'var(--text-strong)' };
  const fieldStyle = {
    display: 'flex', alignItems: 'center', gap: 'var(--space-2)',
    height: '48px', padding: '0 var(--space-4)',
    background: disabled ? 'var(--surface-sunken)' : 'var(--surface)',
    border: `1px solid ${error ? 'var(--action-danger)' : focus ? 'var(--border-focus)' : 'var(--border-strong)'}`,
    borderRadius: 'var(--radius-md)',
    boxShadow: focus ? 'var(--focus-ring)' : 'none',
    transition: 'border-color var(--dur-fast), box-shadow var(--dur-fast)',
  };
  const inputStyle = {
    flex: 1, border: 'none', outline: 'none', background: 'transparent',
    font: 'var(--type-body)', color: 'var(--text-strong)', minWidth: 0,
  };

  return (
    <div style={wrap}>
      {label && <label htmlFor={inputId} style={labelStyle}>{label}</label>}
      <div style={fieldStyle}>
        {prefix && <span style={{ color: 'var(--text-muted)', font: 'var(--type-body)' }}>{prefix}</span>}
        <input
          id={inputId} type={type} value={value} placeholder={placeholder} disabled={disabled}
          onChange={onChange} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
          style={inputStyle} {...rest}
        />
      </div>
      {error
        ? <span style={{ font: 'var(--type-caption)', color: 'var(--tint-danger-fg)' }}>{error}</span>
        : hint && <span style={{ font: 'var(--type-caption)', color: 'var(--text-muted)' }}>{hint}</span>}
    </div>
  );
}
