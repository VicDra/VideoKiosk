Крупная сенсорная плитка главного меню киоска — иконка + заголовок + подпись, вся плитка кликабельна.

```jsx
<TileButton
  tone="brand"
  icon={<i data-lucide="info" />}
  title="Информация об ИРЦ"
  subtitle="Адрес, часы работы и контакты"
  onClick={openInfo}
/>
<TileButton tone="green" icon={<i data-lucide="video" />} title="Видеозвонок оператору" subtitle="Свяжитесь со специалистом" />
```

- Используйте 2–3 плитки в ряд (`display:grid; gap:var(--space-6)`) на главном экране киоска.
- Тон `green` уместен для главного действия (видеозвонок), `brand`/`amber` — для информационных разделов.
