import React from 'react';

/**
 * Строка очереди вызовов в приложении оператора.
 *
 * @startingPoint section="Operator" subtitle="Строка очереди вызовов" viewport="420x80"
 */
export interface QueueItemProps {
  /** Идентификатор источника, например «Киоск №1». */
  kiosk: string;
  /** Местоположение / зал, например «Зал ожидания, 1 этаж». */
  location: string;
  /** Текст времени ожидания, например «ждёт 1:20». */
  waiting?: string;
  /** Порядковый номер в очереди. */
  position: number | string;
  /** Текущий обслуживаемый вызов (выделяется синим). */
  active?: boolean;
  onClick?: () => void;
}

export function QueueItem(props: QueueItemProps): JSX.Element;
