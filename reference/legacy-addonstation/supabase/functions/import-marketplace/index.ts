// Supabase Edge Function: import official Minecraft / PlayFab preview metadata.
// Deploy with: supabase functions deploy import-marketplace

const allowedHosts = new Set([
  'www.minecraft.net',
  'minecraft.net',
  'content1.prod.catalog.playfab.com',
]);

const readMeta = (html: string, name: string) => {
  const expression = new RegExp(`<meta[^>]+(?:property|name)=["']${name}["'][^>]+content=["']([^"']+)["']`, 'i');
  return html.match(expression)?.[1] || '';
};

Deno.serve(async (request) => {
  if (request.method !== 'POST') return new Response('Method not allowed', { status: 405 });
  try {
    const { url } = await request.json();
    const target = new URL(url);
    if (!allowedHosts.has(target.hostname) || target.protocol !== 'https:') {
      return Response.json({ error: 'Only official Minecraft and PlayFab URLs are allowed.' }, { status: 400 });
    }

    // Direct official PlayFab preview image.
    if (target.hostname === 'content1.prod.catalog.playfab.com') {
      return Response.json({ title: '', description: '', cover_url: target.href, video_url: '', source_url: target.href });
    }

    const response = await fetch(target.href, { headers: { 'User-Agent': 'AddonStation Metadata Importer/1.0' } });
    if (!response.ok) return Response.json({ error: 'Could not fetch the official page.' }, { status: 400 });
    const html = await response.text();
    const title = readMeta(html, 'og:title');
    const description = readMeta(html, 'og:description');
    const cover_url = readMeta(html, 'og:image');
    const youtube = html.match(/https?:\/\/(?:www\.)?(?:youtube\.com\/embed\/|youtu\.be\/)[^"'\s<]+/i)?.[0] || '';
    return Response.json({ title, description, cover_url, video_url: youtube, source_url: target.href });
  } catch {
    return Response.json({ error: 'Invalid URL.' }, { status: 400 });
  }
});
