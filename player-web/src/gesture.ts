import { NativeBridge } from './bridge';

export interface GestureCallbacks {
  toggleControls: () => void;
  showControls: () => void;
  areControlsVisible: () => boolean;
  onDoubleTapSeek: (seconds: number, clientX: number, clientY: number, direction: 'left' | 'right') => void;
  onSeekPreview: (targetTime: number, formattedTime: string, deltaSeconds: number) => void;
  onSeekCommit: (targetTime: number) => void;
  onBrightnessChange: (level: number) => void;
  onVolumeChange: (level: number) => void;
  onSpeedBoostStart: () => void;
  onSpeedBoostEnd: () => void;
  onPinchZoomToggle: (mode: 'contain' | 'cover') => void;
  onGestureEnd: () => void;
  isLocked: () => boolean;
}

export class GestureController {
  private surface: HTMLElement;
  private video: HTMLVideoElement;
  private callbacks: GestureCallbacks;

  // Touch & pointer tracking
  private touchStartX = 0;
  private touchStartY = 0;
  private touchStartTime = 0;
  private initialVideoTime = 0;
  private initialBrightness = 0.5;
  private initialVolume = 1.0;

  // Active state
  private activeMode: 'none' | 'seek' | 'brightness' | 'volume' | 'longpress' | 'pinch' = 'none';
  private longPressTimer: any = null;
  private singleTapTimer: any = null;
  private lastTapTime = 0;
  private lastTapX = 0;
  private lastTapY = 0;

  // Pinch tracking
  private initialPinchDist = 0;
  private currentFitMode: 'contain' | 'cover' = 'contain';

  // Calculated targets
  private pendingSeekTarget = 0;
  private currentBrightness = 0.5;
  private currentVolume = 1.0;

  constructor(surface: HTMLElement, video: HTMLVideoElement, callbacks: GestureCallbacks) {
    this.surface = surface;
    this.video = video;
    this.callbacks = callbacks;
    this.currentVolume = video.volume || 1.0;

    this.initEventListeners();
  }

  private initEventListeners(): void {
    const el = this.surface;

    // Mobile touch events
    el.addEventListener('touchstart', this.handleTouchStart.bind(this), { passive: false });
    el.addEventListener('touchmove', this.handleTouchMove.bind(this), { passive: false });
    el.addEventListener('touchend', this.handleTouchEnd.bind(this), { passive: false });
    el.addEventListener('touchcancel', this.handleTouchCancel.bind(this), { passive: false });

    // Click / Mouse / Pointer fallback
    el.addEventListener('click', (e) => {
      // If click was not preceded by touch gesture
      if (Date.now() - this.touchStartTime > 600 || this.touchStartTime === 0) {
        if (!this.callbacks.isLocked()) {
          this.callbacks.toggleControls();
        }
      }
    });
  }

  private handleTouchStart(e: TouchEvent): void {
    if (this.callbacks.isLocked()) {
      return;
    }

    if (e.touches.length === 2) {
      // Pinch gesture
      this.clearTimers();
      this.activeMode = 'pinch';
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      this.initialPinchDist = Math.hypot(dx, dy);
      return;
    }

    if (e.touches.length === 1) {
      const touch = e.touches[0];
      this.touchStartX = touch.clientX;
      this.touchStartY = touch.clientY;
      this.touchStartTime = Date.now();
      this.initialVideoTime = this.video.currentTime || 0;
      this.initialVolume = this.video.volume || 1.0;
      this.activeMode = 'none';

      // Long press detection: after 450ms without movement, engage 2x speed
      this.longPressTimer = setTimeout(() => {
        if (this.activeMode === 'none') {
          this.activeMode = 'longpress';
          this.callbacks.onSpeedBoostStart();
        }
      }, 450);
    }
  }

