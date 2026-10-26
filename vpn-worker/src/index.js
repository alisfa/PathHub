export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 1. CORS Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, HEAD, POST, PUT, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "*",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    // 2. Health check
    if (url.pathname === "/health") {
      return new Response(JSON.stringify({ 
        status: "online", 
        service: "PathHub Built-in VPN Anycast Tunnel",
        colo: request.cf?.colo || "global"
      }), {
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
      });
    }

    // 3. Extract target destination
    let target = url.searchParams.get("url");

    // Check Referer header if target is missing for relative paths (e.g. /view_video.php or assets)
    if (!target) {
      const referer = request.headers.get("Referer");
      if (referer) {
        try {
          const refUrl = new URL(referer);
          const refTarget = refUrl.searchParams.get("url");
          if (refTarget) {
            const parsedTarget = new URL(refTarget);
            target = `${parsedTarget.protocol}//${parsedTarget.host}${url.pathname}${url.search}`;
          }
        } catch (_) {}
      }
    }

    // Check Cookie fallback
    if (!target) {
      const cookieHeader = request.headers.get("Cookie") || "";
      const match = cookieHeader.match(/__ph_target_host=([^;]+)/);
      if (match) {
        const host = decodeURIComponent(match[1]);
        target = `https://${host}${url.pathname}${url.search}`;
      }
    }

    if (!target) {
      return new Response("PathHub Built-in Anycast VPN Active", { status: 200 });
    }

    if (!target.startsWith("http://") && !target.startsWith("https://")) {
      target = "https://" + target;
    }

    let targetUrl;
    try {
      targetUrl = new URL(target);
    } catch (e) {
      return new Response("Invalid Target URL: " + target, { status: 400 });
    }

    // 4. Smart Origin Routing for sites with WGCZ bot filtering against datacenter IPs
    let fetchTarget = target;
    const isXvideos = targetUrl.host === "xvideos.com" || targetUrl.host === "www.xvideos.com";
    const isXnxx = targetUrl.host === "xnxx.com" || targetUrl.host === "www.xnxx.com";

    if (isXvideos) {
      fetchTarget = target.replace(/www\.xvideos\.com|xvideos\.com/, "www.xvideos-en.com");
    } else if (isXnxx) {
      fetchTarget = target.replace(/www\.xnxx\.com|xnxx\.com/, "www.xnxx.ws");
    }

    const upstreamUrl = new URL(fetchTarget);

    // 5. Build upstream headers
    const newHeaders = new Headers();
    for (const [key, val] of request.headers.entries()) {
      const lower = key.toLowerCase();
      if (
        !lower.startsWith("cf-") &&
        lower !== "host" &&
        lower !== "connection" &&
        lower !== "x-forwarded-proto" &&
        lower !== "x-real-ip" &&
        lower !== "origin"
      ) {
        newHeaders.set(key, val);
      }
    }

    newHeaders.set("Host", upstreamUrl.host);
    newHeaders.set("Referer", `${upstreamUrl.protocol}//${upstreamUrl.host}/`);
    newHeaders.set("User-Agent", request.headers.get("User-Agent") || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

    try {
      const response = await fetch(fetchTarget, {
        method: request.method,
        headers: newHeaders,
        body: ["GET", "HEAD"].includes(request.method) ? null : request.body,
        redirect: "follow",
      });

      // 6. Build response headers
      const responseHeaders = new Headers();
      for (const [key, val] of response.headers.entries()) {
        const lower = key.toLowerCase();
        // Remove framing/CSP restrictions to allow seamless rendering in WebView
        if (lower !== "content-security-policy" && lower !== "x-frame-options") {
          let headerVal = val;
          if (isXvideos) {
            headerVal = headerVal.replace(/www\.xvideos-en\.com|xvideos-en\.com/g, "www.xvideos.com");
          } else if (isXnxx) {
            headerVal = headerVal.replace(/www\.xnxx\.ws|xnxx\.ws/g, "www.xnxx.com");
          }
          responseHeaders.set(key, headerVal);
        }
      }

      responseHeaders.set("Access-Control-Allow-Origin", "*");
      responseHeaders.set("Access-Control-Allow-Credentials", "true");
      responseHeaders.set("X-PathHub-VPN", "Active");

      // Remember original target host for subsequent relative links
      responseHeaders.append("Set-Cookie", `__ph_target_host=${encodeURIComponent(targetUrl.host)}; Path=/; SameSite=Lax`);

      // If text/html or application/json, rewrite upstream domain back to user-requested target domain
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("text/html") || contentType.includes("application/javascript") || contentType.includes("json")) {
        let bodyText = await response.text();
        if (isXvideos) {
          bodyText = bodyText.replace(/www\.xvideos-en\.com/gi, "www.xvideos.com")
                             .replace(/xvideos-en\.com/gi, "xvideos.com");
        } else if (isXnxx) {
          bodyText = bodyText.replace(/www\.xnxx\.ws/gi, "www.xnxx.com")
                             .replace(/xnxx\.ws/gi, "xnxx.com");
        }
        return new Response(bodyText, {
          status: response.status,
          statusText: response.statusText,
          headers: responseHeaders,
        });
      }

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: responseHeaders,
      });
    } catch (err) {
      return new Response("PathHub VPN Tunnel Gateway Error: " + err.message, { 
        status: 502,
        headers: { "Content-Type": "text/plain" }
      });
    }
  },
};
