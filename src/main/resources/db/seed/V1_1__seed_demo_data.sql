-- V1_1__seed_demo_data.sql  (local profile only, never in shared environments)
-- Timestamps are relative to now() so expiry/stats demos always work.

INSERT INTO owners (id, name, created_at) VALUES
    (1, 'alice', now() - INTERVAL '30 days'),
    (2, 'bob',   now() - INTERVAL '30 days');
SELECT setval('owners_id_seq', 2);

INSERT INTO api_keys (owner_id, key_hash, key_prefix, label, created_at, revoked_at) VALUES
    (1, 'fa3eb4fb3f5e5d2e92f91162aa0b6e130435c0c8fde4a11f452db7575a7d37d3', 'demo-key-ali', 'alice local demo key', now() - INTERVAL '30 days', NULL),
    (2, 'ec11400e814678dba943e822aabc97823053cb47b2b034c3baada4cc29c11cd7', 'demo-key-bob', 'bob local demo key',   now() - INTERVAL '30 days', NULL),
    (1, '631f19aae8e1c140e79271c3cb2d9e444224423bdd81f618d1f08f5bec7f7c4d', 'demo-key-rev', 'alice revoked key',    now() - INTERVAL '30 days', now() - INTERVAL '1 day');

-- id | code        | owner | scenario
--  1 | aB3dE7x     | alice | active random code, has human + bot clicks
--  2 | spring-docs | alice | active custom alias, expires in 30 days
--  3 | Xy9Kp2Q     | alice | EXPIRED -> redirect returns 410
--  4 | Qm4Rt8Z     | bob   | INACTIVE (soft-deleted) -> redirect returns 404
--  5 | bob-blog    | bob   | active alias owned by bob -> alice gets 404 on metadata
INSERT INTO links (id, code, owner_id, target_url, normalized_url, is_custom_alias, status,
                   created_at, expires_at, deactivated_at, click_count) VALUES
    (1, 'aB3dE7x',     1, 'https://spring.io/projects/spring-boot',      'https://spring.io/projects/spring-boot',      FALSE, 'ACTIVE',   now() - INTERVAL '7 days',  NULL,                       NULL,                      6),
    (2, 'spring-docs', 1, 'https://docs.spring.io/spring-boot/index.html','https://docs.spring.io/spring-boot/index.html',TRUE, 'ACTIVE',   now() - INTERVAL '5 days',  now() + INTERVAL '30 days', NULL,                      3),
    (3, 'Xy9Kp2Q',     1, 'https://www.postgresql.org/docs/',            'https://www.postgresql.org/docs/',            FALSE, 'ACTIVE',   now() - INTERVAL '10 days', now() - INTERVAL '1 day',   NULL,                      1),
    (4, 'Qm4Rt8Z',     2, 'https://github.com/',                         'https://github.com/',                         FALSE, 'INACTIVE', now() - INTERVAL '5 days',  NULL,                       now() - INTERVAL '2 days', 0),
    (5, 'bob-blog',    2, 'https://example.com/blog/hello-world',        'https://example.com/blog/hello-world',        TRUE,  'ACTIVE',   now() - INTERVAL '4 days',  NULL,                       NULL,                      2);
SELECT setval('links_id_seq', 5);

-- ip_hash values = HMAC-SHA256("local-dev-salt", ip) for 203.0.113.10, 198.51.100.7, 192.0.2.55 (RFC 5737 test IPs)
INSERT INTO click_events (link_id, clicked_at, referrer_host, user_agent, is_bot, ip_hash, country) VALUES
    (1, now() - INTERVAL '3 days',            'twitter.com',          'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15', FALSE, '081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5', 'US'),
    (1, now() - INTERVAL '3 days' + INTERVAL '2 hours', 'twitter.com', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0',        FALSE, '027ec7699d9fb2c0277ed9fba2da9f667d031b04f1f33117d9ff177fb6e8838b', 'GB'),
    (1, now() - INTERVAL '2 days',            'news.ycombinator.com', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15', FALSE, '081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5', 'US'),
    (1, now() - INTERVAL '1 day',             NULL,                   'Googlebot/2.1 (+http://www.google.com/bot.html)',              TRUE,  '473492d386b23c12622b28329df48f1fad12822dae0f3935d37db34173d5a243', 'US'),
    (1, now() - INTERVAL '1 day',             'linkedin.com',         'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0',        FALSE, '027ec7699d9fb2c0277ed9fba2da9f667d031b04f1f33117d9ff177fb6e8838b', 'GB'),
    (1, now() - INTERVAL '1 hour',            NULL,                   'curl/8.4.0',                                                    TRUE,  '473492d386b23c12622b28329df48f1fad12822dae0f3935d37db34173d5a243', NULL),
    (2, now() - INTERVAL '4 days',            'google.com',           'Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0',                FALSE, '081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5', 'US'),
    (2, now() - INTERVAL '2 days',            'google.com',           'Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0',                FALSE, '027ec7699d9fb2c0277ed9fba2da9f667d031b04f1f33117d9ff177fb6e8838b', 'DE'),
    (2, now() - INTERVAL '6 hours',           NULL,                   'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile Safari',       FALSE, '081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5', 'US'),
    (3, now() - INTERVAL '5 days',            'reddit.com',           'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0',        FALSE, '027ec7699d9fb2c0277ed9fba2da9f667d031b04f1f33117d9ff177fb6e8838b', 'GB'),
    (5, now() - INTERVAL '2 days',            't.co',                 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15', FALSE, '081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5', 'US'),
    (5, now() - INTERVAL '1 day',             NULL,                   'facebookexternalhit/1.1',                                       TRUE,  '473492d386b23c12622b28329df48f1fad12822dae0f3935d37db34173d5a243', 'IE');
-- Invariant checked by SeedDataIT: links.click_count = count(click_events) per link (6, 3, 1, 0, 2).
