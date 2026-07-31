-- V13: 铭牌序列号
ALTER TABLE organization_device_profiles ADD COLUMN IF NOT EXISTS nameplate_serial_number VARCHAR(100);
