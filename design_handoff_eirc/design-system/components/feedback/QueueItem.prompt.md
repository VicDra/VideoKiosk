Строка очереди вызовов в приложении оператора.

```jsx
<QueueItem position={1} kiosk="Киоск №1" location="Зал ожидания, 1 этаж" active />
<QueueItem position={2} kiosk="Киоск №3" location="Окно приёма показаний" waiting="ждёт 0:45" />
```

- `active` выделяет вызов, который оператор обслуживает сейчас.
- Складывайте в столбец (`display:flex; flex-direction:column; gap:var(--space-2)`).
