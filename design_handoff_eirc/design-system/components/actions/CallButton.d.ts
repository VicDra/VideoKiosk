import React from 'react';

export type CallButtonVariant = 'accept' | 'end' | 'neutral';

/**
 * Круглая кнопка управления видеозвонком (микрофон, камера, принять, завершить).
 * Размещается на тёмном фоне экрана видео. Минимум 88px — крупная тач-цель.
 */
export interface CallButtonProps {
  /** 'accept' (зелёная), 'end' (красная), 'neutral' (стекло на тёмном фоне). */
  variant?: CallButtonVariant;
  /** Иконка (Lucide). */
  icon?: React.ReactNode;
  /** Подпись под кнопкой (также используется как aria-label). */
  label?: string;
  /** Диаметр в px. По умолчанию 88. */
  size?: number;
  onClick?: () => void;
}

export function CallButton(props: CallButtonProps): JSX.Element;
