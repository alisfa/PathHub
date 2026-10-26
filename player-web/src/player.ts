import { NativeBridge } from './bridge';
import { SiteAdapter } from './adapters/types';
import { GestureController } from './gesture';
import { DebugHUD } from './debug';
import { DebugInfo, VideoMetadata } from './types';

export class FluidVideoPlayer {
  private video: HTMLVideoElement;
  private adapter: SiteAdapter;
  private wrapper: HTMLElement;
  private gestureSurface: HTMLElement;
  private controlsOverlay: HTMLElement;
  private gestureController: GestureController;

  // UI elements
  private playPauseBtn!: HTMLElement;
  private centerPlayBtn!: HTMLElement;
  private seekBar!: HTMLInputElement;
  private progressFill!: HTMLElement;
  private bufferFill!: HTMLElement;
  private currentTimeLabel!: HTMLElement;
  private durationLabel!: HTMLElement;
  private speedBtn!: HTMLElement;
  private speedMenu!: HTMLElement;
  private fullscreenBtn!: HTMLElement;
  private lockBtn!: HTMLElement;
  private likeBtn!: HTMLElement;
  private toastElement!: HTMLElement;

  // HUD elements
  private hudCenter!: HTMLElement;
  private hudIcon!: HTMLElement;
  private hudText!: HTMLElement;
  private hudProgress!: HTMLElement;
  private hudFill!: HTMLElement;
  private speedBadge!: HTMLElement;
  private rippleLeft!: HTMLElement;
  private rippleRight!: HTMLElement;

  // State
  private isLockedState: boolean = false;
  private controlsVisible: boolean = true;
  private isFullscreenState: boolean = false;
  private hideControlsTimer: any = null;
  private previousSpeed: number = 1.0;
  private isSavedState: boolean = false;
  private isUserSeeking: boolean = false;
  private directVideoUrl: string = '';

  constructor(video: HTMLVideoElement, adapter: SiteAdapter) {
    this.video = video;
    this.adapter = adapter;

    // Remove or disable native controls and intrusive overlays
    this.video.controls = false;
    (this.video as any).webkitPlaysInline = true;
    this.video.setAttribute('playsinline', 'true');
    this.video.setAttribute('webkit-playsinline', 'true');

    // Build the DOM architecture: Wrapper -> [Video, GestureSurface, ControlsOverlay]
    this.wrapper = this.ensureWrapper();
    this.gestureSurface = this.createGestureSurface();
    this.controlsOverlay = this.createControlsOverlay();

    this.wrapper.appendChild(this.gestureSurface);
    this.wrapper.appendChild(this.controlsOverlay);

    // Suppress intrusive site click interceptors (e.g. Pornhub's .mgp_clickCapture)
    this.suppressIntrusiveOverlays();

    // Initialize gesture controller attached to the permanent gesture surface
    this.gestureController = new GestureController(this.gestureSurface, this.video, {
      toggleControls: () => this.toggleControls(),
      showControls: () => this.showControls(),
      areControlsVisible: () => this.controlsVisible,
      onDoubleTapSeek: (secs, x, y, dir) => this.handleDoubleTapSeek(secs, x, y, dir),
      onSeekPreview: (target, fmt, delta) => this.handleSeekPreview(target, fmt, delta),
      onSeekCommit: (target) => this.handleSeekCommit(target),
      onBrightnessChange: (level) => this.showBrightnessHUD(level),
      onVolumeChange: (level) => this.showVolumeHUD(level),
      onSpeedBoostStart: () => this.handleSpeedBoostStart(),
      onSpeedBoostEnd: () => this.handleSpeedBoostEnd(),
      onPinchZoomToggle: (mode) => this.handlePinchZoom(mode),
      onGestureEnd: () => this.scheduleHideControls(2000),
      isLocked: () => this.isLockedState
    });

    this.bindVideoEvents();
    this.bindControlsUI();
    this.checkInitialSavedStatus();
    this.scheduleHideControls(3500);
  }

  private suppressIntrusiveOverlays(): void {
    const selectors = this.adapter.getSelectorsToHide();
    for (const sel of selectors) {
      document.querySelectorAll(sel).forEach((el) => {
        (el as HTMLElement).style.setProperty('display', 'none', 'important');
        (el as HTMLElement).style.setProperty('pointer-events', 'none', 'important');
      });
    }
  }

