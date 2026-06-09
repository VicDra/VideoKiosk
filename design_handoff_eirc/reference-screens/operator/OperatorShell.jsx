/* global React */
const { useState: useStateO, useEffect: useEffectO } = React;

function OIcon({ name, size = 20, color, style }) {
  return <i data-lucide={name} style={{ width: size, height: size, color, ...style }} />;
}
function useLucideO() { useEffectO(() => { if (window.lucide) window.lucide.createIcons(); }); }

/* ============================ ВЕРХНЯЯ ПАНЕЛЬ ============================ */
function OperatorTopBar({ status, onSettings }) {
  const statusMap = {
    online: { c: 'var(--status-online)', t: 'Готов принимать' },
    busy:   { c: 'var(--status-busy)', t: 'В разговоре' },
    waiting:{ c: 'var(--status-waiting)', t: 'Ожидание' },
  };
  const s = statusMap[status] || statusMap.online;
  return (
    <header style={oTop}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
        <img src="../../design-system/assets/logo-irc.svg" width="38" height="38" alt="" />
        <div>
          <div style={{ font: 'var(--weight-bold) 18px/1 var(--font-display)', color: 'var(--text-strong)' }}>ИРЦ · Оператор</div>
          <div style={{ font: 'var(--type-caption)', color: 'var(--text-muted)' }}>Рабочее место приёма вызовов</div>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
        <span style={oStatusPill}>
          <span style={{ width: 9, height: 9, borderRadius: '50%', background: s.c }} /> {s.t}
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <span style={oAvatar}>АП</span>
          <div>
            <div style={{ font: 'var(--type-label)', color: 'var(--text-strong)', fontWeight: 600 }}>Анна Петрова</div>
            <div style={{ font: 'var(--type-caption)', color: 'var(--text-muted)' }}>Оператор · смена 1</div>
          </div>
        </div>
        <button onClick={onSettings} style={oIconBtn} aria-label="Настройки"><OIcon name="settings" size={20} color="var(--text-body)" /></button>
      </div>
    </header>
  );
}
const oTop = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 'var(--space-4) var(--space-6)', background: 'var(--surface)', borderBottom: '1px solid var(--border)', flexShrink: 0 };
const oStatusPill = { display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', padding: '8px 14px', borderRadius: 'var(--radius-pill)', background: 'var(--surface-muted)', border: '1px solid var(--border)', font: 'var(--type-label)', color: 'var(--text-body)' };
const oAvatar = { width: 40, height: 40, borderRadius: '50%', background: 'var(--blue-600)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', font: 'var(--type-label)', fontWeight: 700 };
const oIconBtn = { width: 40, height: 40, borderRadius: 'var(--radius-md)', background: 'var(--surface)', border: '1px solid var(--border-strong)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' };

/* ============================ ОЧЕРЕДЬ (САЙДБАР) ============================ */
function QueueSidebar({ queue, activeId, onPick }) {
  return (
    <aside style={oSide}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-4)' }}>
        <h2 style={{ font: 'var(--type-h3)', color: 'var(--text-strong)', whiteSpace: 'nowrap' }}>Очередь вызовов</h2>
        <span style={oCount}>{queue.length}</span>
      </div>
      {queue.length === 0 ? (
        <div style={oQueueEmpty}>
          <OIcon name="inbox" size={32} color="var(--gray-400)" />
          <span style={{ font: 'var(--type-body-sm)', color: 'var(--text-muted)', textAlign: 'center' }}>Очередь пуста.<br/>Новые вызовы появятся здесь.</span>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {queue.map((q, i) => {
            const active = q.id === activeId;
            return (
              <button key={q.id} onClick={() => onPick(q.id)} style={{ ...oQueueItem, ...(active ? oQueueItemActive : {}) }}>
                <span style={{ ...oQNum, ...(active ? oQNumActive : {}) }}>{i + 1}</span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ display: 'block', font: 'var(--type-label)', fontWeight: 600, color: 'var(--text-strong)' }}>{q.kiosk}</span>
                  <span style={{ display: 'block', font: 'var(--type-caption)', color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.location}</span>
                </span>
                <span style={{ font: 'var(--type-caption)', fontWeight: 600, color: active ? 'var(--blue-700)' : 'var(--amber-700)' }}>{active ? 'разговор' : q.waiting}</span>
              </button>
            );
          })}
        </div>
      )}
    </aside>
  );
}
const oSide = { width: 340, flexShrink: 0, background: 'var(--surface)', borderRight: '1px solid var(--border)', padding: 'var(--space-5)', overflowY: 'auto' };
const oCount = { minWidth: 28, height: 28, padding: '0 8px', borderRadius: 'var(--radius-pill)', background: 'var(--blue-600)', color: '#fff', font: 'var(--type-label)', fontWeight: 700, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' };
const oQueueEmpty = { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-9) var(--space-4)', background: 'var(--surface-muted)', borderRadius: 'var(--radius-lg)', border: '1px dashed var(--border-strong)' };
const oQueueItem = { display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-3)', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-md)', cursor: 'pointer', textAlign: 'left', width: '100%' };
const oQueueItemActive = { background: 'var(--action-primary-soft)', borderColor: 'var(--blue-300)' };
const oQNum = { width: 36, height: 36, flexShrink: 0, borderRadius: 'var(--radius-sm)', background: 'var(--surface-sunken)', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center', font: 'var(--type-h3)', fontWeight: 700 };
const oQNumActive = { background: 'var(--blue-600)', color: '#fff' };

window.OperatorShared = { OIcon, useLucideO, OperatorTopBar, QueueSidebar };
