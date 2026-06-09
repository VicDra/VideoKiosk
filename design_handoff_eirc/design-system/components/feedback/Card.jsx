import React from 'react';

/**
 * Card — базовая поверхность-контейнер. padding по умолчанию, мягкая тень,
 * скругление --radius-lg. Может быть «приподнятой» (elevated) или плоской.
 */
export function Card({ children, elevated = false, padding = 'var(--space-6)', style = {}, ...rest }) {
  const base = {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-lg)',
    boxShadow: elevated ? 'var(--shadow-md)' : 'var(--shadow-xs)',
    padding,
    ...style,
  };
  return <div style={base} {...rest}>{children}</div>;
}
