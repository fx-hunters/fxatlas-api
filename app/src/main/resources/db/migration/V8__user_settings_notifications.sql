-- Add notification settings columns to user_settings table (Issue #21)
ALTER TABLE user_settings
ADD COLUMN exchange_schedule_reminder boolean NOT NULL DEFAULT true,
ADD COLUMN review_required_alert boolean NOT NULL DEFAULT true,
ADD COLUMN deadline_approach_alert boolean NOT NULL DEFAULT true,
ADD COLUMN bucket_entry_alert boolean NOT NULL DEFAULT true;
