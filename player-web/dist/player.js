"use strict";
var PathHubPlayerLayer = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // player-web/src/index.ts
  var index_exports = {};
  __export(index_exports, {
    default: () => index_default
  });

  // player-web/src/adapters/generic.ts
  var GenericAdapter = class {
    constructor() {
      this.name = "Generic Web Video Adapter";
      this.domains = ["*"];
    }
    resolveVideoUrl(video) {
      let resolvedUrl = video.currentSrc || video.src || "";
      if (!resolvedUrl || resolvedUrl.startsWith("blob:")) {
        const sources = video.querySelectorAll("source");
        for (let i = 0; i < sources.length; i++) {
          const src = sources[i].getAttribute("src") || sources[i].getAttribute("data-src");
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
      if (!resolvedUrl) {
        const dataSrc = video.getAttribute("data-src") || video.getAttribute("data-url") || video.getAttribute("data-video-src");
        if (dataSrc) {
          try {
            resolvedUrl = new URL(dataSrc, window.location.href).href;
          } catch (_) {
            resolvedUrl = dataSrc;
          }
        }
      }
      const isBlob = resolvedUrl.startsWith("blob:");
      const isDRM = !!(video.mediaKeys || video.webkitKeys);
      const isDirect = !!(resolvedUrl && !isBlob && (resolvedUrl.includes(".mp4") || resolvedUrl.includes(".m3u8") || resolvedUrl.includes(".webm") || resolvedUrl.includes(".mov") || resolvedUrl.includes(".mpd")));
      return {
        videoUrl: resolvedUrl || window.location.href,
        isDirect,
        isBlob,
        isDRM,
        format: resolvedUrl.split("?")[0].split(".").pop()
      };
    }
    extractTitle(video) {
      const videoTitle = video.getAttribute("title") || video.getAttribute("aria-label");
      if (videoTitle && videoTitle.trim().length > 0) {
        return videoTitle.trim();
      }
      const ogTitle = document.querySelector('meta[property="og:title"]')?.getAttribute("content");
      if (ogTitle && ogTitle.trim().length > 0) {
        return ogTitle.trim();
      }
      const twitterTitle = document.querySelector('meta[name="twitter:title"]')?.getAttribute("content");
      if (twitterTitle && twitterTitle.trim().length > 0) {
        return twitterTitle.trim();
      }
      if (document.title && document.title.trim().length > 0) {
        return document.title.replace(/\s*[-|]\s*.*$/, "").trim();
      }
      return "Video on " + window.location.hostname;
    }
    extractThumbnail(video) {
      const isImage = (url) => {
        if (!url || typeof url !== "string") return false;
        const u = url.toLowerCase().trim();
        if (u.startsWith("data:image/")) return true;
        if (u === window.location.href || u.includes(".html") || u.includes(".php")) return false;
        return true;
      };
      if (isImage(video.poster) && video.poster.trim().length > 0) {
        try {
          return new URL(video.poster, window.location.href).href;
        } catch (_) {
          return video.poster;
        }
      }
      const ogImage = document.querySelector('meta[property="og:image"], meta[property="og:image:secure_url"]')?.getAttribute("content");
      if (isImage(ogImage) && ogImage.trim().length > 0) {
        try {
          return new URL(ogImage, window.location.href).href;
        } catch (_) {
          return ogImage;
        }
      }
      const twitterImage = document.querySelector('meta[name="twitter:image"], meta[name="twitter:image:src"]')?.getAttribute("content");
      if (isImage(twitterImage) && twitterImage.trim().length > 0) {
        try {
          return new URL(twitterImage, window.location.href).href;
        } catch (_) {
          return twitterImage;
        }
      }
      try {
        if (video && video.videoWidth > 0 && video.readyState >= 2) {
          const canvas = document.createElement("canvas");
          canvas.width = 320;
          canvas.height = 180;
          const ctx = canvas.getContext("2d");
          if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const dataUrl = canvas.toDataURL("image/jpeg", 0.65);
            if (dataUrl && dataUrl.length > 500 && !dataUrl.includes("data:,")) {
              return dataUrl;
            }
          }
        }
      } catch (_) {
      }
      return `https://www.google.com/s2/favicons?domain=${window.location.hostname}&sz=256`;
    }
    getSelectorsToHide() {
      return [
        ".video-ads",
        ".ytp-chrome-top",
        ".ytp-chrome-bottom",
        ".vjs-control-bar",
        ".jw-controls"
      ];
    }
    shouldSaveDirectUrl() {
      return true;
    }
  };

  // player-web/src/adapters/sample.ts
  var SampleMediaAdapter = class {
    constructor() {
      this.name = "Sample Media Portal Adapter";
      this.domains = ["archive.org", "w3schools.com", "commondatastorage.googleapis.com"];
    }
    resolveVideoUrl(video) {
      let directUrl = video.currentSrc || video.src || "";
      if (!directUrl || directUrl.startsWith("blob:")) {
        const hdSource = video.querySelector('source[type="video/mp4"]');
        if (hdSource) {
          directUrl = hdSource.getAttribute("src") || "";
        }
      }
      const isBlob = directUrl.startsWith("blob:");
      const isDRM = !!video.mediaKeys;
      return {
        videoUrl: directUrl || window.location.href,
        isDirect: directUrl.endsWith(".mp4") || directUrl.endsWith(".m3u8"),
        isBlob,
        isDRM,
        format: "mp4"
      };
    }
    extractTitle(video) {
      const heading = document.querySelector("h1, .film-title, .video-title");
      if (heading && heading.textContent) {
        return heading.textContent.trim();
      }
      return document.title || "Archive Video";
    }
    extractThumbnail(video) {
      if (video.poster) return video.poster;
      const img = document.querySelector(".film-poster, .video-thumb img");
      if (img && img.src) return img.src;
      return `https://www.google.com/s2/favicons?domain=${window.location.hostname}&sz=128`;
    }
    getSelectorsToHide() {
      return [
        ".jw-controls",
        ".vjs-control-bar",
        "#ia-player-controls",
        ".native-player-overlay"
      ];
    }
    shouldSaveDirectUrl() {
      return true;
    }
  };

  // player-web/src/adapters/pornhub.ts
  var PornhubAdapter = class {
    constructor() {
      this.name = "Pornhub Media Adapter";
      this.domains = ["pornhub.com", "rt.pornhub.com", "pornhubpremium.com"];
    }
    resolveVideoUrl(video) {
      try {
        const overlays = document.querySelectorAll('.mgp_clickCapture, .interstitial-wrapper, #abTestingFloatingBanner, .ad-overlay, [class*="ad_wrapper"]');
        overlays.forEach((el) => el.remove());
      } catch (_) {
      }
      let resolvedUrl = "";
      let isDirect = false;
      try {
        const win = window;
        const flashvarKeys = Object.keys(win).filter((k) => k.startsWith("flashvars_"));
        for (const k of flashvarKeys) {
          const fv = win[k];
          if (fv && fv.mediaDefinitions && Array.isArray(fv.mediaDefinitions)) {
            const sorted = [...fv.mediaDefinitions].sort((a, b) => {
              const qA = parseInt(a.quality || "0", 10);
              const qB = parseInt(b.quality || "0", 10);
              return qB - qA;
            });
            for (const m of sorted) {
              if (m.videoUrl && typeof m.videoUrl === "string" && m.videoUrl.length > 0) {
                resolvedUrl = m.videoUrl;
                isDirect = resolvedUrl.includes(".mp4") || resolvedUrl.includes(".m3u8");
                break;
              }
            }
          }
          if (resolvedUrl) break;
        }
        if (!resolvedUrl && win.playerObjList) {
          const playerKeys = Object.keys(win.playerObjList);
          for (const pk of playerKeys) {
            const playerObj = win.playerObjList[pk];
            if (playerObj?.mediaDefinitions) {
              for (const m of playerObj.mediaDefinitions) {
                if (m.videoUrl && typeof m.videoUrl === "string") {
                  resolvedUrl = m.videoUrl;
                  isDirect = resolvedUrl.includes(".mp4") || resolvedUrl.includes(".m3u8");
                  break;
                }
              }
            }
            if (resolvedUrl) break;
          }
        }
      } catch (_) {
      }
      if (!resolvedUrl) {
        const scripts = document.querySelectorAll("script");
        for (let i = 0; i < scripts.length; i++) {
          const text = scripts[i].textContent || "";
          if (text.includes("mediaDefinitions") || text.includes("flashvars_")) {
            const match = text.match(/"videoUrl"\s*:\s*"([^"]+)"/);
            if (match && match[1]) {
              resolvedUrl = match[1].replace(/\\\//g, "/");
              isDirect = resolvedUrl.includes(".mp4") || resolvedUrl.includes(".m3u8");
              break;
            }
          }
        }
      }
      if (!resolvedUrl) {
        resolvedUrl = video.currentSrc || video.src || "";
        isDirect = resolvedUrl.includes(".mp4") || resolvedUrl.includes(".m3u8");
      }
      const isBlob = resolvedUrl.startsWith("blob:");
      const isDRM = !!video.mediaKeys;
      return {
        videoUrl: resolvedUrl || window.location.href,
        isDirect,
        isBlob,
        isDRM,
        format: isDirect ? resolvedUrl.includes(".m3u8") ? "hls" : "mp4" : "stream"
      };
    }
    extractTitle(video) {
      const heading = document.querySelector("h1.title, .title-container .title, .inlineFree h1");
      if (heading && heading.textContent && heading.textContent.trim().length > 0) {
        return heading.textContent.trim();
      }
      const ogTitle = document.querySelector('meta[property="og:title"]')?.getAttribute("content");
      if (ogTitle && ogTitle.trim().length > 0) {
        return ogTitle.replace(/\s*-\s*Pornhub.*$/i, "").trim();
      }
      if (document.title) {
        return document.title.replace(/\s*-\s*Pornhub.*$/i, "").trim();
      }
      return "Pornhub Video";
    }
    extractThumbnail(video) {
      const isImage = (url) => {
        if (!url || typeof url !== "string") return false;
        const u = url.toLowerCase().trim();
        if (u.includes("logo") || u.includes(".svg") || u.includes("blank.gif") || u.includes("spacer.gif") || u.includes("pixel")) {
          return false;
        }
        if (u.includes("/view_video.php") || u.includes("/shorties/") || u === window.location.href.toLowerCase()) return false;
        if (u.startsWith("data:image/")) {
          return u.length > 1e3;
        }
        return u.includes(".jpg") || u.includes(".jpeg") || u.includes(".png") || u.includes(".webp") || u.includes("phncdn") || u.includes("pics") || u.includes("image") || u.includes("thumb") || u.includes("original") || u.includes("vts:");
      };
      try {
        const ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
        for (let i = 0; i < ldScripts.length; i++) {
          const text = ldScripts[i].textContent || "";
          if (text.includes("thumbnailUrl") || text.includes("thumbnail")) {
            const parsed = JSON.parse(text);
            const t = parsed.thumbnailUrl || parsed.thumbnail;
            if (isImage(t)) return t;
          }
        }
      } catch (_) {
      }
      const ogImage = document.querySelector('meta[property="og:image"], meta[property="og:image:url"], meta[property="og:image:secure_url"]')?.getAttribute("content");
      if (isImage(ogImage)) {
        return ogImage;
      }
      const twitterImage = document.querySelector('meta[name="twitter:image"]')?.getAttribute("content");
      if (isImage(twitterImage)) {
        return twitterImage;
      }
      const linkImage = document.querySelector('link[rel="image_src"]')?.getAttribute("content") || document.querySelector('link[rel="image_src"]')?.getAttribute("href");
      if (isImage(linkImage)) {
        return linkImage;
      }
      if (isImage(video.poster)) {
        return video.poster;
      }
      const videoAttrPoster = video.getAttribute("poster");
      if (isImage(videoAttrPoster)) {
        return videoAttrPoster;
      }
      try {
        const win = window;
        const fvKey = Object.keys(win).find((k) => k.startsWith("flashvars_"));
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
      } catch (_) {
      }
      try {
        const reelContainer = video.closest('.shorties-container, .short-item, .short-card, .reel-item, .video-element-wrapper, .swiper-slide, [class*="short"]');
        const searchRoot = reelContainer || document;
        const imgs = searchRoot.querySelectorAll('.shorties-container img, .short-item img, .short-card img, .reel-item img, [class*="short"] img, [class*="reel"] img');
        for (let i = 0; i < imgs.length; i++) {
          const el = imgs[i];
          const candidate = el.getAttribute("data-src") || el.getAttribute("data-thumb") || el.getAttribute("data-poster") || el.getAttribute("data-mediumthumb") || el.src;
          if (isImage(candidate)) {
            return candidate;
          }
        }
        const bgEls = searchRoot.querySelectorAll('[style*="background-image"]');
        for (let i = 0; i < bgEls.length; i++) {
          const style = bgEls[i].style.backgroundImage || "";
          const match = style.match(/url\(['"]?([^'"]+)['"]?\)/);
          if (match && isImage(match[1])) {
            return match[1];
          }
        }
      } catch (_) {
      }
      try {
        const playerImgs = document.querySelectorAll("#player img, .mgp_container img, .playerWrapper img, .inlineFree img");
        for (let i = 0; i < playerImgs.length; i++) {
          const el = playerImgs[i];
          const candidate = el.getAttribute("data-src") || el.getAttribute("data-thumb") || el.getAttribute("data-poster") || el.src;
          if (isImage(candidate)) {
            return candidate;
          }
        }
      } catch (_) {
      }
      try {
        if (video && video.videoWidth > 0 && video.readyState >= 2) {
          const canvas = document.createElement("canvas");
          canvas.width = 320;
          canvas.height = 180;
          const ctx = canvas.getContext("2d");
          if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const dataUrl = canvas.toDataURL("image/jpeg", 0.65);
            if (isImage(dataUrl)) {
              return dataUrl;
            }
          }
        }
      } catch (_) {
      }
      const isReelPage = window.location.href.includes("/shorties/") || window.location.href.includes("/shorts/") || window.location.href.includes("shorties");
      if (isReelPage) {
        return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)32.jpg";
      }
      return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)105.jpg";
    }
    getSelectorsToHide() {
      return [
        ".mgp_clickCapture",
        ".mgp_controlBar",
        ".mgp_bigPlay",
        ".mgp_watermark",
        ".mgp_overlay",
        ".mgp_controls",
        ".mgp_bottomControls",
        ".mgp_topControls",
        ".mgp_contextMenu",
        ".mgp_adOverlay",
        ".interstitial-wrapper",
        "#abTestingFloatingBanner",
        ".mgp_playbackRate",
        ".mgp_cast",
        ".mgp_adProgress"
      ];
    }
    shouldSaveDirectUrl() {
      return true;
    }
  };

  // player-web/src/adapters/registry.ts
  var AdapterRegistry = class {
    static getAdapterForHostname(hostname) {
      const cleanHost = hostname.toLowerCase().replace(/^www\./, "");
      for (const adapter of this.adapters) {
        if (adapter.domains.includes("*")) continue;
        for (const domain of adapter.domains) {
          if (cleanHost === domain || cleanHost.endsWith("." + domain)) {
            return adapter;
          }
        }
      }
      return this.adapters[this.adapters.length - 1];
    }
  };
  AdapterRegistry.adapters = [
    new PornhubAdapter(),
    new SampleMediaAdapter(),
    new GenericAdapter()
    // Fallback must always be last
  ];

  // player-web/src/bridge.ts
  if (typeof window !== "undefined") {
    window.__pathHubSavedCallbacks = window.__pathHubSavedCallbacks || {};
    window.__pathHubReceiveSavedStatus = (callbackId, saved) => {
      if (window.__pathHubSavedCallbacks && window.__pathHubSavedCallbacks[callbackId]) {
        window.__pathHubSavedCallbacks[callbackId](saved);
        delete window.__pathHubSavedCallbacks[callbackId];
      }
    };
  }
  var NativeBridge = class {
    static isIOS() {
      return !!(window.webkit && window.webkit.messageHandlers);
    }
    static isAndroid() {
      return !!window.AndroidBridge;
    }
    static saveVideo(metadata) {
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.saveVideo) {
          window.webkit.messageHandlers.saveVideo.postMessage(metadata);
        } else if (this.isAndroid() && window.AndroidBridge?.saveVideo) {
          window.AndroidBridge.saveVideo(JSON.stringify(metadata));
        } else {
          console.log("[NativeBridge Fallback] saveVideo:", metadata);
          const stored = JSON.parse(localStorage.getItem("path_hub_local_saved") || "[]");
          stored.push(metadata);
          localStorage.setItem("path_hub_local_saved", JSON.stringify(stored));
        }
      } catch (e) {
        console.error("[NativeBridge] saveVideo failed:", e);
      }
    }
    static async isSaved(videoUrl) {
      try {
        if (this.isAndroid() && window.AndroidBridge?.isSaved) {
          const result = window.AndroidBridge.isSaved(videoUrl);
          return typeof result === "boolean" ? result : result === "true";
        }
        if (this.isIOS() && window.webkit?.messageHandlers?.isSaved) {
          return new Promise((resolve) => {
            const callbackId = "cb_" + ++this.callbackCounter + "_" + Date.now();
            if (window.__pathHubSavedCallbacks) {
              window.__pathHubSavedCallbacks[callbackId] = resolve;
            }
            window.webkit.messageHandlers.isSaved.postMessage({
              videoUrl,
              callbackId
            });
            setTimeout(() => {
              if (window.__pathHubSavedCallbacks && window.__pathHubSavedCallbacks[callbackId]) {
                delete window.__pathHubSavedCallbacks[callbackId];
                resolve(false);
              }
            }, 1500);
          });
        }
        const stored = JSON.parse(localStorage.getItem("path_hub_local_saved") || "[]");
        return stored.some((item) => item.videoUrl === videoUrl);
      } catch (e) {
        console.error("[NativeBridge] isSaved check failed:", e);
        return false;
      }
    }
    static setBrightness(level) {
      const clamped = Math.max(0, Math.min(1, level));
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.setBrightness) {
          window.webkit.messageHandlers.setBrightness.postMessage({ brightness: clamped });
        } else if (this.isAndroid() && window.AndroidBridge?.setBrightness) {
          window.AndroidBridge.setBrightness(clamped);
        }
      } catch (e) {
        console.error("[NativeBridge] setBrightness failed:", e);
      }
    }
    static setVolume(level) {
      const clamped = Math.max(0, Math.min(1, level));
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.setVolume) {
          window.webkit.messageHandlers.setVolume.postMessage({ volume: clamped });
        } else if (this.isAndroid() && window.AndroidBridge?.setVolume) {
          window.AndroidBridge.setVolume(clamped);
        }
      } catch (e) {
        console.error("[NativeBridge] setVolume failed:", e);
      }
    }
    static enterFullscreen() {
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.enterFullscreen) {
          window.webkit.messageHandlers.enterFullscreen.postMessage({});
        } else if (this.isAndroid() && window.AndroidBridge?.enterFullscreen) {
          window.AndroidBridge.enterFullscreen();
        }
      } catch (e) {
        console.error("[NativeBridge] enterFullscreen failed:", e);
      }
    }
    static exitFullscreen() {
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.exitFullscreen) {
          window.webkit.messageHandlers.exitFullscreen.postMessage({});
        } else if (this.isAndroid() && window.AndroidBridge?.exitFullscreen) {
          window.AndroidBridge.exitFullscreen();
        }
      } catch (e) {
        console.error("[NativeBridge] exitFullscreen failed:", e);
      }
    }
    static log(msg) {
      try {
        if (this.isIOS() && window.webkit?.messageHandlers?.debugLog) {
          window.webkit.messageHandlers.debugLog.postMessage({ message: msg });
        } else if (this.isAndroid() && window.AndroidBridge?.debugLog) {
          window.AndroidBridge.debugLog(msg);
        } else {
          console.log("[NativeBridge]", msg);
        }
      } catch (_) {
      }
    }
  };
  NativeBridge.callbackCounter = 0;

  // player-web/src/index.ts
  var _PathHubDetector = class _PathHubDetector {
    constructor() {
      this.currentMetadata = null;
      this.saveBadge = null;
      this.isSaved = false;
      this.isFullscreenActive = false;
      this.scanDebounce = null;
      this.initPopupBlocker();
      this.initClickInterceptor();
      this.injectStyles();
      this.initSPAListeners();
      this.initObserver();
      this.initFullscreenListeners();
      this.scanVideos();
    }
    static init() {
      if (!this.instance) {
        this.instance = new _PathHubDetector();
      }
      return this.instance;
    }
    initPopupBlocker() {
      try {
        window.open = function(url, _target, _features) {
          console.log("[PathHub Popup Blocker] Suppressed window.open popup:", url);
          return null;
        };
      } catch (_) {
      }
    }
    initClickInterceptor() {
      document.addEventListener("click", (e) => {
        const target = e.target;
        if (!target) return;
        const overlay = target.closest('.mgp_clickCapture, .interstitial-wrapper, #abTestingFloatingBanner, .ad-overlay, .adOverlay, [class*="ad_wrapper"]');
        if (overlay) {
          e.preventDefault();
          e.stopImmediatePropagation();
          overlay.remove();
          console.log("[PathHub AdBlocker] Blocked and removed ad overlay element");
          return false;
        }
        const link = target.closest("a");
        if (link && link.href) {
          const h = link.href.toLowerCase();
          if (h.includes("/interstitial") || h.includes("trafficjunky") || h.includes("exoclick") || h.includes("tsyndicate") || h.includes("popunder") || h.includes("click.php")) {
            e.preventDefault();
            e.stopImmediatePropagation();
            console.log("[PathHub AdBlocker] Blocked ad click link:", link.href);
            return false;
          }
        }
      }, true);
    }
    checkFullscreenState() {
      const videos = Array.from(document.querySelectorAll("video"));
      const isDocFS = !!(document.fullscreenElement || document.webkitFullscreenElement || document.webkitCurrentFullScreenElement);
      const isVideoFS = videos.some((v) => v.webkitDisplayingFullscreen || v.webkitPresentationMode === "fullscreen");
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
    initFullscreenListeners() {
      const handler = () => this.checkFullscreenState();
      document.addEventListener("fullscreenchange", handler);
      document.addEventListener("webkitfullscreenchange", handler);
      window.addEventListener("resize", handler);
      window.addEventListener("orientationchange", handler);
      setInterval(() => this.checkFullscreenState(), 250);
    }
    injectStyles() {
      if (!document.getElementById("pathhub-save-styles")) {
        const style = document.createElement("style");
        style.id = "pathhub-save-styles";
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
    initSPAListeners() {
      window.addEventListener("popstate", () => this.scheduleScan());
      const origPush = history.pushState;
      history.pushState = function(...args) {
        const res = origPush.apply(this, args);
        window.dispatchEvent(new Event("pathhub:routechange"));
        return res;
      };
      const origReplace = history.replaceState;
      history.replaceState = function(...args) {
        const res = origReplace.apply(this, args);
        window.dispatchEvent(new Event("pathhub:routechange"));
        return res;
      };
      window.addEventListener("pathhub:routechange", () => this.scheduleScan());
    }
    initObserver() {
      const observer = new MutationObserver(() => this.scheduleScan());
      observer.observe(document.documentElement, { childList: true, subtree: true });
    }
    scheduleScan() {
      if (this.scanDebounce) clearTimeout(this.scanDebounce);
      this.scanDebounce = setTimeout(() => this.scanVideos(), 300);
    }
    async scanVideos() {
      const videos = document.querySelectorAll("video");
      if (videos.length === 0) {
        this.hideBadge();
        return;
      }
      videos.forEach((video) => {
        if (!video.__pathHubFullscreenBound) {
          video.__pathHubFullscreenBound = true;
          video.addEventListener("webkitbeginfullscreen", () => {
            this.isFullscreenActive = true;
            NativeBridge.enterFullscreen();
            this.hideBadge();
          });
          video.addEventListener("webkitendfullscreen", () => {
            this.isFullscreenActive = false;
            NativeBridge.exitFullscreen();
            this.showBadge();
          });
          video.addEventListener("webkitpresentationmodechanged", () => {
            this.checkFullscreenState();
          });
        }
      });
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
      this.isSaved = await NativeBridge.isSaved(saveUrl);
      this.showBadge();
      if (window.webkit?.messageHandlers?.saveVideo) {
        window.__pathHubActiveVideo = this.currentMetadata;
      }
    }
    showBadge() {
      if (!this.saveBadge) {
        this.saveBadge = document.createElement("div");
        this.saveBadge.className = "pathhub-save-pill";
        this.saveBadge.innerHTML = `
        <svg class="save-icon" viewBox="0 0 24 24"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
        <span class="save-label">Save Video</span>
        <span class="save-close" title="Dismiss">&times;</span>
      `;
        this.saveBadge.addEventListener("click", (e) => {
          const target = e.target;
          if (target && target.classList.contains("save-close")) {
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
      this.saveBadge.classList.add("visible");
    }
    hideBadge() {
      if (this.saveBadge) {
        this.saveBadge.classList.remove("visible");
      }
    }
    updateBadgeUI() {
      if (!this.saveBadge) return;
      const label = this.saveBadge.querySelector(".save-label");
      const icon = this.saveBadge.querySelector(".save-icon");
      if (this.isSaved) {
        this.saveBadge.classList.add("is-saved");
        if (label) label.textContent = "Saved \u2764\uFE0F \u2728";
        if (icon) {
          icon.innerHTML = `<path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>`;
        }
      } else {
        this.saveBadge.classList.remove("is-saved");
        if (label) label.textContent = "Save Video \u2728";
        if (icon) {
          icon.innerHTML = `<path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>`;
        }
      }
    }
    saveCurrentVideo() {
      if (!this.currentMetadata) return;
      NativeBridge.saveVideo(this.currentMetadata);
      this.isSaved = true;
      this.updateBadgeUI();
      this.showToast("Saved to Playlist \u2764\uFE0F [OTA Test Active \u2728]");
    }
    showToast(msg) {
      let toast = document.querySelector(".pathhub-toast-banner");
      if (!toast) {
        toast = document.createElement("div");
        toast.className = "pathhub-toast-banner";
        document.body.appendChild(toast);
      }
      toast.textContent = msg;
      toast.classList.add("show");
      setTimeout(() => {
        toast.classList.remove("show");
      }, 2500);
    }
  };
  _PathHubDetector.instance = null;
  var PathHubDetector = _PathHubDetector;
  window.__pathHubSaveCurrentVideo = () => {
    PathHubDetector.init().saveCurrentVideo();
  };
  if (typeof document !== "undefined") {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", () => PathHubDetector.init());
    } else {
      PathHubDetector.init();
    }
  }
  var index_default = PathHubDetector;
  return __toCommonJS(index_exports);
})();
