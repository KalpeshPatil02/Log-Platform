-- V2
-- 1) Tenant ids are UUID strings (identity-service), not BIGINT.
-- 2) A fingerprint must be resolvable many times. The old unique key (tenant_id, fingerprint, status)
--    allowed only ONE RESOLVED incident per fingerprint, so the 2nd resolve failed with a 500.
--    active_fingerprint = fingerprint while OPEN/ACKNOWLEDGED and NULL once RESOLVED;
--    MySQL allows many NULLs in a unique index, so only one *active* incident per fingerprint exists.

ALTER TABLE incidents DROP INDEX uk_incident_active_fingerprint;

ALTER TABLE alert_rules         MODIFY tenant_id VARCHAR(36) NOT NULL;
ALTER TABLE incidents           MODIFY tenant_id VARCHAR(36) NOT NULL;
ALTER TABLE notification_outbox MODIFY tenant_id VARCHAR(36) NOT NULL;

ALTER TABLE incidents ADD COLUMN active_fingerprint VARCHAR(128) NULL;

-- keep only the newest active incident per fingerprint so the unique key can be created
UPDATE incidents i
JOIN (SELECT MAX(id) AS mid
        FROM incidents
       WHERE status IN ('OPEN','ACKNOWLEDGED')
       GROUP BY tenant_id, fingerprint) m ON i.id = m.mid
   SET i.active_fingerprint = i.fingerprint;

ALTER TABLE incidents ADD UNIQUE KEY uk_incident_active (tenant_id, active_fingerprint);
