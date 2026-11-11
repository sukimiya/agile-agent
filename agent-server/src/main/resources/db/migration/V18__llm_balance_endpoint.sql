ALTER TABLE llm_provider_preset ADD balance_base_url VARCHAR(500) NULL;
ALTER TABLE llm_provider_preset ADD balance_amount_pointer VARCHAR(255) NULL;
ALTER TABLE llm_provider_preset ADD balance_currency_pointer VARCHAR(255) NULL;
ALTER TABLE llm_provider_preset ADD balance_currency VARCHAR(32) NULL;
ALTER TABLE llm_provider_preset ADD balance_divisor INT NULL;
ALTER TABLE llm_provider_preset ADD balance_negate TINYINT NULL;

ALTER TABLE tenant_llm_config ADD balance_api_key_encrypted TEXT NULL;
ALTER TABLE tenant_llm_config ADD balance_team_id_encrypted TEXT NULL;
