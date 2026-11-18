#!/usr/bin/env bash
# phase6-alert-tests.sh  -  all Phase 6 (alert-service) tests in one file.
#
#   bash scripts/phase6-alert-tests.sh fast       # ~3 min : A, B, C (no window-expiry), D, E1-E3, F1-F4, cooldown
#   bash scripts/phase6-alert-tests.sh slow       # ~9 min : window expiry, Redis/MySQL outage, crash recovery
#   bash scripts/phase6-alert-tests.sh gateway    # ~1 min : login, roles through the gateway (needs identity + gateway)
#   bash scripts/phase6-alert-tests.sh pipeline   # ~1 min : ingestion -> processing -> alert (needs all 5 services)
#   bash scripts/phase6-alert-tests.sh all
#
# Needs: docker compose (mysql, redis, alert-service up), jq, curl.  Put this file in log-platform/scripts/.
cd "$(dirname "$0")/.." || exit 1
set -a; source .env; set +a

A=http://localhost:8086
GW=http://localhost:8080
PEM=${PLATFORM_EMAIL:-admin@platform.local}
PW=${PLATFORM_PW:-$(grep -E '^BOOTSTRAP_ADMIN_PASSWORD=' .env 2>/dev/null | cut -d= -f2- | tr -d "\"'")}
PW=${PW:-Root123}
APW='S3cretPassw0rd'
H='Content-Type: application/json'
UT=4f1c9d7e-1111-2222-3333-444455556666
PASS=0; FAIL=0

