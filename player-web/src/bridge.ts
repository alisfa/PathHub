import { VideoMetadata } from './types';

declare global {
  interface Window {
    webkit?: {
      messageHandlers?: {
        saveVideo?: { postMessage(data: any): void };
        isSaved?: { postMessage(data: any): void };
        setBrightness?: { postMessage(data: any): void };
        setVolume?: { postMessage(data: any): void };
        enterFullscreen?: { postMessage(data: any): void };
        exitFullscreen?: { postMessage(data: any): void };
        showToast?: { postMessage(data: any): void };
        debugLog?: { postMessage(data: any): void };
      };
    };
    AndroidBridge?: {
      saveVideo(json: string): void;
      isSaved(videoUrl: string): boolean | string;
      setBrightness(value: number): void;
      setVolume(value: number): void;
      enterFullscreen(): void;
      exitFullscreen(): void;
      showToast?(message: string): void;
      debugLog?(message: string): void;
    };
    __pathHubSavedCallbacks?: Record<string, (saved: boolean) => void>;
    __pathHubReceiveSavedStatus?: (callbackId: string, saved: boolean) => void;
  }
}

// Global callback receiver for asynchronous iOS isSaved responses
if (typeof window !== 'undefined') {
  window.__pathHubSavedCallbacks = window.__pathHubSavedCallbacks || {};
  window.__pathHubReceiveSavedStatus = (callbackId: string, saved: boolean) => {
    if (window.__pathHubSavedCallbacks && window.__pathHubSavedCallbacks[callbackId]) {
      window.__pathHubSavedCallbacks[callbackId](saved);
      delete window.__pathHubSavedCallbacks[callbackId];
    }
  };
}

export class NativeBridge {
  private static callbackCounter = 0;

  public static isIOS(): boolean {
    return !!(window.webkit && window.webkit.messageHandlers);
  }

  public static isAndroid(): boolean {
    return !!window.AndroidBridge;
  }

  public static saveVideo(metadata: VideoMetadata): void {
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.saveVideo) {
        window.webkit.messageHandlers.saveVideo.postMessage(metadata);
      } else if (this.isAndroid() && window.AndroidBridge?.saveVideo) {
        window.AndroidBridge.saveVideo(JSON.stringify(metadata));
      } else {
        console.log('[NativeBridge Fallback] saveVideo:', metadata);
        // Fallback in web browser: store in localStorage
        const stored = JSON.parse(localStorage.getItem('path_hub_local_saved') || '[]');
        stored.push(metadata);
        localStorage.setItem('path_hub_local_saved', JSON.stringify(stored));
      }
    } catch (e) {
      console.error('[NativeBridge] saveVideo failed:', e);
    }
  }

  public static async isSaved(videoUrl: string): Promise<boolean> {
    try {
      if (this.isAndroid() && window.AndroidBridge?.isSaved) {
        const result = window.AndroidBridge.isSaved(videoUrl);
        return typeof result === 'boolean' ? result : result === 'true';
      }

      if (this.isIOS() && window.webkit?.messageHandlers?.isSaved) {
        return new Promise<boolean>((resolve) => {
          const callbackId = 'cb_' + (++this.callbackCounter) + '_' + Date.now();
          if (window.__pathHubSavedCallbacks) {
            window.__pathHubSavedCallbacks[callbackId] = resolve;
          }
          window.webkit!.messageHandlers!.isSaved!.postMessage({
            videoUrl,
            callbackId
          });

          // Fallback timeout in case native shell doesn't respond
          setTimeout(() => {
            if (window.__pathHubSavedCallbacks && window.__pathHubSavedCallbacks[callbackId]) {
              delete window.__pathHubSavedCallbacks[callbackId];
              resolve(false);
            }
          }, 1500);
        });
      }

      // Local storage fallback for standalone browser testing
      const stored = JSON.parse(localStorage.getItem('path_hub_local_saved') || '[]');
      return stored.some((item: any) => item.videoUrl === videoUrl);
    } catch (e) {
      console.error('[NativeBridge] isSaved check failed:', e);
      return false;
    }
  }

  public static setBrightness(level: number): void {
    // level: 0.0 to 1.0
    const clamped = Math.max(0, Math.min(1, level));
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.setBrightness) {
        window.webkit.messageHandlers.setBrightness.postMessage({ brightness: clamped });
      } else if (this.isAndroid() && window.AndroidBridge?.setBrightness) {
        window.AndroidBridge.setBrightness(clamped);
      }
    } catch (e) {
      console.error('[NativeBridge] setBrightness failed:', e);
    }
  }

  public static setVolume(level: number): void {
    // level: 0.0 to 1.0
    const clamped = Math.max(0, Math.min(1, level));
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.setVolume) {
        window.webkit.messageHandlers.setVolume.postMessage({ volume: clamped });
      } else if (this.isAndroid() && window.AndroidBridge?.setVolume) {
        window.AndroidBridge.setVolume(clamped);
      }
    } catch (e) {
      console.error('[NativeBridge] setVolume failed:', e);
    }
  }

  public static enterFullscreen(): void {
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.enterFullscreen) {
        window.webkit.messageHandlers.enterFullscreen.postMessage({});
      } else if (this.isAndroid() && window.AndroidBridge?.enterFullscreen) {
        window.AndroidBridge.enterFullscreen();
      }
    } catch (e) {
      console.error('[NativeBridge] enterFullscreen failed:', e);
    }
  }

  public static exitFullscreen(): void {
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.exitFullscreen) {
        window.webkit.messageHandlers.exitFullscreen.postMessage({});
      } else if (this.isAndroid() && window.AndroidBridge?.exitFullscreen) {
        window.AndroidBridge.exitFullscreen();
      }
    } catch (e) {
      console.error('[NativeBridge] exitFullscreen failed:', e);
    }
  }

  public static log(msg: string): void {
    try {
      if (this.isIOS() && window.webkit?.messageHandlers?.debugLog) {
        window.webkit.messageHandlers.debugLog.postMessage({ message: msg });
      } else if (this.isAndroid() && window.AndroidBridge?.debugLog) {
        window.AndroidBridge.debugLog(msg);
      } else {
        console.log('[NativeBridge]', msg);
      }
    } catch (_) {}
  }
}
