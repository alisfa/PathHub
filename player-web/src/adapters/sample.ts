import { SiteAdapter } from './types';
import { VideoResolutionResult } from '../types';

/**
 * Example Template Adapter for specialized media portals (e.g. archive.org, media hosts)
 * Demonstrates overriding direct stream resolution, custom selector hiding, and metadata extraction.
 */
export class SampleMediaAdapter implements SiteAdapter {
  public name = 'Sample Media Portal Adapter';
  public domains = ['archive.org', 'w3schools.com', 'commondatastorage.googleapis.com'];

  public resolveVideoUrl(video: HTMLVideoElement): VideoResolutionResult {
    let directUrl = video.currentSrc || video.src || '';

    // If source tag contains high-def or mp4 stream
    if (!directUrl || directUrl.startsWith('blob:')) {
      const hdSource = video.querySelector('source[type="video/mp4"]');
      if (hdSource) {
        directUrl = hdSource.getAttribute('src') || '';
      }
    }

    const isBlob = directUrl.startsWith('blob:');
    const isDRM = !!((video as any).mediaKeys);

    return {
      videoUrl: directUrl || window.location.href,
      isDirect: directUrl.endsWith('.mp4') || directUrl.endsWith('.m3u8'),
      isBlob,
      isDRM,
      format: 'mp4'
    };
  }

  public extractTitle(video: HTMLVideoElement): string {
    const heading = document.querySelector('h1, .film-title, .video-title');
    if (heading && heading.textContent) {
      return heading.textContent.trim();
    }
    return document.title || 'Archive Video';
  }

  public extractThumbnail(video: HTMLVideoElement): string {
    if (video.poster) return video.poster;
    const img = document.querySelector('.film-poster, .video-thumb img') as HTMLImageElement;
    if (img && img.src) return img.src;
    return `https://www.google.com/s2/favicons?domain=${window.location.hostname}&sz=128`;
  }

  public getSelectorsToHide(): string[] {
    return [
      '.jw-controls',
      '.vjs-control-bar',
      '#ia-player-controls',
      '.native-player-overlay'
    ];
  }

  public shouldSaveDirectUrl(): boolean {
    return true;
  }
}