dc(){ docker compose --profile alerting "$@"; }
rcli(){ docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning "$@" </dev/null; }
sql(){ docker compose exec -T mysql mysql -N -B -ualert_svc -p"$ALERT_DB_PASSWORD" alert_db -e "$1" 2>/dev/null </dev/null; }
logsql(){ docker compose exec -T mysql sh -c 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" logs_db 2>/dev/null' <<< "$1"; }
api(){ curl -s -o /tmp/resp -w '%{http_code}' -X "$1" "$A$2" -H 'Content-Type: application/json' ${3:+-H "X-Tenant-Id: $3"} -H 'X-User-Id: kalpesh' -H "X-User-Roles: ${ROLES:-TENANT_ADMIN}" ${4:+-d "$4"}; }
code(){ curl -s -o /tmp/resp -w '%{http_code}' "$@"; }
ok(){ if [ "$2" = "$3" ]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1   (expected '$3', got '$2')"; FAIL=$((FAIL+1)); fi; }
jid(){ grep -o '"id":[0-9]*' /tmp/resp | head -1 | cut -d: -f2; }
mkrule(){ api POST /api/v1/alert-rules "$1" "$2" >/dev/null; jid; }
inc_count(){ sql "SELECT COUNT(*) FROM incidents WHERE tenant_id='$1'"; }
clean_state(){
  sql "DELETE FROM notification_outbox; DELETE FROM incidents; DELETE FROM alert_rules;"
  for k in $(rcli --scan --pattern 'alert:window:*'); do rcli DEL "$k" >/dev/null; done
  rcli DEL notifications.out logs.dlq >/dev/null
}
# emit TENANT SEVERITY SERVICE ENV EVENTID   (tenant is raw JSON: 1 for a number, "\"uuid\"" for a string)
emit(){ rcli XADD logs.processed '*' eventKey "$5" payload "{\"eventId\":\"$5\",\"tenantId\":$1,\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",\"service\":\"$3\",\"environment\":\"$4\",\"severity\":\"$2\",\"message\":\"test\"}" >/dev/null; }
waitdrain(){ local lag; for i in $(seq 1 40); do lag=$(rcli XINFO GROUPS logs.processed | awk '/^lag$/{getline; print; exit}'); [ "$lag" = "0" ] && break; sleep 1; done; sleep 3; }
tok(){ curl -s $GW/api/v1/auth/login -H "$H" -d "{\"tenantSlug\":\"$1\",\"email\":\"$2\",\"password\":\"$3\"}" | jq -r '.accessToken // empty'; }

RULE='{"name":"pay errors","severity":"ERROR","serviceName":"payment-api","environment":"production","thresholdCount":3,"windowSeconds":60,"cooldownSeconds":0,"enabled":true}'
R1='{"name":"threshold-1","severity":"ERROR","thresholdCount":1,"windowSeconds":60,"cooldownSeconds":0,"enabled":true}'
RANYSVC='{"name":"any svc","severity":"ERROR","environment":"production","thresholdCount":3,"windowSeconds":60,"cooldownSeconds":0,"enabled":true}'
RCOOL='{"name":"cooldown 30s","severity":"ERROR","thresholdCount":1,"windowSeconds":60,"cooldownSeconds":30,"enabled":true}'

section(){ echo; echo "=============== $1 ==============="; }

# ---------------------------------------------------------------- A
test_A(){ section "A. smoke"
  ok "A1 alert-service health" "$(curl -s -m 5 $A/actuator/health | grep -o '"status":"UP"' | head -1)" '"status":"UP"'
  ok "A2 migration V2 applied" "$(sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1")" 1
  ok "A3 consumer group exists" "$(rcli XINFO GROUPS logs.processed | grep -c alert-service)" 1
}
# ---------------------------------------------------------------- B
test_B(){ section "B. alert rule CRUD"
  clean_state
  ok "B1 create rule" "$(api POST /api/v1/alert-rules 1 "$RULE")" 201; RID=$(jid)
  ok "B2 list rules" "$(api GET /api/v1/alert-rules 1)" 200
  grep -q "pay errors" /tmp/resp && echo "PASS  B2 list contains the rule" && PASS=$((PASS+1)) || { echo "FAIL  B2 list contains the rule"; FAIL=$((FAIL+1)); }
  for bad in '"thresholdCount":0' '"windowSeconds":0' '"windowSeconds":86401' '"severity":"BOGUS"' '"name":""' '"cooldownSeconds":-1'; do
    key=${bad%%:*}; body=$(echo "$RULE" | sed -E "s/${key}:[^,}]*/${bad}/")
    ok "B3 invalid $bad -> 400" "$(api POST /api/v1/alert-rules 1 "$body")" 400
  done
  ok "B4 lowercase severity accepted" "$(api POST /api/v1/alert-rules 1 "$(echo "$RULE" | sed 's/"ERROR"/"error"/')")" 201
  grep -q '"severity":"ERROR"' /tmp/resp && echo "PASS  B4 stored upper-case" && PASS=$((PASS+1)) || { echo "FAIL  B4 stored upper-case"; FAIL=$((FAIL+1)); }
  ok "B5 rule without filters" "$(api POST /api/v1/alert-rules 1 '{"name":"all","thresholdCount":5,"windowSeconds":60,"cooldownSeconds":0,"enabled":true}')" 201
  ok "B6 update rule" "$(api PUT /api/v1/alert-rules/$RID 1 "$(echo "$RULE" | sed 's/"thresholdCount":3/"thresholdCount":5/')")" 200
  ok "B6 threshold changed" "$(sql "SELECT threshold_count FROM alert_rules WHERE id=$RID")" 5
  ok "B9 other tenant sees no rules" "$(api GET /api/v1/alert-rules 2 >/dev/null; cat /tmp/resp)" '[]'
  ok "B9 other tenant PUT -> 404" "$(api PUT /api/v1/alert-rules/$RID 2 "$RULE")" 404
  ok "B9 other tenant DELETE -> 404" "$(api DELETE /api/v1/alert-rules/$RID 2)" 404
  ok "B7 delete (soft)" "$(api DELETE /api/v1/alert-rules/$RID 1)" 204
  ok "B7 row disabled, not removed" "$(sql "SELECT enabled FROM alert_rules WHERE id=$RID")" 0
  ok "B8 PUT unknown id" "$(api PUT /api/v1/alert-rules/999999 1 "$RULE")" 404
  ok "B8 DELETE unknown id" "$(api DELETE /api/v1/alert-rules/999999 1)" 404
  ok "B10 no tenant header -> 400" "$(api GET /api/v1/alert-rules "")" 400
  ok "B10 bad tenant header -> 400" "$(api GET /api/v1/alert-rules 'a b')" 400
  ok "B11 UUID tenant works" "$(api POST /api/v1/alert-rules $UT "$R1")" 201
  ok "B12 no roles header -> 403" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Tenant-Id: 1' $A/api/v1/alert-rules)" 403
  ok "B12 VIEWER cannot create -> 403" "$(ROLES=VIEWER api POST /api/v1/alert-rules 1 "$RULE")" 403
  ok "B12 VIEWER can list -> 200" "$(ROLES=VIEWER api GET /api/v1/alert-rules 1)" 200
}
# ---------------------------------------------------------------- C
test_C(){ section "C. evaluation and incident creation"
  clean_state; mkrule 1 "$RULE" >/dev/null
  emit 1 ERROR payment-api production c1-1; emit 1 ERROR payment-api production c1-2; waitdrain
  ok "C1 2 events -> no incident" "$(inc_count 1)" 0
  emit 1 ERROR payment-api production c1-3; waitdrain
  ok "C2 3rd event -> 1 incident" "$(inc_count 1)" 1
  ok "C2 status OPEN" "$(sql "SELECT status FROM incidents WHERE tenant_id='1'")" OPEN
  ok "C2 outbox row created" "$(sql "SELECT COUNT(*) FROM notification_outbox")" 1
  emit 1 ERROR payment-api production c1-4; emit 1 ERROR payment-api production c1-5; waitdrain
  ok "C3 still one incident" "$(inc_count 1)" 1
  ok "C3 occurrence_count grew" "$(sql "SELECT occurrence_count FROM incidents WHERE tenant_id='1'")" 5

  clean_state; mkrule 1 "$RULE" >/dev/null
  for i in 1 2 3; do emit 1 WARN payment-api production c4-$i; done; waitdrain
  ok "C4 WARN does not match an ERROR rule" "$(inc_count 1)" 0

  clean_state; mkrule 1 "$RULE" >/dev/null
  for i in 1 2 3; do emit 1 ERROR other-api production c5a-$i; emit 1 ERROR payment-api staging c5b-$i; done; waitdrain
  ok "C5 other service / environment do not match" "$(inc_count 1)" 0

  clean_state; RID=$(mkrule 1 "$RULE"); api DELETE /api/v1/alert-rules/$RID 1 >/dev/null
  for i in 1 2 3; do emit 1 ERROR payment-api production c6-$i; done; waitdrain
  ok "C6 disabled rule -> no incident" "$(inc_count 1)" 0

  clean_state; mkrule 1 "$RULE" >/dev/null; mkrule 2 "$RULE" >/dev/null
  for i in 1 2 3; do emit 2 ERROR payment-api production c7-$i; done; waitdrain
  ok "C7 tenant 2 gets its incident" "$(inc_count 2)" 1
  ok "C7 tenant 1 gets none" "$(inc_count 1)" 0

  clean_state; mkrule 1 "$RANYSVC" >/dev/null
  for i in 1 2 3; do emit 1 ERROR svc-a production c9a-$i; emit 1 ERROR svc-b production c9b-$i; done; waitdrain
  ok "C9 two services -> two incidents" "$(inc_count 1)" 2

  clean_state; mkrule 1 "$R1" >/dev/null
  emit 1 ERROR svc-x production c11-1; waitdrain
  ok "C11 threshold 1 fires on first event" "$(inc_count 1)" 1

  clean_state; api POST /api/v1/alert-rules $UT "$R1" >/dev/null
  emit "\"$UT\"" ERROR svc-u production u-1; emit "\"$UT\"" ERROR svc-u production u-2; waitdrain
  ok "C13 UUID tenant: one incident" "$(inc_count $UT)" 1
  ok "C13 UUID tenant: second event counted on it" "$(sql "SELECT occurrence_count FROM incidents WHERE tenant_id='$UT'")" 2
  ok "C13 nothing dead-lettered" "$(rcli XLEN logs.dlq)" 0
}
# ---------------------------------------------------------------- D
test_D(){ section "D. incident lifecycle"
  clean_state; mkrule 1 "$R1" >/dev/null
  emit 1 ERROR svc-d production d-1; waitdrain
  IID=$(sql "SELECT id FROM incidents WHERE tenant_id='1' LIMIT 1")
  ok "D1 list" "$(api GET /api/v1/incidents 1)" 200
  ok "D1 filter status=open" "$(api GET '/api/v1/incidents?status=open' 1)" 200
  ok "D2 get" "$(api GET /api/v1/incidents/$IID 1)" 200
  ok "D2 get unknown -> 404" "$(api GET /api/v1/incidents/999999 1)" 404
  ok "D6 other tenant cannot acknowledge -> 404" "$(api POST /api/v1/incidents/$IID/acknowledge 2)" 404
  ok "D3 acknowledge" "$(api POST /api/v1/incidents/$IID/acknowledge 1)" 200
  ok "D3 status" "$(sql "SELECT status FROM incidents WHERE id=$IID")" ACKNOWLEDGED
  ok "D3 acknowledged_by" "$(sql "SELECT acknowledged_by FROM incidents WHERE id=$IID")" kalpesh
  ok "D4 assign" "$(api POST /api/v1/incidents/$IID/assign 1 '{"value":"on-call"}')" 200
  ok "D4 assign blank -> 400" "$(api POST /api/v1/incidents/$IID/assign 1 '{"value":""}')" 400
  ok "D11 VIEWER cannot resolve -> 403" "$(ROLES=VIEWER api POST /api/v1/incidents/$IID/resolve 1)" 403
  ok "D5 resolve" "$(api POST /api/v1/incidents/$IID/resolve 1)" 200
  ok "D5 status" "$(sql "SELECT status FROM incidents WHERE id=$IID")" RESOLVED
  emit 1 ERROR svc-d production d-2; waitdrain
  ok "D8 new incident opens after resolve" "$(inc_count 1)" 2
  IID2=$(sql "SELECT id FROM incidents WHERE tenant_id='1' AND status='OPEN' LIMIT 1")
  ok "D9 second resolve of the same fingerprint" "$(api POST /api/v1/incidents/$IID2/resolve 1)" 200
  ok "D9 two resolved rows" "$(sql "SELECT COUNT(*) FROM incidents WHERE tenant_id='1' AND status='RESOLVED'")" 2
}
# ---------------------------------------------------------------- cooldown
test_cooldown(){ section "cooldown (takes ~40 s)"
  clean_state; mkrule 1 "$RCOOL" >/dev/null
  emit 1 ERROR svc-k production k-1; waitdrain
  IID=$(sql "SELECT id FROM incidents WHERE tenant_id='1' LIMIT 1")
  ok "K1 first incident opens" "$(inc_count 1)" 1
  api POST /api/v1/incidents/$IID/resolve 1 >/dev/null
  emit 1 ERROR svc-k production k-2; waitdrain
  ok "K2 no new incident during the 30 s cooldown" "$(inc_count 1)" 1
  ok "K2 still only the resolved one" "$(sql "SELECT COUNT(*) FROM incidents WHERE tenant_id='1' AND status='OPEN'")" 0
  sleep 32
  emit 1 ERROR svc-k production k-3; waitdrain
  ok "K3 new incident after the cooldown" "$(inc_count 1)" 2
}
# ---------------------------------------------------------------- E (fast part)
test_E_fast(){ section "E. notification outbox"
  clean_state; mkrule 1 "$R1" >/dev/null
  emit 1 ERROR svc-e production e-1; waitdrain; sleep 3
  ok "E1 outbox row PUBLISHED" "$(sql "SELECT status FROM notification_outbox")" PUBLISHED
  ok "E1 one entry in notifications.out" "$(rcli XLEN notifications.out)" 1
  ok "E2 entry has incidentId and tenantId" "$(rcli XRANGE notifications.out - + | grep -cE 'incidentId|tenantId')" 2
  ok "E3 event key format" "$(sql "SELECT event_key LIKE 'incident-created:1:%' FROM notification_outbox")" 1
}
# ---------------------------------------------------------------- F (fast part)
test_F_fast(){ section "F. stream and reliability (fast)"
  clean_state
  rcli XADD logs.processed '*' payload 'not json' >/dev/null; waitdrain
  ok "F1 malformed payload -> DLQ" "$(rcli XLEN logs.dlq)" 1
  ok "F1 nothing left pending" "$(rcli XPENDING logs.processed alert-service | head -1)" 0

  clean_state; mkrule 1 "$R1" >/dev/null
  emit 0 ERROR svc-f production f2-1; waitdrain
  ok "F2 tenantId 0 ignored, not dead-lettered" "$(rcli XLEN logs.dlq)" 0

  clean_state; mkrule 1 "$RULE" >/dev/null
  emit 1 ERROR payment-api production dup-1; emit 1 ERROR payment-api production dup-1; emit 1 ERROR payment-api production dup-2; waitdrain
  ok "F4 duplicate delivery counted once (no incident yet)" "$(inc_count 1)" 0
  emit 1 ERROR payment-api production dup-3; waitdrain
  ok "F4 third distinct event fires" "$(inc_count 1)" 1
}
# ---------------------------------------------------------------- slow
test_slow(){ section "slow tests (~9 min)"
  # C8 window expiry
  clean_state; mkrule 1 "$RULE" >/dev/null
  emit 1 ERROR payment-api production c8-1; emit 1 ERROR payment-api production c8-2
  sleep 62
  emit 1 ERROR payment-api production c8-3; waitdrain
  ok "C8 events outside the 60 s window do not add up" "$(inc_count 1)" 0

  # E4/E5 Redis outage and the outbox
  clean_state
  docker compose stop redis >/dev/null 2>&1
  sql "INSERT INTO notification_outbox(event_key,tenant_id,payload,status,attempts,next_attempt_at,created_at) VALUES('manual:test:1','1','{\"x\":1}','PENDING',0,NOW(6),NOW(6))" >/dev/null
  sleep 10
  ok "E4 row still PENDING while Redis is down" "$(sql "SELECT status FROM notification_outbox WHERE event_key='manual:test:1'")" PENDING
  docker compose start redis >/dev/null 2>&1; sleep 30
  ok "E5 published after Redis returns" "$(sql "SELECT status FROM notification_outbox WHERE event_key='manual:test:1'")" PUBLISHED
  ok "E5 exactly one stream entry" "$(rcli XLEN notifications.out)" 1

  # F5 catch-up after downtime
  clean_state; mkrule 1 "$RULE" >/dev/null
  dc stop alert-service >/dev/null 2>&1
  for i in 1 2 3; do emit 1 ERROR payment-api production f5-$i; done
  dc start alert-service >/dev/null 2>&1; sleep 30; waitdrain
  ok "F5 events sent while the service was down were processed" "$(inc_count 1)" 1

  # F7 MySQL outage must not lose events
  clean_state; mkrule 1 "$R1" >/dev/null
  docker compose stop mysql >/dev/null 2>&1
  emit 1 ERROR svc-f production f7-1; sleep 10
  docker compose start mysql >/dev/null 2>&1; sleep 110
  ok "F7 event survived the DB outage" "$(inc_count 1)" 1
  ok "F7 nothing dead-lettered" "$(rcli XLEN logs.dlq)" 0

  # F6/F9 crash during a burst
  rcli XTRIM logs.processed MAXLEN 0 >/dev/null
  clean_state; mkrule 1 "$RULE" >/dev/null
  TS=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
  seq 1 1000 | awk -v ts="$TS" '{printf "XADD logs.processed * eventKey b%d payload \"{\\\"eventId\\\":\\\"b%d\\\",\\\"tenantId\\\":1,\\\"timestamp\\\":\\\"%s\\\",\\\"service\\\":\\\"svc-b\\\",\\\"environment\\\":\\\"production\\\",\\\"severity\\\":\\\"ERROR\\\",\\\"message\\\":\\\"burst\\\"}\"\n",$1,$1,ts}' > /tmp/burst.txt
  docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning < /tmp/burst.txt >/dev/null
  sleep 3; dc kill alert-service >/dev/null 2>&1; dc start alert-service >/dev/null 2>&1; sleep 180
  ok "F6 nothing stuck pending after recovery" "$(rcli XPENDING logs.processed alert-service | head -1)" 0
  ok "F9 stream is empty after processing" "$(rcli XLEN logs.processed)" 0
  ok "F6 no events lost to the DLQ" "$(rcli XLEN logs.dlq)" 0
}
# ---------------------------------------------------------------- gateway
test_gateway(){ section "G/R. through the gateway (identity 8081 + gateway 8080 must be running)"
  ok "G0 gateway healthy" "$(curl -s -o /dev/null -m 5 -w '%{http_code}' $GW/actuator/health)" 200
  local S="alt$(date +%s)" PT TT VT DT RID
  PT=$(tok platform "$PEM" "$PW"); ok "G0 platform admin login" "$([ -n "$PT" ] && echo yes)" yes
  ok "G0 create tenant" "$(code $GW/api/v1/tenants -H "Authorization: Bearer $PT" -H "$H" -d "{\"name\":\"AlertTest\",\"slug\":\"$S\",\"adminEmail\":\"boss@alert.io\",\"adminPassword\":\"$APW\",\"adminFullName\":\"Boss\"}")" 201
  TT=$(tok $S boss@alert.io "$APW")
  ok "G0 create viewer" "$(code $GW/api/v1/users -H "Authorization: Bearer $TT" -H "$H" -d "{\"email\":\"view@alert.io\",\"password\":\"$APW\",\"fullName\":\"Viewer\",\"roles\":[\"VIEWER\"]}")" 201
  ok "G0 create developer" "$(code $GW/api/v1/users -H "Authorization: Bearer $TT" -H "$H" -d "{\"email\":\"dev@alert.io\",\"password\":\"$APW\",\"fullName\":\"Dev\",\"roles\":[\"DEVELOPER\"]}")" 201
  VT=$(tok $S view@alert.io "$APW"); DT=$(tok $S dev@alert.io "$APW")
  clean_state
  ok "G1 no token -> 401" "$(code $GW/api/v1/alert-rules)" 401
  ok "G2 tenant admin lists rules" "$(code -H "Authorization: Bearer $TT" $GW/api/v1/alert-rules)" 200
  ok "G2 tenant admin creates a rule" "$(code -H "Authorization: Bearer $TT" -H "$H" -d "$RULE" $GW/api/v1/alert-rules)" 201; RID=$(jid)
  ok "G2 stored tenant_id is a full UUID" "$(sql "SELECT LENGTH(tenant_id) FROM alert_rules WHERE id=$RID")" 36
  ok "G4 spoofed X-Tenant-Id is ignored" "$(curl -s -H "Authorization: Bearer $TT" -H 'X-Tenant-Id: 99' $GW/api/v1/alert-rules | jq length)" 1
  ok "R2 viewer can list rules" "$(code -H "Authorization: Bearer $VT" $GW/api/v1/alert-rules)" 200
  ok "R3 viewer cannot create" "$(code -H "Authorization: Bearer $VT" -H "$H" -d "$RULE" $GW/api/v1/alert-rules)" 403
  ok "R4 viewer cannot update" "$(code -X PUT -H "Authorization: Bearer $VT" -H "$H" -d "$RULE" $GW/api/v1/alert-rules/$RID)" 403
  ok "G3 viewer cannot delete" "$(code -X DELETE -H "Authorization: Bearer $VT" $GW/api/v1/alert-rules/$RID)" 403
  ok "R5 rule still enabled" "$(sql "SELECT enabled FROM alert_rules WHERE id=$RID")" 1
  ok "R6 viewer cannot resolve incidents" "$(code -X POST -H "Authorization: Bearer $VT" $GW/api/v1/incidents/1/resolve)" 403
  ok "R7 viewer can list incidents" "$(code -H "Authorization: Bearer $VT" $GW/api/v1/incidents)" 200
  ok "R8 developer can update" "$(code -X PUT -H "Authorization: Bearer $DT" -H "$H" -d "$RULE" $GW/api/v1/alert-rules/$RID)" 200
  ok "R9 developer can delete" "$(code -X DELETE -H "Authorization: Bearer $DT" $GW/api/v1/alert-rules/$RID)" 204
}
# ---------------------------------------------------------------- pipeline
test_pipeline(){ section "P. real pipeline: ingestion -> processing -> alert"
  local missing=0 p
  for p in 8081 8080 8083 8084 8086; do
    c=$(curl -s -o /dev/null -m 3 -w '%{http_code}' localhost:$p/actuator/health)
    ok "P0 port $p healthy" "$c" 200; [ "$c" = 200 ] || missing=1
  done
  [ $missing = 1 ] && { echo "Start the missing service first (see the commands I gave you), then run this again."; return; }

  local S="pipe$(date +%s)" PT TT TJ TID KJ KEY n DLQ0 NOW
  PT=$(tok platform "$PEM" "$PW")
  TJ=$(curl -s $GW/api/v1/tenants -H "Authorization: Bearer $PT" -H "$H" -d "{\"name\":\"PipeTest\",\"slug\":\"$S\",\"adminEmail\":\"boss@pipe.io\",\"adminPassword\":\"$APW\",\"adminFullName\":\"Boss\"}")
  TID=$(echo "$TJ" | jq -r .id); TT=$(tok $S boss@pipe.io "$APW")
  ok "P1 tenant created, admin can log in" "$([ -n "$TT" ] && [ "$TID" != null ] && echo yes)" yes
  KJ=$(curl -s $GW/api/v1/api-keys -H "Authorization: Bearer $TT" -H "$H" -d '{"name":"pipeline-test"}')
  KEY=$(echo "$KJ" | jq -r .apiKey)
  ok "P2 API key created (lap_ prefix)" "$([[ $KEY == lap_* ]] && echo yes)" yes
  ok "P3 alert rule created through the gateway" "$(code -H "Authorization: Bearer $TT" -H "$H" -d '{"name":"pipeline errors","severity":"ERROR","serviceName":"payment-api","environment":"production","thresholdCount":3,"windowSeconds":120,"cooldownSeconds":0,"enabled":true}' $GW/api/v1/alert-rules)" 201
  DLQ0=$(rcli XLEN logs.dlq)
  for i in 1 2 3; do
    NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    ok "P4 log event $i accepted by ingestion (202)" "$(code $GW/api/v1/logs -H "X-API-KEY: $KEY" -H "$H" -d "{\"eventId\":\"pipe-$S-$i\",\"service\":\"payment-api\",\"environment\":\"production\",\"severity\":\"ERROR\",\"timestamp\":\"$NOW\",\"message\":\"payment failed $i\"}")" 202
  done
  n=0; for i in $(seq 1 30); do n=$(curl -s -H "Authorization: Bearer $TT" $GW/api/v1/incidents | jq length 2>/dev/null); [ "$n" = "1" ] && break; sleep 2; done
  ok "P5 an incident appeared (through the whole pipeline)" "$n" 1
  ok "P6 processing-service stored the 3 events" "$(logsql "SELECT COUNT(*) FROM processed_logs WHERE tenant_id='$TID'" | tr -d '[:space:]')" 3
  ok "P7 a notification was queued for the incident" "$(sql "SELECT COUNT(*) FROM notification_outbox WHERE tenant_id='$TID'")" 1
  NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  code $GW/api/v1/logs -H "X-API-KEY: $KEY" -H "$H" -d "{\"eventId\":\"pipe-$S-1\",\"service\":\"payment-api\",\"environment\":\"production\",\"severity\":\"ERROR\",\"timestamp\":\"$NOW\",\"message\":\"retry\"}" >/dev/null
  ok "P8 re-sent event is reported as a duplicate" "$(jq -r .duplicates /tmp/resp)" 1
  sleep 8
  ok "P9 still exactly one incident after the duplicate" "$(curl -s -H "Authorization: Bearer $TT" $GW/api/v1/incidents | jq length)" 1
  ok "P10 nothing dead-lettered" "$(( $(rcli XLEN logs.dlq) - DLQ0 ))" 0
  ok "P11 another tenant cannot see the incident" "$(curl -s -H "Authorization: Bearer $PT" $GW/api/v1/incidents | jq length)" 0
  # informational: severity aliases (processing-service only accepts the exact names)
  code $GW/api/v1/logs -H "X-API-KEY: $KEY" -H "$H" -d "{\"eventId\":\"pipe-$S-warning\",\"service\":\"payment-api\",\"environment\":\"production\",\"severity\":\"WARNING\",\"message\":\"alias test\"}" >/dev/null
  sleep 8
  echo "INFO  severity \"WARNING\" sent: logs.dlq grew by $(( $(rcli XLEN logs.dlq) - DLQ0 )) (0 = alias accepted, 1 = known processing-service bug, event dead-lettered)"
}

mode=${1:-fast}
case $mode in
  fast)     test_A; test_B; test_C; test_D; test_cooldown; test_E_fast; test_F_fast ;;
  slow)     test_slow ;;
  gateway)  test_gateway ;;
  pipeline) test_pipeline ;;
  all)      test_A; test_B; test_C; test_D; test_cooldown; test_E_fast; test_F_fast; test_slow; test_gateway; test_pipeline ;;
  *) echo "usage: $0 [fast|slow|gateway|pipeline|all]"; exit 2 ;;
esac
echo; echo "=============== RESULT: $PASS passed, $FAIL failed ==============="
[ $FAIL -eq 0 ]
