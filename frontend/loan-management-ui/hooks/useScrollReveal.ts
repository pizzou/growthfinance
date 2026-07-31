'use client';
import { useEffect, useRef, useState } from 'react';

/** Returns a ref + boolean that flips to true once the element scrolls into view.
 *  Use it to trigger a CSS transition (fade/slide up) — see globals.css `.reveal` classes. */
export function useScrollReveal<T extends HTMLElement = HTMLDivElement>(threshold = 0.15) {
  const ref = useRef<T>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) { setVisible(true); observer.disconnect(); } },
      { threshold }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [threshold]);

  return { ref, visible };
}

/** Animates a number counting up from 0 once it scrolls into view. */
export function useCountUp(target: number, visible: boolean, durationMs = 1400) {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (!visible) return;
    let raf: number;
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / durationMs, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // ease-out-cubic
      setValue(Math.round(target * eased));
      if (progress < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [visible, target, durationMs]);
  return value;
}
