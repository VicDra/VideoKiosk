import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'accept' | 'danger';
export type ButtonSize = 'md' | 'lg';

/**
 * Основная кнопка действия для приложения оператора и форм настройки.
 * Используйте `primary` для главного действия экрана, `accept`/`danger` для
 * принятия/завершения вызова, `secondary`/`ghost` для второстепенных действий.
 *
 * @startingPoint section="Actions" subtitle="Кнопка действия: primary / accept / danger" viewport="700x120"
 */
export interface ButtonProps {
  children: React.ReactNode;
  /** Визуальный вариант. По умолчанию 'primary'. */
  variant?: ButtonVariant;
  /** Размер. 'md' (44px) — формы, 'lg' (52px) — главные действия. */
  size?: ButtonSize;
  /** Иконка слева (например, <i data-lucide="phone" />). */
  icon?: React.ReactNode;
  /** Иконка справа. */
  iconRight?: React.ReactNode;
  /** Растянуть на всю ширину контейнера. */
  fullWidth?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}

export function Button(props: ButtonProps): JSX.Element;
