import React from 'react';

export type ConnectionStatus = 'online' | 'busy' | 'waiting' | 'offline';

/**
 * Индикатор статуса с пульсирующей точкой — статус оператора/соединения.
 * Пульсация активна для 'online' и 'waiting' (с учётом prefers-reduced-motion).
 */
export interface StatusPillProps {
  status?: ConnectionStatus;
  /** Переопределить текст. По умолчанию подбирается по статусу. */
  label?: string;
}

export function StatusPill(props: StatusPillProps): JSX.Element;
