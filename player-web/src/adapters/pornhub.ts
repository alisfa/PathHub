import { SiteAdapter } from './types';
import { VideoResolutionResult } from '../types';

export class PornhubAdapter implements SiteAdapter {
  public name = 'Pornhub Media Adapter';
  public domains = ['pornhub.com', 'rt.pornhub.com', 'pornhubpremium.com'];

  public resolveVideoUrl(video: HTMLVideoElement): VideoResolutionResult {
    // Actively remove ad interceptor overlays
    try {
      const overlays = document.querySelectorAll('.mgp_clickCapture, .interstitial-wrapper, #abTestingFloatingBanner, .ad-overlay, [class*="ad_wrapper"]');
      overlays.forEach(el => el.remove());
    } catch (_) {}

    let resolvedUrl = '';
    let isDirect = false;

    // 1. Try to extract direct MP4 or HLS manifest from window.flashvars or playerObjList
    try {
      const win = window as any;
      // Search for any flashvars global variable (e.g. flashvars_1234567)
      const flashvarKeys = Object.keys(win).filter(k => k.startsWith('flashvars_'));
      for (const k of flashvarKeys) {
        const fv = win[k];
        if (fv && fv.mediaDefinitions && Array.isArray(fv.mediaDefinitions)) {
          // Look for direct 1080p, 720p mp4 or hls master playlist
          const sorted = [...fv.mediaDefinitions].sort((a: any, b: any) => {
            const qA = parseInt(a.quality || '0', 10);
            const qB = parseInt(b.quality || '0', 10);
            return qB - qA;
          });

          for (const m of sorted) {
            if (m.videoUrl && typeof m.videoUrl === 'string' && m.videoUrl.length > 0) {
              resolvedUrl = m.videoUrl;
              isDirect = resolvedUrl.includes('.mp4') || resolvedUrl.includes('.m3u8');
              break;
            }
          }
        }
        if (resolvedUrl) break;
      }

      // Check playerObjList fallback
      if (!resolvedUrl && win.playerObjList) {
        const playerKeys = Object.keys(win.playerObjList);
        for (const pk of playerKeys) {
          const playerObj = win.playerObjList[pk];
          if (playerObj?.mediaDefinitions) {
            for (const m of playerObj.mediaDefinitions) {
              if (m.videoUrl && typeof m.videoUrl === 'string') {
                resolvedUrl = m.videoUrl;
                isDirect = resolvedUrl.includes('.mp4') || resolvedUrl.includes('.m3u8');
                break;
              }
            }
          }
          if (resolvedUrl) break;
        }
      }
    } catch (_) {
      // Ignore security/cross-context read errors
    }

    // 2. Try scraping inline scripts for mediaDefinitions or videoUrl
    if (!resolvedUrl) {
      const scripts = document.querySelectorAll('script');
      for (let i = 0; i < scripts.length; i++) {
        const text = scripts[i].textContent || '';
        if (text.includes('mediaDefinitions') || text.includes('flashvars_')) {
          const match = text.match(/"videoUrl"\s*:\s*"([^"]+)"/);
          if (match && match[1]) {
            resolvedUrl = match[1].replace(/\\\//g, '/');
            isDirect = resolvedUrl.includes('.mp4') || resolvedUrl.includes('.m3u8');
            break;
          }
        }
      }
    }

    // 3. Fallback to video element currentSrc or src
    if (!resolvedUrl) {
      resolvedUrl = video.currentSrc || video.src || '';
      isDirect = resolvedUrl.includes('.mp4') || resolvedUrl.includes('.m3u8');
    }

    const isBlob = resolvedUrl.startsWith('blob:');
    const isDRM = !!((video as any).mediaKeys);

    return {
      videoUrl: resolvedUrl || window.location.href,
      isDirect,
      isBlob,
      isDRM,
      format: isDirect ? (resolvedUrl.includes('.m3u8') ? 'hls' : 'mp4') : 'stream'
    };
  }

  public extractTitle(video: HTMLVideoElement): string {
    // 1. Pornhub page video title
    const heading = document.querySelector('h1.title, .title-container .title, .inlineFree h1');
    if (heading && heading.textContent && heading.textContent.trim().length > 0) {
      return heading.textContent.trim();
    }

    // 2. OpenGraph title
    const ogTitle = document.querySelector('meta[property="og:title"]')?.getAttribute('content');
    if (ogTitle && ogTitle.trim().length > 0) {
      return ogTitle.replace(/\s*-\s*Pornhub.*$/i, '').trim();
    }

    // 3. Document title fallback
    if (document.title) {
      return document.title.replace(/\s*-\s*Pornhub.*$/i, '').trim();
    }

    return 'Pornhub Video';
  }

