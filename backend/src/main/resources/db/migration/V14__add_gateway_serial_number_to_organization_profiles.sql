-- V14: 机场网关序列号
ALTER TABLE organization_device_profiles ADD COLUMN IF NOT EXISTS gateway_serial_number VARCHAR(100);
