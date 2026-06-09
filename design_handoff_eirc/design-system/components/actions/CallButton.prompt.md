Круглая кнопка управления видеозвонком на тёмном фоне — микрофон, камера, принять, завершить.

```jsx
<CallButton variant="neutral" icon={<i data-lucide="mic" />} label="Микрофон" />
<CallButton variant="neutral" icon={<i data-lucide="video" />} label="Камера" />
<CallButton variant="end" icon={<i data-lucide="phone-off" />} label="Завершить" size={96} />
```

- `accept`/`end` — для приёма и завершения; `neutral` (стеклянная) — для переключателей мик/камера.
- Группируйте в ряд (`display:flex; gap:var(--space-7)`) внизу экрана видео.
