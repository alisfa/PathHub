import { AdapterRegistry } from './adapters/registry';
import { NativeBridge } from './bridge';
import { VideoMetadata } from './types';

class PathHubDetector {
  private static instance: PathHubDetector | null = null;
  private currentMetadata: VideoMetadata | null = null;
  private saveBadge: HTMLElement | null = null;
  private isSaved: boolean = false;

  public static init(): PathHubDetector {
    if (!this.instance) {
      this.instance = new PathHubDetector();
    }
    return this.instance;
  }

  constructor() {
    this.initPopupBlocker();
    this.initClickInterceptor();
    this.injectStyles();
    this.initSPAListeners();
    this.initObserver();
    this.initFullscreenListeners();
    this.scanVideos();
  }

  private initPopupBlocker(): void {
    try {
      (window as any).open = function (url?: string | URL, _target?: string, _features?: string) {
        console.log('[PathHub Popup Blocker] Suppressed window.open popup:', url);
        return null;
      };
    } catch (_) {}
  }

  private initClickInterceptor(): void {
    document.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (!target) return;

      // 1. Defang click capture overlays (e.g. Pornhub mgp_clickCapture, interstitial wrappers, etc.)
      const overlay = target.closest('.mgp_clickCapture, .interstitial-wrapper, #abTestingFloatingBanner, .ad-overlay, .adOverlay, [class*="ad_wrapper"]');
      if (overlay) {
        e.preventDefault();
        e.stopImmediatePropagation();
        overlay.remove();
        console.log('[PathHub AdBlocker] Blocked and removed ad overlay element');
        return false;
      }

      // 2. Intercept links pointing to interstitials or ad networks
      const link = target.closest('a') as HTMLAnchorElement | null;
      if (link && link.href) {
        const h = link.href.toLowerCase();
        if (h.includes('/interstitial') || h.includes('trafficjunky') || h.includes('exoclick') || h.includes('tsyndicate') || h.includes('popunder') || h.includes('click.php')) {
          e.preventDefault();
          e.stopImmediatePropagation();
          console.log('[PathHub AdBlocker] Blocked ad click link:', link.href);
          return false;
        }
      }
    }, true); // useCapture = true: runs BEFORE site listeners!
  }

  private isFullscreenActive: boolean = false;

  private checkFullscreenState(): void {
    const videos = Array.from(document.querySelectorAll<HTMLVideoElement>('video'));
    const isDocFS = !!(document.fullscreenElement || (document as any).webkitFullscreenElement || (document as any).webkitCurrentFullScreenElement);
    const isVideoFS = videos.some(v => (v as any).webkitDisplayingFullscreen || (v as any).webkitPresentationMode === 'fullscreen');
    const isFS = isDocFS || isVideoFS;

    if (isFS && !this.isFullscreenActive) {
      this.isFullscreenActive = true;
      NativeBridge.enterFullscreen();
      this.hideBadge();
    } else if (!isFS && this.isFullscreenActive) {
      this.isFullscreenActive = false;
      NativeBridge.exitFullscreen();
      this.showBadge();
    }
  }

  private initFullscreenListeners(): void {
    const handler = () => this.checkFullscreenState();

    document.addEventListener('fullscreenchange', handler);
    document.addEventListener('webkitfullscreenchange', handler);
    window.addEventListener('resize', handler);
    window.addEventListener('orientationchange', handler);

    // High-frequency, lightweight state checker
    setInterval(() => this.checkFullscreenState(), 250);
  }

  private injectStyles(): void {
    if (!document.getElementById('pathhub-save-styles')) {
      const style = document.createElement('style');
      style.id = 'pathhub-save-styles';
      style.textContent = `
        /* Defang adult site ad overlays and click-jackers */
        .mgp_clickCapture,
        .interstitial-wrapper,
        #abTestingFloatingBanner,
        .ad-overlay,
        .adOverlay,
        div[id*="ad_"],
        div[id*="banner"],
        iframe[src*="trafficjunky"],
        iframe[src*="exoclick"],
        iframe[src*="tsyndicate"] {
          display: none !important;
          pointer-events: none !important;
          opacity: 0 !important;
          visibility: hidden !important;
          width: 0 !important;
          height: 0 !important;
          z-index: -99999 !important;
        }

        /* Path Hub Liquid Glass Floating Save Pill */
        .pathhub-save-pill {
          position: fixed !important;
          bottom: calc(env(safe-area-inset-bottom, 0px) + 84px) !important;
          right: calc(env(safe-area-inset-right, 0px) + 16px) !important;
          display: inline-flex !important;
          align-items: center !important;
          gap: 8px !important;
          background: rgba(15, 23, 42, 0.75) !important;
          backdrop-filter: blur(24px) saturate(180%) !important;
          -webkit-backdrop-filter: blur(24px) saturate(180%) !important;
          border: 1px solid rgba(255, 255, 255, 0.25) !important;
          border-radius: 30px !important;
          padding: 8px 14px !important;
          color: #ffffff !important;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
          font-size: 13px !important;
          font-weight: 600 !important;
          letter-spacing: -0.2px !important;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.3) !important;
          z-index: 2147483647 !important;
          cursor: pointer !important;
          user-select: none !important;
          -webkit-user-select: none !important;
          touch-action: manipulation !important;
          transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), background 0.25s ease, opacity 0.2s ease, box-shadow 0.25s ease !important;
          opacity: 0;
          pointer-events: none;
        }

        .pathhub-save-pill.visible {
          opacity: 1 !important;
          pointer-events: auto !important;
        }

        .pathhub-save-pill:active {
          transform: scale(0.92) !important;
        }

        .pathhub-save-pill.is-saved {
          background: rgba(225, 29, 72, 0.82) !important;
          border-color: rgba(255, 255, 255, 0.4) !important;
          box-shadow: 0 0 25px rgba(225, 29, 72, 0.65), 0 8px 24px rgba(0, 0, 0, 0.4) !important;
        }

        .pathhub-save-pill svg {
          width: 16px !important;
          height: 16px !important;
          pointer-events: none !important;
        }

        .pathhub-save-pill .save-close {
          display: inline-flex !important;
          align-items: center !important;
          justify-content: center !important;
          margin-left: 2px !important;
          width: 18px !important;
          height: 18px !important;
          border-radius: 50% !important;
          font-size: 15px !important;
          line-height: 1 !important;
          color: rgba(255, 255, 255, 0.6) !important;
          transition: color 0.15s ease, background 0.15s ease !important;
        }

        .pathhub-save-pill .save-close:hover {
          color: #ffffff !important;
          background: rgba(255, 255, 255, 0.15) !important;
        }

        /* Liquid Glass Toast banner */
        .pathhub-toast-banner {
          position: fixed !important;
          bottom: calc(env(safe-area-inset-bottom, 0px) + 84px) !important;
          left: 50% !important;
          transform: translateX(-50%) translateY(20px) !important;
          background: rgba(15, 23, 42, 0.85) !important;
          backdrop-filter: blur(28px) saturate(190%) !important;
          -webkit-backdrop-filter: blur(28px) saturate(190%) !important;
          border: 1px solid rgba(255, 255, 255, 0.28) !important;
          color: #ffffff !important;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
          font-size: 14px !important;
          font-weight: 600 !important;
          padding: 12px 24px !important;
          border-radius: 28px !important;
          box-shadow: 0 14px 40px rgba(0, 0, 0, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.35) !important;
          z-index: 2147483647 !important;
          opacity: 0 !important;
          pointer-events: none !important;
          transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275), opacity 0.2s ease !important;
        }

        .pathhub-toast-banner.show {
          opacity: 1 !important;
          transform: translateX(-50%) translateY(0) !important;
        }
      `;
      document.head.appendChild(style);
    }
  }

  private initSPAListeners(): void {
    window.addEventListener('popstate', () => this.scheduleScan());

    const origPush = history.pushState;
    history.pushState = function (...args) {
      const res = origPush.apply(this, args);
      window.dispatchEvent(new Event('pathhub:routechange'));
      return res;
    };

    const origReplace = history.replaceState;
    history.replaceState = function (...args) {
      const res = origReplace.apply(this, args);
      window.dispatchEvent(new Event('pathhub:routechange'));
      return res;
    };

    window.addEventListener('pathhub:routechange', () => this.scheduleScan());
  }

  private initObserver(): void {
    const observer = new MutationObserver(() => this.scheduleScan());
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  private scanDebounce: any = null;
  private scheduleScan(): void {
    if (this.scanDebounce) clearTimeout(this.scanDebounce);
    this.scanDebounce = setTimeout(() => this.scanVideos(), 300);
  }

  public async scanVideos(): Promise<void> {
    const videos = document.querySelectorAll<HTMLVideoElement>('video');
    if (videos.length === 0) {
      this.hideBadge();
      return;
    }

    // Attach native fullscreen listeners to every video
    videos.forEach((video) => {
      if (!(video as any).__pathHubFullscreenBound) {
        (video as any).__pathHubFullscreenBound = true;
        video.addEventListener('webkitbeginfullscreen', () => {
          this.isFullscreenActive = true;
          NativeBridge.enterFullscreen();
          this.hideBadge();
        });
        video.addEventListener('webkitendfullscreen', () => {
          this.isFullscreenActive = false;
          NativeBridge.exitFullscreen();
          this.showBadge();
        });
        video.addEventListener('webkitpresentationmodechanged', () => {
          this.checkFullscreenState();
        });
      }
    });

    // Pick the most prominent or playing video
    let selectedVideo = videos[0];
    for (let i = 0; i < videos.length; i++) {
      if (!videos[i].paused && videos[i].currentTime > 0) {
        selectedVideo = videos[i];
        break;
      }
    }

    const adapter = AdapterRegistry.getAdapterForHostname(window.location.hostname);
    const res = adapter.resolveVideoUrl(selectedVideo);
    const saveUrl = adapter.shouldSaveDirectUrl() && res.isDirect ? res.videoUrl : window.location.href;

    this.currentMetadata = {
      videoUrl: saveUrl,
      pageUrl: window.location.href,
      title: adapter.extractTitle(selectedVideo),
      thumbnailUrl: adapter.extractThumbnail(selectedVideo),
      timestamp: Date.now()
    };

    // Check saved status
    this.isSaved = await NativeBridge.isSaved(saveUrl);

    // Show floating save badge
    this.showBadge();

    // Notify native shell that video is detected so native bottom bar Like button is enabled
    if (window.webkit?.messageHandlers?.saveVideo) {
      // Expose globally for native bottom bar triggers
      (window as any).__pathHubActiveVideo = this.currentMetadata;
    }
  }

  private showBadge(): void {
    if (!this.saveBadge) {
      this.saveBadge = document.createElement('div');
      this.saveBadge.className = 'pathhub-save-pill';
      this.saveBadge.innerHTML = `
        <svg class="save-icon" viewBox="0 0 24 24"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
        <span class="save-label">Save Video</span>
        <span class="save-close" title="Dismiss">&times;</span>
      `;
      this.saveBadge.addEventListener('click', (e) => {
        const target = e.target as HTMLElement;
        if (target && target.classList.contains('save-close')) {
          e.stopPropagation();
          this.hideBadge();
          return;
        }
        e.stopPropagation();
        this.saveCurrentVideo();
      });
      document.body.appendChild(this.saveBadge);
    }

    this.updateBadgeUI();
    this.saveBadge.classList.add('visible');
  }

  private hideBadge(): void {
    if (this.saveBadge) {
      this.saveBadge.classList.remove('visible');
    }
  }

  private updateBadgeUI(): void {
    if (!this.saveBadge) return;
    const label = this.saveBadge.querySelector('.save-label');
    const icon = this.saveBadge.querySelector('.save-icon');

    if (this.isSaved) {
      this.saveBadge.classList.add('is-saved');
      if (label) label.textContent = 'Saved ❤️';
      if (icon) {
        icon.innerHTML = `<path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>`;
      }
    } else {
      this.saveBadge.classList.remove('is-saved');
      if (label) label.textContent = 'Save Video';
      if (icon) {
        icon.innerHTML = `<path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>`;
      }
    }
  }

  public saveCurrentVideo(): void {
    if (!this.currentMetadata) return;

    NativeBridge.saveVideo(this.currentMetadata);
    this.isSaved = true;
    this.updateBadgeUI();
    this.showToast('Saved to Playlist ❤️');
  }

  private showToast(msg: string): void {
    let toast = document.querySelector('.pathhub-toast-banner') as HTMLElement;
    if (!toast) {
      toast = document.createElement('div');
      toast.className = 'pathhub-toast-banner';
      document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => {
      toast.classList.remove('show');
    }, 2500);
  }
}

// Global hook for native bottom bar Save button to call
(window as any).__pathHubSaveCurrentVideo = () => {
  PathHubDetector.init().saveCurrentVideo();
};

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => PathHubDetector.init());
  } else {
    PathHubDetector.init();
  }
}

export default PathHubDetector;
