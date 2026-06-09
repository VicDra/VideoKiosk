import React from 'react';
import { Button } from '../actions/Button.jsx';

/**
 * IncomingCallDialog — карточка входящего видеозвонка у оператора.
 * Аватар-источник, заголовок «Входящий вызов», источник и кнопки
 * «Принять» / «Отклонить». count показывает, сколько ещё в очереди.
 */
export function IncomingCallDialog({ kiosk, location, count = 0, onAccept, onReject }) {
  return (
    <div style={{
      width: 'min(440px, 92vw)', background: 'var(--surface)',
      borderRadius: 'var(--radius-xl)', boxShadow: 'var(--shadow-lg)',
      border: '1px solid var(--border)', overflow: 'hidden',
    }}>
      <div style={{ padding: 'var(--space-7) var(--space-7) var(--space-6)', textAlign: 'center' }}>
        <div style={{
          width: 96, height: 96, margin: '0 auto var(--space-5)',
          borderRadius: 'var(--radius-pill)', background: 'var(--blue-50)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--blue-600)', position: 'relative',
        }}>
          <i data-lucide="video" style={{ width: 44, height: 44 }} />
          <span style={{
            position: 'absolute', inset: -6, borderRadius: '50%',
            border: '3px solid var(--blue-300)', opacity: 0.6,
            animation: 'irc-ring 1.8s var(--ease-out) infinite',
          }} />
        </div>
        <div style={{ font: 'var(--type-h2)', color: 'var(--text-strong)', marginBottom: 'var(--space-2)' }}>
          Входящий видеозвонок
        </div>
        <div style={{ font: 'var(--type-body)', color: 'var(--text-body)' }}>{kiosk}</div>
        <div style={{ font: 'var(--type-body-sm)', color: 'var(--text-muted)', marginTop: 2 }}>{location}</div>
        {count > 0 && (
          <div style={{ marginTop: 'var(--space-4)', font: 'var(--type-caption)', color: 'var(--amber-700)', fontWeight: 600 }}>
            Ещё {count} в очереди
          </div>
        )}
      </div>
      <div style={{
        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)',
        padding: 'var(--space-5) var(--space-7) var(--space-7)',
      }}>
        <Button variant="secondary" size="lg" fullWidth icon={<i data-lucide="phone-off" style={{ width: 20, height: 20 }} />} onClick={onReject}>
          Отклонить
        </Button>
        <Button variant="accept" size="lg" fullWidth icon={<i data-lucide="phone" style={{ width: 20, height: 20 }} />} onClick={onAccept}>
          Принять
        </Button>
      </div>
      <style>{`@keyframes irc-ring { 0% { transform: scale(1); opacity: .6 } 100% { transform: scale(1.35); opacity: 0 } }
        @media (prefers-reduced-motion: reduce) { [style*="irc-ring"] { animation: none !important } }`}</style>
    </div>
  );
}
