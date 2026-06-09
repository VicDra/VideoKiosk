Текстовое поле с меткой, подсказкой и состоянием ошибки — для настроек подключения оператора.

```jsx
<Input label="IP сигнального сервера" value={ip} placeholder="192.168.1.100" onChange={e => setIp(e.target.value)} hint="Адрес в локальной сети" />
<Input label="Порт" value={port} placeholder="8080" />
<Input label="Адрес WebSocket" prefix="wss://" value={addr} error="Неверный формат адреса" />
```

- `prefix` помещает неизменяемую часть (протокол) внутрь поля.
- `error` имеет приоритет над `hint`: окрашивает рамку и показывает сообщение.
