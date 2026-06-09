import React from 'react';

export type BadgeTone = 'info' | 'success' | 'danger' | 'warning' | 'neutral';

/**
 * Небольшой тонированный бейдж/тег с опциональной точкой-индикатором.
 */
export interface BadgeProps {
  children: React.ReactNode;
  tone?: BadgeTone;
  /** Показать цветную точку слева. */
  dot?: boolean;
}

export function Badge(props: BadgeProps): JSX.Element;