  private ensureWrapper(): HTMLElement {
    const parent = this.video.parentElement;
    if (parent && parent.classList.contains('pathhub-player-wrapper')) {
      return parent;
    }

    const wrapper = document.createElement('div');
    wrapper.className = 'pathhub-player-wrapper';
    
    if (this.video.parentNode) {
      this.video.parentNode.insertBefore(wrapper, this.video);
      wrapper.appendChild(this.video);
    }
    return wrapper;
  }

  private createGestureSurface(): HTMLElement {
    const surface = document.createElement('div');
    surface.className = 'pathhub-gesture-surface';
    return surface;
  }

  private createControlsOverlay(): HTMLElement {
    const overlay = document.createElement('div');
    overlay.className = 'pathhub-controls-overlay';
    overlay.innerHTML = `
      <!-- Double tap ripples -->
      <div class="pathhub-ripple pathhub-ripple-left">
        <div class="ripple-icon">« 10s</div>
      </div>
      <div class="pathhub-ripple pathhub-ripple-right">
        <div class="ripple-icon">10s »</div>
      </div>

      <!-- Quick HUD Indicator -->
      <div class="pathhub-hud-center">
        <div class="pathhub-hud-icon"></div>
        <div class="pathhub-hud-text"></div>
        <div class="pathhub-hud-bar"><div class="pathhub-hud-fill"></div></div>
      </div>

      <!-- 2X Speed held badge -->
      <div class="pathhub-speed-badge">⚡ 2X Speed</div>

      <!-- Toast banner -->
      <div class="pathhub-toast"></div>

      <!-- Top Bar -->
      <div class="pathhub-top-bar">
        <div class="pathhub-title-container">
          <span class="pathhub-title">${this.escapeHTML(this.adapter.extractTitle(this.video))}</span>
        </div>
        <div class="pathhub-top-actions">
          <button class="pathhub-icon-btn pathhub-lock-btn" title="Lock Controls">
            <svg class="icon-unlocked" viewBox="0 0 24 24"><path fill="currentColor" d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2m6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 5-5 5 5 0 0 1 5 5v2zm-6-5a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3"/></svg>
            <svg class="icon-locked" viewBox="0 0 24 24"><path fill="currentColor" d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0 2 2m6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 5-5 5 5 0 0 1 5 5h2v2zm-4-2a2 2 0 0 0-2-2 2 2 0 0 0-2 2v2h4z"/></svg>
          </button>
          <button class="pathhub-icon-btn pathhub-like-btn" title="Save to Playlist">
            <svg class="icon-like-outline" viewBox="0 0 24 24"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <svg class="icon-like-filled" viewBox="0 0 24 24"><path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
          </button>
        </div>
      </div>

      <!-- Center Big Play Button -->
      <div class="pathhub-center-action">
        <button class="pathhub-big-play-btn">
          <svg class="icon-play" viewBox="0 0 24 24"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>
          <svg class="icon-pause" viewBox="0 0 24 24"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
        </button>
      </div>

      <!-- Bottom Bar Controls -->
      <div class="pathhub-bottom-bar">
        <div class="pathhub-scrubber-row">
          <div class="pathhub-progress-track">
            <div class="pathhub-buffer-fill"></div>
            <div class="pathhub-progress-fill"></div>
            <input type="range" class="pathhub-seek-slider" min="0" max="100" step="0.1" value="0" />
          </div>
        </div>

        <div class="pathhub-controls-row">
          <div class="pathhub-left-controls">
            <button class="pathhub-icon-btn pathhub-play-btn">
              <svg class="icon-play" viewBox="0 0 24 24"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>
              <svg class="icon-pause" viewBox="0 0 24 24"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
            </button>
            <div class="pathhub-time-display">
              <span class="pathhub-time-current">0:00</span>
              <span class="pathhub-time-divider">/</span>
              <span class="pathhub-time-duration">0:00</span>
            </div>
          </div>

          <div class="pathhub-right-controls">
            <!-- Speed Selector Button & Popover -->
            <div class="pathhub-speed-container">
              <button class="pathhub-text-btn pathhub-speed-btn">1.0x</button>
              <div class="pathhub-speed-menu">
                <div class="speed-opt" data-speed="0.5">0.5x</div>
                <div class="speed-opt" data-speed="0.75">0.75x</div>
                <div class="speed-opt active" data-speed="1.0">1.0x</div>
                <div class="speed-opt" data-speed="1.25">1.25x</div>
                <div class="speed-opt" data-speed="1.5">1.5x</div>
                <div class="speed-opt" data-speed="2.0">2.0x</div>
              </div>
            </div>

            <!-- Fullscreen Toggle -->
            <button class="pathhub-icon-btn pathhub-fullscreen-btn" title="Fullscreen">
              <svg class="icon-fs-enter" viewBox="0 0 24 24"><path fill="currentColor" d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
              <svg class="icon-fs-exit" viewBox="0 0 24 24"><path fill="currentColor" d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>
            </button>
          </div>
        </div>
      </div>
    `;

    // Cache elements
    this.playPauseBtn = overlay.querySelector('.pathhub-play-btn')!;
    this.centerPlayBtn = overlay.querySelector('.pathhub-big-play-btn')!;
    this.seekBar = overlay.querySelector('.pathhub-seek-slider')!;
    this.progressFill = overlay.querySelector('.pathhub-progress-fill')!;
    this.bufferFill = overlay.querySelector('.pathhub-buffer-fill')!;
    this.currentTimeLabel = overlay.querySelector('.pathhub-time-current')!;
    this.durationLabel = overlay.querySelector('.pathhub-time-duration')!;
    this.speedBtn = overlay.querySelector('.pathhub-speed-btn')!;
    this.speedMenu = overlay.querySelector('.pathhub-speed-menu')!;
    this.fullscreenBtn = overlay.querySelector('.pathhub-fullscreen-btn')!;
    this.lockBtn = overlay.querySelector('.pathhub-lock-btn')!;
    this.likeBtn = overlay.querySelector('.pathhub-like-btn')!;
    this.toastElement = overlay.querySelector('.pathhub-toast')!;

    this.hudCenter = overlay.querySelector('.pathhub-hud-center')!;
    this.hudIcon = overlay.querySelector('.pathhub-hud-icon')!;
    this.hudText = overlay.querySelector('.pathhub-hud-text')!;
    this.hudProgress = overlay.querySelector('.pathhub-hud-bar')!;
    this.hudFill = overlay.querySelector('.pathhub-hud-fill')!;
    this.speedBadge = overlay.querySelector('.pathhub-speed-badge')!;
    this.rippleLeft = overlay.querySelector('.pathhub-ripple-left')!;
    this.rippleRight = overlay.querySelector('.pathhub-ripple-right')!;

    return overlay;
  }

