CREATE TABLE watch_items (
 id UUID PRIMARY KEY,title VARCHAR(240) NOT NULL,item_type VARCHAR(40) NOT NULL,source_url TEXT,why_saved VARCHAR(500),
 youtube_video_id VARCHAR(32),youtube_title VARCHAR(300),youtube_thumbnail TEXT,priority VARCHAR(30) NOT NULL,status VARCHAR(30) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,watched_at TIMESTAMPTZ,snoozed_until TIMESTAMPTZ
);
CREATE TABLE tv_settings (
 id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL,notify_on_tv_start BOOLEAN NOT NULL,periodic_enabled BOOLEAN NOT NULL,
 period_minutes INTEGER NOT NULL,max_items INTEGER NOT NULL,only_unwatched BOOLEAN NOT NULL,device_id VARCHAR(120) NOT NULL,last_periodic_sent_at TIMESTAMPTZ
);
