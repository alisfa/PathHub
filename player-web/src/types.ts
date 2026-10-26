export interface VideoMetadata {
  videoUrl: string;
  pageUrl: string;
  title: string;
  thumbnailUrl: string;
  siteId?: string;
  timestamp: number;
}

export interface VideoResolutionResult {
  videoUrl: string;
  isDirect: boolean;
  isBlob: boolean;
  isDRM: boolean;
  format?: string;
}

export interface DebugInfo {
  videoCount: number;
  activeAdapter: string;
  resolvedUrl: string;
  isBlob: boolean;
  isDRM: boolean;
  readyState: number;
  videoWidth: number;
  videoHeight: number;
  currentTime: number;
  duration: number;
}
