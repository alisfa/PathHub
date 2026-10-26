import { DebugInfo } from './types';

export class DebugHUD {
  private static instance: DebugHUD | null = null;
  private container: HTMLElement | null = null;
  private isVisible: boolean = false;

  public static getInstance(): DebugHUD {
    if (!this.instance) {
      this.instance = new DebugHUD();
    }
    return this.instance;
  }

  public toggle(info?: DebugInfo): void {
    if (this.isVisible) {
      this.hide();
    } else {
      this.show(info);
    }
  }

  public show(info?: DebugInfo): void {
    if (!this.container) {
      this.createDOM();
    }
    this.updateContent(info);
    this.container!.style.display = 'block';
    this.isVisible = true;
  }

  public hide(): void {
    if (this.container) {
      this.container.style.display = 'none';
    }
    this.isVisible = false;
  }

  public update(info: DebugInfo): void {
    if (this.isVisible && this.container) {
      this.updateContent(info);
    }
  }

  private createDOM(): void {
    this.container = document.createElement('div');
    this.container.id = 'pathhub-debug-hud';
    this.container.className = 'pathhub-debug-panel';
    this.container.innerHTML = `
      <div class="pathhub-debug-header">
        <span>⚙️ Path Hub Inspector</span>
        <button id="pathhub-debug-close">✕</button>
      </div>
      <div class="pathhub-debug-body" id="pathhub-debug-body">
        Loading video diagnostics...
      </div>
    `;
    document.body.appendChild(this.container);

    const closeBtn = this.container.querySelector('#pathhub-debug-close');
    closeBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      this.hide();
    });
  }

  private updateContent(info?: DebugInfo): void {
    const body = document.getElementById('pathhub-debug-body');
    if (!body) return;

    if (!info) {
      const videos = document.querySelectorAll('video');
      body.innerHTML = `
        <div class="debug-row"><strong>Page:</strong> ${window.location.hostname}</div>
        <div class="debug-row"><strong>Video Count:</strong> ${videos.length}</div>
        <div class="debug-row"><em>Tap a video to inspect media stream diagnostics.</em></div>
      `;
      return;
    }

    body.innerHTML = `
      <div class="debug-row"><strong>Adapter:</strong> ${info.activeAdapter}</div>
      <div class="debug-row"><strong>Video Count:</strong> ${info.videoCount}</div>
      <div class="debug-row"><strong>Status:</strong> ReadyState=${info.readyState} (${this.getReadyStateLabel(info.readyState)})</div>
      <div class="debug-row"><strong>Blob Stream:</strong> <span class="badge ${info.isBlob ? 'badge-warn' : 'badge-ok'}">${info.isBlob ? 'YES (blob:)' : 'NO'}</span></div>
      <div class="debug-row"><strong>DRM / EME:</strong> <span class="badge ${info.isDRM ? 'badge-err' : 'badge-ok'}">${info.isDRM ? 'YES (Protected)' : 'NO'}</span></div>
      <div class="debug-row"><strong>Dimensions:</strong> ${info.videoWidth} x ${info.videoHeight}</div>
      <div class="debug-row"><strong>Playback:</strong> ${Math.round(info.currentTime)}s / ${Math.round(info.duration)}s</div>
      <div class="debug-row debug-url"><strong>Resolved URL:</strong><br/><code>${info.resolvedUrl || 'N/A'}</code></div>
    `;
  }

  private getReadyStateLabel(state: number): string {
    switch (state) {
      case 0: return 'HAVE_NOTHING';
      case 1: return 'HAVE_METADATA';
      case 2: return 'HAVE_CURRENT_DATA';
      case 3: return 'HAVE_FUTURE_DATA';
      case 4: return 'HAVE_ENOUGH_DATA';
      default: return 'UNKNOWN';
    }
  }
}
