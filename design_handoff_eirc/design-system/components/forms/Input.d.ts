import React from 'react';

/**
 * Текстовое поле ввода с меткой, подсказкой и состоянием ошибки.
 * Основное применение — экран настроек подключения оператора.
 *
 * @startingPoint section="Forms" subtitle="Поле ввода с меткой и подсказкой" viewport="700x140"
 */
export interface InputProps {
  /** Метка над полем. */
  label?: string;
  /** Подсказка под полем (серым). */
  hint?: string;
  /** Текст ошибки — окрашивает рамку в красный и показывает сообщение. */
  error?: string;
  value?: string;
  placeholder?: string;
  type?: string;
  /** Префикс внутри поля, например протокол «wss://». */
  prefix?: React.ReactNode;
  disabled?: boolean;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  id?: string;
}

export function Input(props: InputProps): JSX.Element;
