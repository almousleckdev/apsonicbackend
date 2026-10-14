INSERT INTO system_configs (config_key, config_value) 
VALUES ('homepage_config', '{}') 
ON CONFLICT (config_key) DO NOTHING;