  public extractThumbnail(video: HTMLVideoElement): string {
    const isImage = (url: string | null | undefined): boolean => {
      if (!url || typeof url !== 'string') return false;
      const u = url.toLowerCase().trim();
      // Exclude logos, svgs, and 1x1 blank placeholders
      if (u.includes('logo') || u.includes('.svg') || u.includes('blank.gif') || u.includes('spacer.gif') || u.includes('pixel')) {
        return false;
      }
      if (u.includes('/view_video.php') || u.includes('/shorties/') || u === window.location.href.toLowerCase()) return false;
      if (u.startsWith('data:image/')) {
        return u.length > 1000;
      }
      return u.includes('.jpg') || u.includes('.jpeg') || u.includes('.png') ||
             u.includes('.webp') || u.includes('phncdn') || u.includes('pics') ||
             u.includes('image') || u.includes('thumb') || u.includes('original') ||
             u.includes('vts:');
    };

    // 1. JSON-LD Schema VideoObject (contains official CDN video thumbnail)
    try {
      const ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
      for (let i = 0; i < ldScripts.length; i++) {
        const text = ldScripts[i].textContent || '';
        if (text.includes('thumbnailUrl') || text.includes('thumbnail')) {
          const parsed = JSON.parse(text);
          const t = parsed.thumbnailUrl || parsed.thumbnail;
          if (isImage(t)) return t;
        }
      }
    } catch (_) {}

    // 2. OpenGraph or Twitter Card
    const ogImage = document.querySelector('meta[property="og:image"], meta[property="og:image:url"], meta[property="og:image:secure_url"]')?.getAttribute('content');
    if (isImage(ogImage)) {
      return ogImage!;
    }

    const twitterImage = document.querySelector('meta[name="twitter:image"]')?.getAttribute('content');
    if (isImage(twitterImage)) {
      return twitterImage!;
    }

    const linkImage = document.querySelector('link[rel="image_src"]')?.getAttribute('content') || document.querySelector('link[rel="image_src"]')?.getAttribute('href');
    if (isImage(linkImage)) {
      return linkImage!;
    }

    // 3. Poster on video tag
    if (isImage(video.poster)) {
      return video.poster;
    }
    const videoAttrPoster = video.getAttribute('poster');
    if (isImage(videoAttrPoster)) {
      return videoAttrPoster!;
    }

    // 4. Flashvars / Player object poster if available
    try {
      const win = window as any;
      const fvKey = Object.keys(win).find(k => k.startsWith('flashvars_'));
      if (fvKey && isImage(win[fvKey]?.image_url)) {
        return win[fvKey].image_url;
      }
      if (fvKey && isImage(win[fvKey]?.poster)) {
        return win[fvKey].poster;
      }
      if (win.playerObjList) {
        for (const pk of Object.keys(win.playerObjList)) {
          const po = win.playerObjList[pk];
          if (isImage(po?.image_url)) return po.image_url;
          if (isImage(po?.poster)) return po.poster;
          if (isImage(po?.options?.poster)) return po.options.poster;
        }
      }
    } catch (_) {}

    // 5. Shorties / Reels container images (check data-src, data-thumb, data-poster, background-image)
    try {
      const reelContainer = video.closest('.shorties-container, .short-item, .short-card, .reel-item, .video-element-wrapper, .swiper-slide, [class*="short"]');
      const searchRoot = reelContainer || document;
      const imgs = searchRoot.querySelectorAll('.shorties-container img, .short-item img, .short-card img, .reel-item img, [class*="short"] img, [class*="reel"] img');
      for (let i = 0; i < imgs.length; i++) {
        const el = imgs[i] as HTMLImageElement;
        const candidate = el.getAttribute('data-src') || el.getAttribute('data-thumb') || el.getAttribute('data-poster') || el.getAttribute('data-mediumthumb') || el.src;
        if (isImage(candidate)) {
          return candidate;
        }
      }
      // Check background-image
      const bgEls = searchRoot.querySelectorAll('[style*="background-image"]');
      for (let i = 0; i < bgEls.length; i++) {
        const style = (bgEls[i] as HTMLElement).style.backgroundImage || '';
        const match = style.match(/url\(['"]?([^'"]+)['"]?\)/);
        if (match && isImage(match[1])) {
          return match[1];
        }
      }
    } catch (_) {}

    // 6. In-page player posters and images
    try {
      const playerImgs = document.querySelectorAll('#player img, .mgp_container img, .playerWrapper img, .inlineFree img');
      for (let i = 0; i < playerImgs.length; i++) {
        const el = playerImgs[i] as HTMLImageElement;
        const candidate = el.getAttribute('data-src') || el.getAttribute('data-thumb') || el.getAttribute('data-poster') || el.src;
        if (isImage(candidate)) {
          return candidate;
        }
      }
    } catch (_) {}

    // 7. Try live video canvas snapshot
    try {
      if (video && video.videoWidth > 0 && video.readyState >= 2) {
        const canvas = document.createElement('canvas');
        canvas.width = 320;
        canvas.height = 180;
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL('image/jpeg', 0.65);
          if (isImage(dataUrl)) {
            return dataUrl;
          }
        }
      }
    } catch (_) {}

    // 8. Verified fallback posters on phncdn CDN
    const isReelPage = window.location.href.includes('/shorties/') || window.location.href.includes('/shorts/') || window.location.href.includes('shorties');
    if (isReelPage) {
      return 'https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)32.jpg';
    }

    return 'https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)105.jpg';
  }

  public getSelectorsToHide(): string[] {
    // Hide native Pornhub player overlays and click capturers so our custom player receives all taps
    return [
      '.mgp_clickCapture',
      '.mgp_controlBar',
      '.mgp_bigPlay',
      '.mgp_watermark',
      '.mgp_overlay',
      '.mgp_controls',
      '.mgp_bottomControls',
      '.mgp_topControls',
      '.mgp_contextMenu',
      '.mgp_adOverlay',
      '.interstitial-wrapper',
      '#abTestingFloatingBanner',
      '.mgp_playbackRate',
      '.mgp_cast',
      '.mgp_adProgress'
    ];
  }

  public shouldSaveDirectUrl(): boolean {
    // Save direct MP4/HLS if resolved, otherwise page URL
    return true;
  }
}
