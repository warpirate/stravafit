// SPDX-License-Identifier: AGPL-3.0-or-later
//
// Group invite landing page.
//
//   GET /functions/v1/join?t=<token>               HTML page: group name + "Open in StravaFit"
//   GET /functions/v1/join?t=<token>&format=json   the invite preview as JSON
//
// The invite token is the permission, so this runs without JWT verification.
// It reads the preview with the publishable (anon) key through
// public.get_group_invite, which anyone holding the token may call. Joining
// itself happens in the app (join_group RPC) after the user signs in.
//
// The supabase.co domain serves HTML as text/plain, so there a page request
// is redirected to the static copy in web/join/ on GitHub Pages (JOIN_PAGE_URL
// overrides the address); the JSON endpoint stays here.

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const TOKEN_RE = /^[A-Za-z0-9_-]{24}$/;
const RELEASES_URL = "https://github.com/warpirate/stravafit/releases/latest";

function publicKey(): string {
  const legacy = Deno.env.get("SUPABASE_ANON_KEY");
  if (legacy) return legacy;
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    return keys["default"] ?? Object.values(keys)[0] ?? "";
  } catch {
    return "";
  }
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

async function readInvite(token: string): Promise<Record<string, unknown> | null> {
  const key = publicKey();
  const headers: Record<string, string> = { "apikey": key, "Content-Type": "application/json" };
  if (key.split(".").length === 3) headers["Authorization"] = `Bearer ${key}`;
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_group_invite`, {
    method: "POST",
    headers,
    body: JSON.stringify({ p_token: token }),
  });
  if (!res.ok) throw new Error(`invite lookup failed: ${res.status}`);
  const rows = await res.json();
  const g = Array.isArray(rows) ? rows[0] : null;
  if (!g) return null;
  return {
    name: g.name,
    sport: g.sport,
    description: g.description,
    member_count: g.member_count,
    avatar_url: g.avatar_path ? `${SUPABASE_URL}/storage/v1/object/public/avatars/${g.avatar_path}` : null,
  };
}

function page(token: string, nonce: string): string {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex, nofollow">
<meta name="theme-color" content="#FC5200">
<title>Join a group · StravaFit</title>
<style nonce="${nonce}">
  :root { --orange: #FC5200; --ink: #242428; --muted: #6D6D78; --bg: #F7F7FA; --card: #FFFFFF; --line: #E6E6EB; }
  @media (prefers-color-scheme: dark) {
    :root { --ink: #F2F2F5; --muted: #A3A3AD; --bg: #121214; --card: #1C1C1F; --line: #2C2C31; }
  }
  * { box-sizing: border-box; }
  html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--ink);
               font: 16px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  .bar { background: var(--orange); color: #fff; padding: max(14px, env(safe-area-inset-top)) 20px 14px;
         font-weight: 700; display: flex; align-items: center; gap: 10px; }
  .logo { width: 28px; height: 28px; border-radius: 8px; background: rgba(255,255,255,.18);
          display: grid; place-items: center; font-weight: 800; }
  main { max-width: 480px; margin: 24px auto; padding: 0 16px; }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 24px 20px; text-align: center; }
  .avatar { width: 84px; height: 84px; border-radius: 20px; margin: 0 auto 14px; background: var(--orange) center/cover no-repeat;
            color: #fff; font-size: 34px; font-weight: 800; display: grid; place-items: center; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  .sub { color: var(--muted); font-size: 14px; }
  .desc { margin: 14px 0 0; white-space: pre-line; }
  .btn { display: block; margin-top: 22px; padding: 14px 18px; border-radius: 999px; background: var(--orange);
         color: #fff; font-weight: 700; text-decoration: none; font-size: 16px; }
  .btn:focus-visible { outline: 3px solid var(--ink); outline-offset: 2px; }
  .alt { display: block; margin-top: 14px; color: var(--muted); font-size: 14px; }
  .alt a { color: var(--orange); font-weight: 600; }
</style>
</head>
<body>
<header class="bar"><div class="logo" aria-hidden="true">S</div>StravaFit</header>
<main>
  <section class="card" aria-live="polite">
    <div class="avatar" id="avatar" aria-hidden="true">·</div>
    <h1 id="name">Loading invite…</h1>
    <div class="sub" id="sub"></div>
    <p class="desc" id="desc" hidden></p>
    <a class="btn" id="open" href="#" hidden>Open in StravaFit</a>
    <span class="alt" id="alt" hidden>Don't have the app yet? <a href="${RELEASES_URL}" rel="noopener">Get StravaFit</a> (free, open source), then open this link again.</span>
  </section>
</main>
<script nonce="${nonce}">
(() => {
  const TOKEN = ${JSON.stringify(token)};
  const ENDPOINT = location.pathname + "?t=" + encodeURIComponent(TOKEN) + "&format=json";
  const RELEASES = ${JSON.stringify(RELEASES_URL)};
  const $ = (id) => document.getElementById(id);
  const deep = "openactivity://group/join?t=" + encodeURIComponent(TOKEN);
  const intent = "intent://group/join?t=" + encodeURIComponent(TOKEN) +
    "#Intent;scheme=openactivity;S.browser_fallback_url=" + encodeURIComponent(RELEASES) + ";end";
  const SPORT = { Run: "Running", TrailRun: "Trail running", Walk: "Walking", Hike: "Hiking", Ride: "Cycling",
    MountainBikeRide: "Mountain biking", GravelRide: "Gravel riding", EBikeRide: "E-bike riding",
    Swim: "Swimming", Rowing: "Rowing" };
  fetch(ENDPOINT, { cache: "no-store" }).then(async (res) => {
    if (res.status === 404) {
      $("name").textContent = "This invite link isn't valid";
      $("sub").textContent = "It may have been turned off or replaced. Ask the group for a new link.";
      $("avatar").textContent = "!";
      return;
    }
    if (!res.ok) throw new Error(String(res.status));
    const g = await res.json();
    document.title = "Join " + g.name + " · StravaFit";
    $("name").textContent = g.name;
    const n = g.member_count || 0;
    $("sub").textContent = [SPORT[g.sport] || g.sport, n + (n === 1 ? " member" : " members")].filter(Boolean).join(" · ");
    if (g.description) { $("desc").textContent = g.description; $("desc").hidden = false; }
    if (g.avatar_url) { $("avatar").style.backgroundImage = "url(" + JSON.stringify(g.avatar_url) + ")"; $("avatar").textContent = ""; }
    else $("avatar").textContent = (g.name[0] || "?").toUpperCase();
    const open = $("open");
    open.href = /Android/i.test(navigator.userAgent) ? intent : deep;
    open.hidden = false;
    $("alt").hidden = false;
  }).catch(() => {
    $("name").textContent = "Can't load the invite";
    $("sub").textContent = "Check your connection and reload the page.";
  });
})();
</script>
</body>
</html>`;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "GET") return json(405, { error: "method not allowed" });

  const url = new URL(req.url);
  const token = url.searchParams.get("t") ?? "";
  if (!TOKEN_RE.test(token)) return json(404, { error: "not found" });

  if (url.searchParams.get("format") === "json") {
    try {
      const invite = await readInvite(token);
      return invite ? json(200, invite) : json(404, { error: "not found" });
    } catch (e) {
      console.error(e);
      return json(502, { error: "temporarily unavailable" });
    }
  }

  // Hosted supabase.co cannot serve HTML: default to the GitHub Pages copy there.
  // Set JOIN_PAGE_URL=self (custom domain, self-hosting) to serve the page from here.
  const configured = Deno.env.get("JOIN_PAGE_URL");
  const pageUrl = configured === "self" ? null
    : configured ?? (new URL(SUPABASE_URL).hostname.endsWith(".supabase.co")
      ? "https://warpirate.github.io/stravafit/join/" : null);
  if (pageUrl) {
    return new Response(null, {
      status: 302,
      headers: { "Location": `${pageUrl}?t=${encodeURIComponent(token)}`, "Cache-Control": "no-store" },
    });
  }

  const nonce = crypto.randomUUID().replaceAll("-", "");
  return new Response(page(token, nonce), {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": [
        "default-src 'none'",
        `script-src 'nonce-${nonce}'`,
        `style-src 'nonce-${nonce}'`,
        `img-src ${SUPABASE_URL} data:`,
        "connect-src 'self'",
        "base-uri 'none'",
        "form-action 'none'",
        "frame-ancestors 'none'",
      ].join("; "),
      "X-Robots-Tag": "noindex, nofollow",
      "X-Content-Type-Options": "nosniff",
    },
  });
});
