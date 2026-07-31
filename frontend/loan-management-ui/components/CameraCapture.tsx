'use client';
import { useEffect, useRef, useState } from 'react';

interface CameraCaptureProps {
  onCapture: (blob: Blob) => void;
  onClose: () => void;
  primary?: string;
}

/**
 * Opens the device camera (front-facing where available) for a live selfie
 * capture — used for KYC face verification. Falls back to a plain file input
 * with a `capture` attribute (which itself opens the native camera app on
 * most mobile browsers) when getUserMedia isn't available — e.g. no camera,
 * permission denied, or a non-HTTPS/non-localhost origin, since getUserMedia
 * requires a secure context.
 */
export default function CameraCapture({ onCapture, onClose, primary = '#0D6B3E' }: CameraCaptureProps) {
  const videoRef  = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [error, setError]     = useState('');
  const [ready, setReady]     = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [capturedBlob, setCapturedBlob] = useState<Blob | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('Camera access is not available in this browser. Please use the file upload option instead.');
      return;
    }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
      .then(stream => {
        if (cancelled) { stream.getTracks().forEach(t => t.stop()); return; }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch(() => {});
        }
        setReady(true);
      })
      .catch(() => setError('We couldn\'t access your camera. Please check your browser permissions, or use the file upload option instead.'));

    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach(t => t.stop());
    };
  }, []);

  const capture = () => {
    const video = videoRef.current, canvas = canvasRef.current;
    if (!video || !canvas) return;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(blob => {
      if (!blob) return;
      setCapturedBlob(blob);
      setPreview(URL.createObjectURL(blob));
    }, 'image/jpeg', 0.9);
  };

  const retake = () => {
    if (preview) URL.revokeObjectURL(preview);
    setPreview(null);
    setCapturedBlob(null);
  };

  const confirm = () => {
    if (capturedBlob) onCapture(capturedBlob);
  };

  const handleFallbackFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) onCapture(file);
  };

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl max-w-md w-full overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="font-bold text-gray-900">Take a Selfie</div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <div className="p-5">
          {error ? (
            <div>
              <p className="text-sm text-red-600 mb-4">{error}</p>
              <label className="block text-center py-3 rounded-md text-sm font-bold text-white cursor-pointer"
                style={{ backgroundColor: primary }}>
                Upload a Photo Instead
                <input type="file" accept="image/*" capture="user" className="hidden" onChange={handleFallbackFile} />
              </label>
            </div>
          ) : (
            <>
              <p className="text-xs text-gray-500 mb-3">
                Face the camera in good lighting, remove sunglasses/hats, and center your face in the frame.
              </p>
              <div className="relative rounded-lg overflow-hidden bg-gray-900 aspect-square mb-4">
                {!preview ? (
                  <video ref={videoRef} playsInline muted className="w-full h-full object-cover -scale-x-100" />
                ) : (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={preview} alt="Captured selfie" className="w-full h-full object-cover" />
                )}
                {!ready && !preview && (
                  <div className="absolute inset-0 flex items-center justify-center text-white/60 text-sm">
                    Starting camera…
                  </div>
                )}
              </div>
              <canvas ref={canvasRef} className="hidden" />

              {!preview ? (
                <button onClick={capture} disabled={!ready}
                  className="w-full py-3 rounded-md text-sm font-bold text-white disabled:opacity-50"
                  style={{ backgroundColor: primary }}>
                  Capture Photo
                </button>
              ) : (
                <div className="flex gap-2">
                  <button onClick={retake} className="flex-1 py-3 rounded-md text-sm font-bold border border-gray-300 text-gray-700">
                    Retake
                  </button>
                  <button onClick={confirm} className="flex-1 py-3 rounded-md text-sm font-bold text-white"
                    style={{ backgroundColor: primary }}>
                    Use This Photo
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
