import { useEffect, useRef, useState, useCallback } from 'react';

export interface AppointmentStatusMessage {
  appointmentId: number;
  status: 'PROCESSING' | 'PROCESSED' | 'FAILED' | 'DUPLICATE';
  note: string;
  eventType: string;
  timestamp: string;
}

function stompFrame(command: string, headers: Record<string, string>, body = ''): string {
  const headerLines = Object.entries(headers)
    .map(([k, v]) => `${k}:${v}`)
    .join('\n');
  return `${command}\n${headerLines}\n\n${body}\0`;
}

function parseFrame(raw: string): { command: string; headers: Record<string, string>; body: string } {
  const nullIdx = raw.indexOf('\0');
  const text = nullIdx >= 0 ? raw.slice(0, nullIdx) : raw;
  const [headerSection, ...bodyParts] = text.split('\n\n');
  const lines = headerSection.split('\n');
  const command = lines[0].trim();
  const headers: Record<string, string> = {};
  for (const line of lines.slice(1)) {
    const sep = line.indexOf(':');
    if (sep >= 0) headers[line.slice(0, sep).trim()] = line.slice(sep + 1).trim();
  }
  return { command, headers, body: bodyParts.join('\n\n') };
}

const MAX_LIVE_EVENTS = 30;

export function useAppointmentSocket(
  appointmentIds: number[],
  wsBaseUrl: string,
) {
  const [statusMap, setStatusMap] = useState<Record<number, AppointmentStatusMessage>>({});
  const [liveEvents, setLiveEvents] = useState<AppointmentStatusMessage[]>([]);
  const [connected, setConnected] = useState(false);

  const wsRef       = useRef<WebSocket | null>(null);
  const subIds      = useRef<Record<number, string>>({});   
  const subCounter  = useRef(0);
  const reconnTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const unmounted   = useRef(false);

  const handleMessage = useCallback((msg: AppointmentStatusMessage) => {
    setStatusMap(prev => ({ ...prev, [msg.appointmentId]: msg }));
    setLiveEvents(prev => [msg, ...prev].slice(0, MAX_LIVE_EVENTS));
  }, []);

  const connect = useCallback(() => {
    if (unmounted.current) return;

    const base = wsBaseUrl.replace(/^http/, 'ws').replace(/\/$/, '');
    const url  = `${base}/ws/websocket`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {

      ws.send(stompFrame('CONNECT', {
        'accept-version': '1.1,1.0',
        'heart-beat':     '0,0',
      }));
    };

    ws.onmessage = (evt) => {
      const frame = parseFrame(evt.data as string);

      if (frame.command === 'CONNECTED') {
        setConnected(true);

        appointmentIds.forEach(id => subscribe(ws, id));
      }

      if (frame.command === 'MESSAGE') {
        try {
          const msg: AppointmentStatusMessage = JSON.parse(frame.body);
          handleMessage(msg);
        } catch {

        }
      }

      if (frame.command === 'ERROR') {
        ws.close();
      }
    };

    ws.onclose = () => {
      setConnected(false);
      wsRef.current = null;
      subIds.current = {};
      if (!unmounted.current) {

        reconnTimer.current = setTimeout(connect, 3000);
      }
    };

    ws.onerror = () => ws.close();
  }, [wsBaseUrl, appointmentIds, handleMessage]); 

  function subscribe(ws: WebSocket, appointmentId: number) {
    if (subIds.current[appointmentId]) return;          
    const subId = `sub-${++subCounter.current}`;
    subIds.current[appointmentId] = subId;
    ws.send(stompFrame('SUBSCRIBE', {
      id:          subId,
      destination: `/topic/appointments/${appointmentId}`,
    }));
  }

  useEffect(() => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    appointmentIds.forEach(id => subscribe(ws, id));
  }, [appointmentIds]); 

  useEffect(() => {
    unmounted.current = false;
    connect();
    return () => {
      unmounted.current = true;
      if (reconnTimer.current) clearTimeout(reconnTimer.current);
      const ws = wsRef.current;
      if (ws) {
        ws.onclose = null; 
        ws.close();
      }
    };
  }, [connect]);

  return { statusMap, liveEvents, connected };
}