  private bindVideoEvents(): void {
    const v = this.video;

    v.addEventListener('play', () => {
      this.updatePlayState(true);
      this.scheduleHideControls(3000);
    });

    v.addEventListener('pause', () => {
      this.updatePlayState(false);
      this.showControls();
    });

    v.addEventListener('timeupdate', () => {
      if (!this.isUserSeeking) {
        this.updateProgress();
      }
      this.refreshDebugHUD();
    });

    v.addEventListener('progress', () => {
      this.updateBuffered();
    });

    v.addEventListener('durationchange', () => {
      this.durationLabel.textContent = this.formatTime(v.duration);
    });

    v.addEventListener('loadedmetadata', () => {
      this.durationLabel.textContent = this.formatTime(v.duration);
      this.updateBuffered();
      this.resolveAndCheckSaved();
    });
  }

  private bindControlsUI(): void {
    // Play/Pause toggles
    const togglePlay = (e: Event) => {
      e.stopPropagation();
      if (this.isLockedState) return;
      if (this.video.paused) {
        this.video.play().catch(() => {});
      } else {
        this.video.pause();
      }
    };
    this.playPauseBtn.addEventListener('click', togglePlay);
    this.centerPlayBtn.addEventListener('click', togglePlay);

    // Scrubber / Seekbar
    this.seekBar.addEventListener('input', (e) => {
      e.stopPropagation();
      if (this.isLockedState) return;
      this.isUserSeeking = true;
      const targetPercent = parseFloat(this.seekBar.value);
      const targetTime = (targetPercent / 100) * (this.video.duration || 1);
      this.progressFill.style.width = `${targetPercent}%`;
      this.currentTimeLabel.textContent = this.formatTime(targetTime);
    });

    this.seekBar.addEventListener('change', (e) => {
      e.stopPropagation();
      if (this.isLockedState) return;
      const targetPercent = parseFloat(this.seekBar.value);
      this.video.currentTime = (targetPercent / 100) * (this.video.duration || 1);
      this.isUserSeeking = false;
      this.scheduleHideControls(2000);
    });

    // Speed button & popover
    this.speedBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (this.isLockedState) return;
      this.speedMenu.classList.toggle('visible');
    });

    this.speedMenu.querySelectorAll('.speed-opt').forEach((opt) => {
      opt.addEventListener('click', (e) => {
        e.stopPropagation();
        const speed = parseFloat(opt.getAttribute('data-speed') || '1.0');
        this.video.playbackRate = speed;
        this.speedBtn.textContent = `${speed}x`;
        this.speedMenu.querySelectorAll('.speed-opt').forEach((o) => o.classList.remove('active'));
        opt.classList.add('active');
        this.speedMenu.classList.remove('visible');
        this.scheduleHideControls(2000);
      });
    });

    // Lock button
    this.lockBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.isLockedState = !this.isLockedState;
      this.lockBtn.classList.toggle('locked', this.isLockedState);
      this.controlsOverlay.classList.toggle('controls-locked', this.isLockedState);
      this.showToast(this.isLockedState ? 'Controls Locked' : 'Controls Unlocked');
      if (this.isLockedState) {
        this.scheduleHideControls(1500);
      }
    });

    // Like / Save button
    this.likeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.handleLikeButton();
    });

    // Fullscreen toggle
    this.fullscreenBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.toggleFullscreen();
    });
  }

  private handleLikeButton(): void {
    const res = this.adapter.resolveVideoUrl(this.video);
    const saveUrl = this.adapter.shouldSaveDirectUrl() && res.isDirect ? res.videoUrl : window.location.href;

    const metadata: VideoMetadata = {
      videoUrl: saveUrl,
      pageUrl: window.location.href,
      title: this.adapter.extractTitle(this.video),
      thumbnailUrl: this.adapter.extractThumbnail(this.video),
      timestamp: Date.now()
    };

    NativeBridge.saveVideo(metadata);

    this.isSavedState = true;
    this.likeBtn.classList.add('saved');
    this.showToast('Saved to Playlist ❤️');
  }

  private async checkInitialSavedStatus(): Promise<void> {
    const res = this.adapter.resolveVideoUrl(this.video);
    const checkUrl = this.adapter.shouldSaveDirectUrl() && res.isDirect ? res.videoUrl : window.location.href;
    this.directVideoUrl = checkUrl;

    const saved = await NativeBridge.isSaved(checkUrl);
    this.isSavedState = saved;
    this.likeBtn.classList.toggle('saved', saved);
  }

  private resolveAndCheckSaved(): void {
    this.checkInitialSavedStatus().catch(() => {});
  }

  private updatePlayState(playing: boolean): void {
    this.controlsOverlay.classList.toggle('is-playing', playing);
  }

  private updateProgress(): void {
    if (!this.video.duration) return;
    const current = this.video.currentTime || 0;
    const dur = this.video.duration;
    const percent = Math.min(100, Math.max(0, (current / dur) * 100));

    this.seekBar.value = percent.toString();
    this.progressFill.style.width = `${percent}%`;
    this.currentTimeLabel.textContent = this.formatTime(current);
  }

  private updateBuffered(): void {
    if (!this.video.duration || this.video.buffered.length === 0) return;
    const dur = this.video.duration;
    const current = this.video.currentTime || 0;

    let bufferedEnd = 0;
    for (let i = 0; i < this.video.buffered.length; i++) {
      if (this.video.buffered.start(i) <= current && this.video.buffered.end(i) >= current) {
        bufferedEnd = this.video.buffered.end(i);
        break;
      }
    }
    const percent = Math.min(100, Math.max(0, (bufferedEnd / dur) * 100));
    this.bufferFill.style.width = `${percent}%`;
  }

  // Controls Visibility & Gestures
  public toggleControls(): void {
    if (this.controlsVisible) {
      this.hideControls();
    } else {
      this.showControls();
    }
  }

  public showControls(): void {
    this.controlsVisible = true;
    this.controlsOverlay.classList.remove('controls-hidden');
    this.scheduleHideControls(3500);
  }

  public hideControls(): void {
    if (this.video.paused && !this.isLockedState) {
      return;
    }
    this.controlsVisible = false;
    this.controlsOverlay.classList.add('controls-hidden');
    this.speedMenu.classList.remove('visible');
  }

  private scheduleHideControls(delayMs: number): void {
    if (this.hideControlsTimer) {
      clearTimeout(this.hideControlsTimer);
    }
    this.hideControlsTimer = setTimeout(() => {
      this.hideControls();
    }, delayMs);
  }

  private handleDoubleTapSeek(seconds: number, clientX: number, clientY: number, direction: 'left' | 'right'): void {
    const target = Math.max(0, Math.min(this.video.duration || 100, (this.video.currentTime || 0) + seconds));
    this.video.currentTime = target;
    this.updateProgress();

    const ripple = direction === 'left' ? this.rippleLeft : this.rippleRight;
    ripple.classList.remove('animate');
    void ripple.offsetWidth;
    ripple.classList.add('animate');
    this.showControls();
  }

  private handleSeekPreview(targetTime: number, formattedTime: string, deltaSeconds: number): void {
    this.showControls();
    this.hudCenter.classList.add('visible');
    this.hudIcon.textContent = deltaSeconds >= 0 ? '⏩' : '⏪';
    this.hudText.textContent = `${formattedTime} (${deltaSeconds >= 0 ? '+' : ''}${deltaSeconds}s)`;
    this.hudProgress.style.display = 'none';
  }

  private handleSeekCommit(targetTime: number): void {
    this.video.currentTime = targetTime;
    this.updateProgress();
    this.hudCenter.classList.remove('visible');
  }

  private showBrightnessHUD(level: number): void {
    this.hudCenter.classList.add('visible');
    this.hudIcon.textContent = '☀️';
    this.hudText.textContent = `${Math.round(level * 100)}%`;
    this.hudProgress.style.display = 'block';
    this.hudFill.style.width = `${level * 100}%`;
  }

  private showVolumeHUD(level: number): void {
    this.hudCenter.classList.add('visible');
    this.hudIcon.textContent = level === 0 ? '🔇' : '🔊';
    this.hudText.textContent = `${Math.round(level * 100)}%`;
    this.hudProgress.style.display = 'block';
    this.hudFill.style.width = `${level * 100}%`;
  }

  private handleSpeedBoostStart(): void {
    this.previousSpeed = this.video.playbackRate;
    this.video.playbackRate = 2.0;
    this.speedBadge.classList.add('visible');
  }

  private handleSpeedBoostEnd(): void {
    this.video.playbackRate = this.previousSpeed;
    this.speedBadge.classList.remove('visible');
  }

  private handlePinchZoom(mode: 'contain' | 'cover'): void {
    this.video.style.objectFit = mode;
    this.showToast(mode === 'cover' ? 'Fill Screen' : 'Fit Screen');
  }

  public toggleFullscreen(): void {
    if (!this.isFullscreenState) {
      this.enterFullscreen();
    } else {
      this.exitFullscreen();
    }
  }

  public enterFullscreen(): void {
    this.isFullscreenState = true;
    this.wrapper.classList.add('is-viewport-fullscreen');
    this.controlsOverlay.classList.add('is-fullscreen');

    // Notify native iOS shell to hide bottom bar and adapt
    NativeBridge.enterFullscreen();

    // Standard HTML5 / WebKit fullscreen if supported
    if (this.wrapper.requestFullscreen) {
      this.wrapper.requestFullscreen().catch(() => {});
    }

    this.showControls();
  }

  public exitFullscreen(): void {
    this.isFullscreenState = false;
    this.wrapper.classList.remove('is-viewport-fullscreen');
    this.controlsOverlay.classList.remove('is-fullscreen');

    NativeBridge.exitFullscreen();

    if (document.fullscreenElement && document.exitFullscreen) {
      document.exitFullscreen().catch(() => {});
    }

    this.showControls();
  }

  private showToast(msg: string): void {
    this.toastElement.textContent = msg;
    this.toastElement.classList.add('visible');
    setTimeout(() => {
      this.toastElement.classList.remove('visible');
    }, 2200);
  }

  private refreshDebugHUD(): void {
    const res = this.adapter.resolveVideoUrl(this.video);
    const info: DebugInfo = {
      videoCount: document.querySelectorAll('video').length,
      activeAdapter: this.adapter.name,
      resolvedUrl: res.videoUrl,
      isBlob: res.isBlob,
      isDRM: res.isDRM,
      readyState: this.video.readyState,
      videoWidth: this.video.videoWidth,
      videoHeight: this.video.videoHeight,
      currentTime: this.video.currentTime,
      duration: this.video.duration
    };
    DebugHUD.getInstance().update(info);
  }

  private formatTime(secs: number): string {
    if (isNaN(secs) || !isFinite(secs)) return '0:00';
    const s = Math.floor(secs);
    const mins = Math.floor(s / 60);
    const rem = s % 60;
    return `${mins}:${rem < 10 ? '0' : ''}${rem}`;
  }

  private escapeHTML(str: string): string {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  public destroy(): void {
    this.gestureController.destroy();
    if (this.hideControlsTimer) clearTimeout(this.hideControlsTimer);
    this.wrapper.remove();
  }
}
