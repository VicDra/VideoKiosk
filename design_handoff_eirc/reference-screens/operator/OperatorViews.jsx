/* global React */
const OIc = window.OperatorShared.OIcon;

/* ============================ ЭКРАН ОЖИДАНИЯ ============================ */
function WaitingView() {
  window.OperatorShared.useLucideO();
  return (
    <div style={oCenter}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-6)', textAlign: 'center' }}>
        <span style={oWaitIcon}><OIc name="headset" size={56} color="var(--blue-500)" /></span>
        <div>
          <h1 style={{ font: 'var(--type-h1)', color: 'var(--text-strong)', marginBottom: 'var(--space-3)' }}>Ожидание вызовов</h1>
          <p style={{ font: 'var(--type-body)', color: 'var(--text-muted)', maxWidth: 420 }}>
            Вы подключены к сигнальному серверу. Когда клиент киоска начнёт видеозвонок, здесь появится уведомление.
          </p>
        </div>
        <span style={oConnBadge}><span style={{ width: 9, height: 9, borderRadius: '50%', background: 'var(--status-online)' }} /> Соединение с сервером активно · 192.168.1.100:8080</span>
      </div>
    </div>
  );
}
const oCenter = { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-9)' };
const oWaitIcon = { width: 120, height: 120, borderRadius: '50%', background: 'var(--blue-50)', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const oConnBadge = { display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', padding: '10px 18px', borderRadius: 'var(--radius-pill)', background: 'var(--surface)', border: '1px solid var(--border)', font: 'var(--type-label)', color: 'var(--text-body)' };

/* ============================ ЭКРАН РАЗГОВОРА ============================ */
function InCallView({ caller, elapsed, onEnd }) {
  window.OperatorShared.useLucideO();
  const [mic, setMic] = React.useState(true);
  const [cam, setCam] = React.useState(true);
  const fmt = (s) => `${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: 'var(--space-6)', gap: 'var(--space-5)', minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ font: 'var(--type-h2)', color: 'var(--text-strong)' }}>{caller.kiosk}</div>
          <div style={{ font: 'var(--type-body-sm)', color: 'var(--text-muted)' }}>{caller.location}</div>
        </div>
        <span style={oCallTimer}><span style={{ width: 9, height: 9, borderRadius: '50%', background: 'var(--status-online)' }} /> Идёт разговор · {fmt(elapsed)}</span>
      </div>
      <div style={oVideoWrap}>
        {/* Видео клиента */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', opacity: 0.85 }}>
          <span style={oClientAvatar}><OIc name="user-round" size={56} color="rgba(255,255,255,0.7)" /></span>
          <span style={{ font: 'var(--type-body)', color: 'var(--text-on-dark)' }}>Видео клиента</span>
        </div>
        {/* Своё видео PiP */}
        <div style={oPip}>
          <OIc name="user" size={32} color="rgba(255,255,255,0.55)" />
          <span style={{ position: 'absolute', bottom: 6, left: 8, font: 'var(--type-caption)', color: 'rgba(255,255,255,0.85)', fontWeight: 600 }}>Вы</span>
        </div>
        {/* Управление */}
        <div style={oControls}>
          <button onClick={() => setMic(m => !m)} style={oCtrl(!mic)}><OIc name={mic ? 'mic' : 'mic-off'} size={22} color={mic ? '#fff' : 'var(--gray-800)'} /></button>
          <button onClick={() => setCam(c => !c)} style={oCtrl(!cam)}><OIc name={cam ? 'video' : 'video-off'} size={22} color={cam ? '#fff' : 'var(--gray-800)'} /></button>
          <button onClick={onEnd} style={oEnd}><OIc name="phone-off" size={24} color="#fff" /> <span style={{ font: 'var(--type-label)', color: '#fff' }}>Завершить</span></button>
        </div>
      </div>
    </div>
  );
}
const oCallTimer = { display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', padding: '8px 16px', borderRadius: 'var(--radius-pill)', background: 'var(--tint-success-bg)', color: 'var(--tint-success-fg)', font: 'var(--type-label)', fontWeight: 600 };
const oVideoWrap = { position: 'relative', flex: 1, borderRadius: 'var(--radius-xl)', background: 'radial-gradient(120% 90% at 50% 0%, #11346F, var(--bg-page-blue) 60%, #06205A)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', minHeight: 360 };
const oClientAvatar = { width: 130, height: 130, borderRadius: '50%', background: 'rgba(255,255,255,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid rgba(255,255,255,0.18)' };
const oPip = { position: 'absolute', top: 'var(--space-5)', right: 'var(--space-5)', width: 180, height: 124, borderRadius: 'var(--radius-lg)', background: 'linear-gradient(150deg, #1B3A6B, #0A2A63)', border: '2px solid rgba(255,255,255,0.22)', boxShadow: 'var(--shadow-lg)', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const oControls = { position: 'absolute', bottom: 'var(--space-6)', left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 'var(--space-4)' };
const oCtrl = (off) => ({ width: 56, height: 56, borderRadius: 'var(--radius-pill)', background: off ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.16)', border: '1px solid rgba(255,255,255,0.25)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' });
const oEnd = { display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', height: 56, padding: '0 var(--space-6)', borderRadius: 'var(--radius-pill)', background: 'var(--action-danger)', border: 'none', cursor: 'pointer', boxShadow: '0 8px 22px rgba(224,56,45,.4)' };

/* ============================ ВХОДЯЩИЙ ВЫЗОВ ============================ */
function IncomingOverlay({ caller, count, onAccept, onReject }) {
  window.OperatorShared.useLucideO();
  return (
    <div style={oOverlay}>
      <div style={oDialog}>
        <div style={{ padding: 'var(--space-8) var(--space-8) var(--space-6)', textAlign: 'center' }}>
          <div style={oIncAvatar}>
            <OIc name="video" size={44} color="var(--blue-600)" />
            <span style={oIncRing} /><span style={{ ...oIncRing, animationDelay: '0.7s' }} />
          </div>
          <div style={{ font: 'var(--type-h2)', color: 'var(--text-strong)', marginBottom: 'var(--space-2)' }}>Входящий видеозвонок</div>
          <div style={{ font: 'var(--type-body)', color: 'var(--text-body)' }}>{caller.kiosk}</div>
          <div style={{ font: 'var(--type-body-sm)', color: 'var(--text-muted)', marginTop: 2 }}>{caller.location}</div>
          {count > 0 && <div style={{ marginTop: 'var(--space-4)', font: 'var(--type-caption)', color: 'var(--amber-700)', fontWeight: 600 }}>Ещё {count} в очереди</div>}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)', padding: 'var(--space-5) var(--space-8) var(--space-8)' }}>
          <button onClick={onReject} style={oReject}><OIc name="phone-off" size={20} color="var(--text-strong)" /> Отклонить</button>
          <button onClick={onAccept} style={oAccept}><OIc name="phone" size={20} color="#fff" /> Принять</button>
        </div>
      </div>
    </div>
  );
}
const oOverlay = { position: 'absolute', inset: 0, background: 'rgba(10,42,99,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50 };
const oDialog = { width: 'min(440px, 92vw)', background: 'var(--surface)', borderRadius: 'var(--radius-xl)', boxShadow: 'var(--shadow-lg)', border: '1px solid var(--border)', overflow: 'hidden' };
const oIncAvatar = { position: 'relative', width: 96, height: 96, margin: '0 auto var(--space-5)', borderRadius: '50%', background: 'var(--blue-50)', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const oIncRing = { position: 'absolute', inset: 0, borderRadius: '50%', border: '3px solid var(--blue-300)', animation: 'op-ring 1.8s var(--ease-out) infinite' };
const oReject = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', height: 56, borderRadius: 'var(--radius-md)', background: 'var(--surface)', border: '1px solid var(--border-strong)', font: 'var(--type-h3)', color: 'var(--text-strong)', cursor: 'pointer' };
const oAccept = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', height: 56, borderRadius: 'var(--radius-md)', background: 'var(--action-accept)', border: 'none', font: 'var(--type-h3)', color: '#fff', cursor: 'pointer', boxShadow: '0 8px 22px rgba(31,164,99,.3)' };

/* ============================ НАСТРОЙКИ ============================ */
function SettingsOverlay({ onClose }) {
  window.OperatorShared.useLucideO();
  const field = (label, value, hint, prefix) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
      <label style={{ font: 'var(--type-label)', color: 'var(--text-strong)' }}>{label}</label>
      <div style={oField}>{prefix && <span style={{ color: 'var(--text-muted)' }}>{prefix}</span>}<span>{value}</span></div>
      {hint && <span style={{ font: 'var(--type-caption)', color: 'var(--text-muted)' }}>{hint}</span>}
    </div>
  );
  return (
    <div style={oOverlay}>
      <div style={{ ...oDialog, width: 'min(560px, 94vw)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 'var(--space-6) var(--space-7)', borderBottom: '1px solid var(--border)' }}>
          <h2 style={{ font: 'var(--type-h2)', color: 'var(--text-strong)' }}>Настройки подключения</h2>
          <button onClick={onClose} style={oIconBtn2} aria-label="Закрыть"><OIc name="x" size={20} color="var(--text-body)" /></button>
        </div>
        <div style={{ padding: 'var(--space-7)', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
          {field('Сигнальный сервер (IP)', '192.168.1.100', 'Адрес в локальной сети')}
          {field('Порт', '8080')}
          {field('Адрес WebSocket', '192.168.1.100:8080', 'Протокол подключения', 'wss://')}
          {field('STUN/TURN (coturn)', '192.168.1.100:3478', 'Локальный медиасервер')}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', padding: 'var(--space-5) var(--space-7) var(--space-7)' }}>
          <button onClick={onClose} style={oReject}>Отмена</button>
          <button onClick={onClose} style={{ ...oAccept, background: 'var(--action-primary)', boxShadow: 'var(--shadow-brand)', padding: '0 var(--space-7)' }}>Сохранить</button>
        </div>
      </div>
    </div>
  );
}
const oField = { display: 'flex', alignItems: 'center', gap: 'var(--space-2)', height: 48, padding: '0 var(--space-4)', background: 'var(--surface)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius-md)', font: 'var(--type-body)', color: 'var(--text-strong)' };
const oIconBtn2 = { width: 40, height: 40, borderRadius: 'var(--radius-md)', background: 'var(--surface-muted)', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' };

window.OperatorViews = { WaitingView, InCallView, IncomingOverlay, SettingsOverlay };
