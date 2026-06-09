import React from 'react';

export type TileTone = 'brand' | 'green' | 'amber';

/**
 * Крупная сенсорная плитка главного меню киоска. Вся плитка — одна тач-цель
 * (≥ 200px высотой). Иконка в цветном квадрате, крупный заголовок и подпись.
 *
 * @startingPoint section="Kiosk" subtitle="Плитка главного меню киоска" viewport="700x260"
 */
export interface TileButtonProps {
  /** Крупный заголовок плитки, например «Видеозвонок оператору». */
  title: string;
  /** Поясняющая подпись под заголовком. */
  subtitle?: string;
  /** Иконка (Lucide), отображается в цветном квадрате 88×88. */
  icon?: React.ReactNode;
  /** Цветовой тон акцента. По умолчанию 'brand'. */
  tone?: TileTone;
  onClick?: () => void;
}

export function TileButton(props: TileButtonProps): JSX.Element;
