'use client';
import { ChartPoint } from '../../types/index';

interface Props {
  data: ChartPoint[];
  label: string;
  color?: string;
  valuePrefix?: string;
}

export function BarChart({ data, label, color = 'bg-blue-500', valuePrefix = '' }: Props) {
  const max = Math.max(...data.map(d => d.amount), 1);
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <h3 className="font-semibold text-gray-800 text-sm mb-4">{label}</h3>
      <div className="flex items-end gap-2 h-32">
        {data.map((d) => {
          const pct = Math.max((d.amount / max) * 100, 2);
          return (
            <div key={d.month} className="flex-1 flex flex-col items-center gap-1">
              <span className="text-[10px] text-gray-400">
                {valuePrefix}{d.amount >= 1000
                  ? `${(d.amount / 1000).toFixed(1)}k`
                  : d.amount.toFixed(0)}
              </span>
              <div className="w-full flex items-end" style={{ height: '80px' }}>
                <div
                  className={`w-full rounded-t-md ${color} transition-all hover:opacity-80`}
                  style={{ height: `${pct}%` }}
                />
              </div>
              <span className="text-[10px] text-gray-500 font-medium">{d.month}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function AreaChart({
  data,
  label,
  color = '#3b82f6',
  valuePrefix = '',
}: Props & { color?: string }) {
  const max = Math.max(...data.map(d => d.amount), 1);
  const H = 80;
  const W = 100;

  const points = data.map((d, i) => {
    const x = data.length === 1 ? 50 : (i / (data.length - 1)) * W;
    const y = H - (d.amount / max) * (H - 8);
    return { x, y, d };
  });

  const polyline = points.map(p => `${p.x},${p.y}`).join(' ');
  const areaPath =
    `${points[0].x},${H} ` +
    points.map(p => `${p.x},${p.y}`).join(' ') +
    ` ${points[points.length - 1].x},${H}`;

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <h3 className="font-semibold text-gray-800 text-sm mb-4">{label}</h3>
      <svg
        viewBox={`0 0 ${W} ${H + 10}`}
        className="w-full h-28"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.25" />
            <stop offset="100%" stopColor={color} stopOpacity="0.02" />
          </linearGradient>
        </defs>
        <polygon points={areaPath} fill="url(#areaGrad)" />
        <polyline
          points={polyline}
          fill="none"
          stroke={color}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {points.map((p, i) => (
          <circle key={i} cx={p.x} cy={p.y} r="2.5" fill={color} />
        ))}
      </svg>
      <div className="flex justify-between mt-1">
        {data.map(d => (
          <span key={d.month} className="text-[10px] text-gray-400">{d.month}</span>
        ))}
      </div>
    </div>
  );
}