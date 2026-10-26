import { SiteAdapter } from './types';
import { VideoResolutionResult } from '../types';

export class GenericAdapter implements SiteAdapter {
  public name = 'Generic Web Video Adapter';
  public domains = ['*'];

  public resolveVideoUrl(video: HTMLVideoElement): VideoResolutionResult {
    let resolvedUrl = video.currentSrc || video.src || '';

    // Check inner <source> tags if video.src is empty
    if (!resolvedUrl || resolvedUrl.startsWith('blob:')) {
      const sources = video.querySelectorAll('source');
      for (let i = 0; i < sources.length; i++) {
        const src = sources[i].getAttribute('src') || sources[i].getAttribute('data-src');
        if (src) {
          try {
            resolvedUrl = new URL(src, window.location.href).href;
            break;
          } catch (_) {
            resolvedUrl = src;
            break;
          }
        }
      }
    }

    // Check data attributes on video tag
    if (!resolvedUrl) {
      const dataSrc = video.getAttribute('data-src') ||
                      video.getAttribute('data-url') ||
                      video.getAttribute('data-video-src');
      if (dataSrc) {
        try {
          resolvedUrl = new URL(dataSrc, window.location.href).href;
        } catch (_) {
          resolvedUrl = dataSrc;
        }
      }
    }

    const isBlob = resolvedUrl.startsWith('blob:');
    // Check if mediaKeys are attached (DRM / EME)
    const isDRM = !!((video as any).mediaKeys || (video as any).webkitKeys);

    const isDirect = !!(
      resolvedUrl &&
      !isBlob &&
      (resolvedUrl.includes('.mp4') ||
       resolvedUrl.includes('.m3u8') ||
       resolvedUrl.includes('.webm') ||
       resolvedUrl.includes('.mov') ||
       resolvedUrl.includes('.mpd'))
    );

    return {
      videoUrl: resolvedUrl || window.location.href,
      isDirect,
      isBlob,
      isDRM,
      format: resolvedUrl.split('?')[0].split('.').pop()
    };
  }

  public extractTitle(video: HTMLVideoElement): string {
    // 1. Video title / aria-label
    const videoTitle = video.getAttribute('title') || video.getAttribute('aria-label');
    if (videoTitle && videoTitle.trim().length > 0) {
      return videoTitle.trim();
    }

    // 2. OpenGraph or Twitter title
    const ogTitle = document.querySelector('meta[property="og:title"]')?.getAttribute('content');
    if (ogTitle && ogTitle.trim().length > 0) {
      return ogTitle.trim();
    }

    const twitterTitle = document.querySelector('meta[name="twitter:title"]')?.getAttribute('content');
    if (twitterTitle && twitterTitle.trim().length > 0) {
      return twitterTitle.trim();
    }

    // 3. Document / Page title
    if (document.title && document.title.trim().length > 0) {
      return document.title.replace(/\s*[-|]\s*.*$/, '').trim(); // Remove suffix like " - SiteName"
    }

    return 'Video on ' + window.location.hostname;
  }

  public extractThumbnail(video: HTMLVideoElement): string {
    const isImage = (url: string | null | undefined): boolean => {
      if (!url || typeof url !== 'string') return false;
      const u = url.toLowerCase().trim();
      if (u.startsWith('data:image/')) return true;
      if (u === window.location.href || u.includes('.html') || u.includes('.php')) return false;
      return true;
    };

    // 1. Poster attribute on video element
    if (isImage(video.poster) && video.poster.trim().length > 0) {
      try {
        return new URL(video.poster, window.location.href).href;
      } catch (_) {
        return video.poster;
      }
    }

    // 2. OpenGraph or Twitter image
    const ogImage = document.querySelector('meta[property="og:image"], meta[property="og:image:secure_url"]')?.getAttribute('content');
    if (isImage(ogImage) && ogImage!.trim().length > 0) {
      try {
        return new URL(ogImage!, window.location.href).href;
      } catch (_) {
        return ogImage!;
      }
    }

    const twitterImage = document.querySelector('meta[name="twitter:image"], meta[name="twitter:image:src"]')?.getAttribute('content');
    if (isImage(twitterImage) && twitterImage!.trim().length > 0) {
      try {
        return new URL(twitterImage!, window.location.href).href;
      } catch (_) {
        return twitterImage!;
      }
    }

    // 3. Try live video canvas snapshot
    try {
      if (video && video.videoWidth > 0 && video.readyState >= 2) {
        const canvas = document.createElement('canvas');
        canvas.width = 320;
        canvas.height = 180;
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL('image/jpeg', 0.65);
          if (dataUrl && dataUrl.length > 500 && !dataUrl.includes('data:,')) {
            return dataUrl;
          }
        }
      }
    } catch (_) {}

    // 4. High-res favicon fallback
    return `https://www.google.com/s2/favicons?domain=${window.location.hostname}&sz=256`;
  }

  public getSelectorsToHide(): string[] {
    // Common intrusive floating ad bars or overlays
    return [
      '.video-ads',
      '.ytp-chrome-top',
      '.ytp-chrome-bottom',
      '.vjs-control-bar',
      '.jw-controls'
    ];
  }

  public shouldSaveDirectUrl(): boolean {
    return true;
  }
}
