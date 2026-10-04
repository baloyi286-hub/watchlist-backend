CREATE TABLE tv_jobs (
  id UUID PRIMARY KEY,
  device_id VARCHAR(255) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL,
  delivered_at TIMESTAMPTZ
);

CREATE INDEX idx_tv_jobs_device_status_created
  ON tv_jobs(device_id, status, created_at);
