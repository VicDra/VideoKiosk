import React from 'react';

/**
 * Базовая поверхность-контейнер с мягкой тенью и скруглением.
 */
export interface CardProps {
  children: React.ReactNode;
  /** Приподнятая тень (--shadow-md) вместо обычной. */
  elevated?: boolean;
  /** Внутренний отступ. По умолчанию var(--space-6). */
  padding?: string;
  style?: React.CSSProperties;
}

export function Card(props: CardProps): JSX.Element;
