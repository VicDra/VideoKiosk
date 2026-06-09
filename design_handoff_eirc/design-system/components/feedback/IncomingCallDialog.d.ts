import React from 'react';

/**
 * Карточка входящего видеозвонка у оператора — с кнопками «Принять»/«Отклонить».
 *
 * @startingPoint section="Operator" subtitle="Уведомление о входящем вызове" viewport="480x420"
 */
export interface IncomingCallDialogProps {
  /** Источник, например «Киоск №1». */
  kiosk: string;
  /** Местоположение источника. */
  location: string;
  /** Сколько вызовов ещё ждёт в очереди (0 — не показывать). */
  count?: number;
  onAccept?: () => void;
  onReject?: () => void;
}

export function IncomingCallDialog(props: IncomingCallDialogProps): JSX.Element;
