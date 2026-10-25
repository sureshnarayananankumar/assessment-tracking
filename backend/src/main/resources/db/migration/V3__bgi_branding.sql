-- =====================================================================
-- V3: Business Gateways International branding.
-- Sets the organisation name used in the UI and e-mails, but only if it
-- still has the original default (an administrator's custom value is kept).
-- =====================================================================
UPDATE app_settings
SET setting_value = 'Business Gateways International',
    updated_at    = UTC_TIMESTAMP(6)
WHERE setting_key = 'org.name'
  AND setting_value = 'AssessHub';
