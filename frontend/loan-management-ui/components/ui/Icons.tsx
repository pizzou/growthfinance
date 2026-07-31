// Small, dependency-free icon set (Feather-style: 24x24, 1.8px stroke,
// currentColor) so the product doesn't rely on emoji for financial UI —
// emoji render inconsistently across OS/browser and read as informal for
// a lending platform. Sized and colored via className, same as any icon
// library — e.g. <IconMoney className="w-5 h-5 text-teal-600" />.

import React from 'react';

type IconProps = React.SVGProps<SVGSVGElement>;

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
};

export function IconBank(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M3 21h18M4 21V10M20 21V10M3 10l9-6 9 6M7 10v11M11 10v11M13 10v11M17 10v11" />
    </svg>
  );
}

export function IconSignature(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M3 17c2-3 3.5-4.5 5-4.5s1.5 2 3 2 2.5-3.5 4-3.5 1.5 3 3 3 2-1.5 3-3" />
      <path d="M4 21h16" />
    </svg>
  );
}

export function IconCard(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="2.5" y="5.5" width="19" height="13" rx="2" />
      <path d="M2.5 10h19" />
    </svg>
  );
}

export function IconCoins(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <ellipse cx="8" cy="7" rx="5.5" ry="3" />
      <path d="M2.5 7v5c0 1.66 2.46 3 5.5 3s5.5-1.34 5.5-3V7" />
      <path d="M2.5 12v5c0 1.66 2.46 3 5.5 3s5.5-1.34 5.5-3v-5" />
      <ellipse cx="16" cy="12" rx="5.5" ry="3" />
      <path d="M10.5 12v5c0 1.66 2.46 3 5.5 3s5.5-1.34 5.5-3v-5" />
    </svg>
  );
}

export function IconSend(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M21 3 3 10.5l7.5 3L14 21l7-18Z" />
      <path d="M10.5 13.5 21 3" />
    </svg>
  );
}

export function IconCheckCircle(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9.25" />
      <path d="m8 12.3 2.6 2.6 5.4-5.6" />
    </svg>
  );
}

export function IconClock(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9.25" />
      <path d="M12 7v5l3.2 2" />
    </svg>
  );
}

export function IconFileText(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 2.75h8.5L19 7.25V21a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.75a1 1 0 0 1 1-1Z" />
      <path d="M14 2.75V7h4.25" />
      <path d="M8.5 12.5h7M8.5 16h7" />
    </svg>
  );
}

export function IconAlertTriangle(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5 22 20.5H2Z" />
      <path d="M12 9.5v5M12 17.5h.01" />
    </svg>
  );
}

export function IconFileEdit(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M13 2.75H6a1 1 0 0 0-1 1V21a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5Z" />
      <path d="M13 2.75V9.5h6.75" />
      <path d="m11 15.5 4.5-4.5 1.75 1.75-4.5 4.5H11Z" />
    </svg>
  );
}

export function IconSearch(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m20 20-4.5-4.5" />
    </svg>
  );
}

export function IconCalendar(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="4.5" width="18" height="16.5" rx="2" />
      <path d="M3 9.5h18M8 2.5v4M16 2.5v4" />
    </svg>
  );
}

export function IconFlag(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M5 21V3.5" />
      <path d="M5 4.5c2-1.3 4-1.3 6 0s4 1.3 6 0v9c-2 1.3-4 1.3-6 0s-4-1.3-6 0Z" />
    </svg>
  );
}
