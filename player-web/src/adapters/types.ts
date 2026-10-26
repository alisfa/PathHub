import { VideoResolutionResult } from '../types';

export interface SiteAdapter {
  /** Domain pattern or exact hostname this adapter handles (e.g. "youtube.com", "vimeo.com", "*") */
  name: string;
  domains: string[];

  /**
   * Resolves the real stream or video URL from the video element or surrounding DOM.
   * Can inspect <source>, data-* attributes, or page scripts.
   */
  resolveVideoUrl(video: HTMLVideoElement): VideoResolutionResult;

  /**
   * Extracts clean page/video title.
   */
  extractTitle(video: HTMLVideoElement): string;

  /**
   * Extracts video thumbnail (from poster, og:image, microdata, etc.).
   */
  extractThumbnail(video: HTMLVideoElement): string;

  /**
   * CSS selectors for ads, banners, or native floating overlays to hide.
   */
  getSelectorsToHide(): string[];

  /**
   * Whether to save the direct video stream URL (MP4/HLS) or the host page URL.
   */
  shouldSaveDirectUrl(): boolean;
}
