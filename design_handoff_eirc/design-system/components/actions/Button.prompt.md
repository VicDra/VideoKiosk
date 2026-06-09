Основная кнопка действия для десктопа оператора и форм — варианты primary / secondary / ghost / accept / danger.

```jsx
<Button variant="primary" size="lg" onClick={save}>Сохранить</Button>
<Button variant="accept" icon={<i data-lucide="phone" />}>Принять</Button>
<Button variant="danger" icon={<i data-lucide="phone-off" />}>Завершить</Button>
<Button variant="secondary">Отмена</Button>
```

- `size="lg"` (52px) — для главных действий экрана; `md` (44px) — в формах и панелях.
- `accept` (зелёная) и `danger` (красная) — только для управления вызовом.
- `fullWidth` растягивает на ширину контейнера (удобно в диалогах входящего вызова).
