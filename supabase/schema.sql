-- ==============================================================================
-- Path Hub - Shared Global Database Schema (No Auth Required)
-- All users share the exact same list of sites and saved videos in realtime
-- ==============================================================================

-- 1. Enable Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Shared Playlists Table
CREATE TABLE IF NOT EXISTS public.playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL DEFAULT 'Shared Saved Videos',
    invite_code TEXT UNIQUE NOT NULL DEFAULT 'HUB001',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Ensure global shared playlist exists
INSERT INTO public.playlists (id, name, invite_code)
VALUES ('00000000-0000-0000-0000-000000000001', 'Shared Saved Videos', 'HUB001')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- 3. Shared Sites Table (Websites Bookmarks)
CREATE TABLE IF NOT EXISTS public.sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    url TEXT NOT NULL UNIQUE,
    icon_url TEXT DEFAULT '',
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sites_sort_order ON public.sites(sort_order ASC);

-- 4. Shared Playlist Items (Saved Videos across all users)
CREATE TABLE IF NOT EXISTS public.playlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES public.playlists(id) ON DELETE CASCADE,
    site_id UUID REFERENCES public.sites(id) ON DELETE SET NULL,
    video_url TEXT NOT NULL,
    page_url TEXT NOT NULL,
    title TEXT NOT NULL,
    thumbnail_url TEXT DEFAULT '',
    notes TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT playlist_items_video_unique UNIQUE (playlist_id, video_url)
);

-- Migration for existing instances:
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS notes TEXT DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_playlist_items_created ON public.playlist_items(created_at DESC);

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES & GRANTS
-- Public read & write for all users without authentication
-- ==============================================================================

GRANT ALL ON public.sites TO anon, authenticated;
GRANT ALL ON public.playlists TO anon, authenticated;
GRANT ALL ON public.playlist_items TO anon, authenticated;

ALTER TABLE public.sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;

-- Sites: Public full access
DROP POLICY IF EXISTS "Public full access to sites" ON public.sites;
CREATE POLICY "Public full access to sites"
    ON public.sites FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- Playlists: Public full access
DROP POLICY IF EXISTS "Public full access to playlists" ON public.playlists;
CREATE POLICY "Public full access to playlists"
    ON public.playlists FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- Playlist Items: Public full access (All users see and save to the same list)
DROP POLICY IF EXISTS "Public full access to playlist_items" ON public.playlist_items;
CREATE POLICY "Public full access to playlist_items"
    ON public.playlist_items FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- ==============================================================================
-- SEED INITIAL SITES
-- ==============================================================================

INSERT INTO public.sites (id, name, url, icon_url, sort_order)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Pornhub', 'https://www.pornhub.com', 'https://www.google.com/s2/favicons?domain=pornhub.com&sz=128', 0),
    ('10000000-0000-0000-0000-000000000002', 'Big Buck Bunny (MP4 Demo)', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'https://www.google.com/s2/favicons?domain=blender.org&sz=128', 1),
    ('10000000-0000-0000-0000-000000000003', 'Internet Archive Cinema', 'https://archive.org/details/moviesandfilms', 'https://www.google.com/s2/favicons?domain=archive.org&sz=128', 2)
ON CONFLICT (url) DO NOTHING;

-- ==============================================================================
-- REALTIME REPLICATION CONFIGURATION
-- ==============================================================================

ALTER TABLE public.sites REPLICA IDENTITY FULL;
ALTER TABLE public.playlist_items REPLICA IDENTITY FULL;
ALTER TABLE public.playlists REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'sites'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.sites;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'playlist_items'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.playlist_items;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'playlists'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.playlists;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'history'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.history;
    END IF;
END $$;

-- ==============================================================================
-- 5. BALABOO BROWSING HISTORY (PIN 1991)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url TEXT NOT NULL,
    title TEXT DEFAULT '',
    device_name TEXT NOT NULL DEFAULT 'Unknown Device',
    visited_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_history_visited_at ON public.history(visited_at DESC);

ALTER TABLE public.history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public full access to history" ON public.history;
CREATE POLICY "Public full access to history"
    ON public.history FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

GRANT ALL ON public.history TO anon, authenticated;
ALTER TABLE public.history REPLICA IDENTITY FULL;