  private handleTouchMove(e: TouchEvent): void {
    if (this.callbacks.isLocked()) return;

    if (this.activeMode === 'pinch' && e.touches.length === 2) {
      e.preventDefault();
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const dist = Math.hypot(dx, dy);
      const delta = dist - this.initialPinchDist;

      if (Math.abs(delta) > 35) {
        this.currentFitMode = delta > 0 ? 'cover' : 'contain';
        this.callbacks.onPinchZoomToggle(this.currentFitMode);
        this.initialPinchDist = dist;
      }
      return;
    }

    if (e.touches.length === 1) {
      const touch = e.touches[0];
      const dx = touch.clientX - this.touchStartX;
      const dy = touch.clientY - this.touchStartY;
      const distance = Math.hypot(dx, dy);

      // Cancel long press if finger moved > 12px
      if (distance > 12 && this.longPressTimer) {
        clearTimeout(this.longPressTimer);
        this.longPressTimer = null;
      }

      if (this.activeMode === 'longpress') {
        return;
      }

      const rect = this.surface.getBoundingClientRect();
      const width = rect.width || window.innerWidth;
      const height = rect.height || window.innerHeight;

      // Detect swipe direction once threshold exceeded
      if (this.activeMode === 'none' && distance > 18) {
        if (Math.abs(dx) > Math.abs(dy)) {
          this.activeMode = 'seek';
        } else {
          const touchRelX = this.touchStartX - rect.left;
          if (touchRelX < width * 0.5) {
            this.activeMode = 'brightness';
          } else {
            this.activeMode = 'volume';
          }
        }
      }

      if (this.activeMode === 'seek') {
        e.preventDefault();
        const duration = this.video.duration || 100;
        const seekScale = Math.min(90, Math.max(30, duration * 0.3));
        const deltaSeconds = (dx / width) * seekScale;
        const targetTime = Math.max(0, Math.min(duration, this.initialVideoTime + deltaSeconds));
        this.pendingSeekTarget = targetTime;
        this.callbacks.onSeekPreview(targetTime, this.formatTime(targetTime), Math.round(deltaSeconds));
      } else if (this.activeMode === 'brightness') {
        e.preventDefault();
        const delta = -dy / height;
        const level = Math.max(0, Math.min(1, this.initialBrightness + delta));
        this.currentBrightness = level;
        this.callbacks.onBrightnessChange(level);
        NativeBridge.setBrightness(level);
      } else if (this.activeMode === 'volume') {
        e.preventDefault();
        const delta = -dy / height;
        const level = Math.max(0, Math.min(1, this.initialVolume + delta));
        this.currentVolume = level;
        this.video.volume = level;
        this.callbacks.onVolumeChange(level);
        NativeBridge.setVolume(level);
      }
    }
  }

  private handleTouchEnd(e: TouchEvent): void {
    if (this.callbacks.isLocked()) {
      this.callbacks.toggleControls();
      return;
    }

    if (this.longPressTimer) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }

    if (this.activeMode === 'longpress') {
      this.callbacks.onSpeedBoostEnd();
      this.activeMode = 'none';
      return;
    }

    if (this.activeMode === 'seek') {
      this.callbacks.onSeekCommit(this.pendingSeekTarget);
      this.activeMode = 'none';
      this.callbacks.onGestureEnd();
      return;
    }

    if (this.activeMode === 'brightness' || this.activeMode === 'volume' || this.activeMode === 'pinch') {
      this.activeMode = 'none';
      this.callbacks.onGestureEnd();
      return;
    }

    // Tap handling
    const now = Date.now();
    const rect = this.surface.getBoundingClientRect();
    const x = this.touchStartX - rect.left;
    const y = this.touchStartY - rect.top;
    const width = rect.width || window.innerWidth;

    const timeSinceLastTap = now - this.lastTapTime;
    const distSinceLastTap = Math.hypot(this.touchStartX - this.lastTapX, this.touchStartY - this.lastTapY);

    if (timeSinceLastTap < 320 && distSinceLastTap < 45) {
      // Double Tap detected
      if (this.singleTapTimer) {
        clearTimeout(this.singleTapTimer);
        this.singleTapTimer = null;
      }
      this.lastTapTime = 0;

      if (x < width * 0.42) {
        // Double tap left: -10s
        this.callbacks.onDoubleTapSeek(-10, this.touchStartX, this.touchStartY, 'left');
      } else if (x > width * 0.58) {
        // Double tap right: +10s
        this.callbacks.onDoubleTapSeek(10, this.touchStartX, this.touchStartY, 'right');
      } else {
        // Center double tap toggles play/pause
        if (this.video.paused) {
          this.video.play().catch(() => {});
        } else {
          this.video.pause();
        }
      }
    } else {
      // Single tap: schedule toggle
      this.lastTapTime = now;
      this.lastTapX = this.touchStartX;
      this.lastTapY = this.touchStartY;

      this.singleTapTimer = setTimeout(() => {
        this.callbacks.toggleControls();
        this.singleTapTimer = null;
      }, 280);
    }
  }

  private handleTouchCancel(): void {
    this.clearTimers();
    if (this.activeMode === 'longpress') {
      this.callbacks.onSpeedBoostEnd();
    }
    this.activeMode = 'none';
    this.callbacks.onGestureEnd();
  }

  private clearTimers(): void {
    if (this.longPressTimer) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }
    if (this.singleTapTimer) {
      clearTimeout(this.singleTapTimer);
      this.singleTapTimer = null;
    }
  }

  private formatTime(seconds: number): string {
    const s = Math.floor(seconds || 0);
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }

  public destroy(): void {
    this.clearTimers();
  }
}
