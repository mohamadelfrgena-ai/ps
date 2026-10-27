-- ============================================================================
-- حسابات المستخدمين (auth) — مستخرجة من المشروع القديم jqjmkssgjdsfdojkuqzq
-- بتاريخ 2026-10-27. كلمات المرور المشفّرة كما هي ⇒ تبقى كلمات مرور
-- المستخدمين الحالية تعمل بعد النقل.
-- يُنفَّذ على المشروع الجديد: psql ... -f old-auth.sql
-- ============================================================================
begin;

insert into auth.users (id, instance_id, email, encrypted_password, email_confirmed_at,
                        phone, confirmed_at, last_sign_in_at,
                        raw_app_meta_data, raw_user_meta_data,
                        created_at, updated_at, phone_confirmed_at)
select * from json_populate_recordset(null::auth.users, '[
{"id":"8694dace-72e9-447b-bba1-77b1fca37742","instance_id":"00000000-0000-0000-0000-000000000000","email":"silverfoxinteractive@gmail.com","encrypted_password":"$2a$10$S8egpkj3F9CJHGokGmVJvuNxT3R6LUzuTH7LTvVNFCQEWW0R18B2G","email_confirmed_at":"2026-09-29 14:41:08.575851+00","phone":null,"confirmed_at":"2026-09-29 14:41:08.575851+00","last_sign_in_at":"2026-10-26 12:58:46.363571+00","raw_app_meta_data":{"provider":"email","providers":["email"]},"raw_user_meta_data":{"email_verified":true},"created_at":"2026-09-29 14:41:08.527861+00","updated_at":"2026-10-26 14:02:45.981468+00","phone_confirmed_at":null},
{"id":"f2dc4a3d-489f-4bf4-8a19-723127397cd4","instance_id":"00000000-0000-0000-0000-000000000000","email":"test@gmail.com","encrypted_password":"$2a$10$VIpwuAENkuol.1bKw.072ecY3GoMXQ.FH1q9twio9UvZGFWPKi1M6","email_confirmed_at":"2026-10-05 15:56:22.818301+00","phone":null,"confirmed_at":"2026-10-05 15:56:22.818301+00","last_sign_in_at":"2026-10-26 23:54:07.290301+00","raw_app_meta_data":{"provider":"email","providers":["email"]},"raw_user_meta_data":{"email_verified":true},"created_at":"2026-10-05 15:56:22.744974+00","updated_at":"2026-10-26 23:54:07.366738+00","phone_confirmed_at":null},
{"id":"bfc2a07b-d0a4-45a6-b566-2db7ead6e1f1","instance_id":"00000000-0000-0000-0000-000000000000","email":"aloi@gmail.com","encrypted_password":"$2a$10$HVXrAzUVaq0yIgmSNG9UpOyyHXc2P9Zevm5W27jmWtNFYhLYO3WGm","email_confirmed_at":null,"phone":null,"confirmed_at":null,"last_sign_in_at":null,"raw_app_meta_data":{"provider":"email","providers":["email"]},"raw_user_meta_data":{"sub":"bfc2a07b-d0a4-45a6-b566-2db7ead6e1f1","email":"aloi@gmail.com","username":"aloi","email_verified":false,"phone_verified":false},"created_at":"2026-10-20 18:28:23.474945+00","updated_at":"2026-10-20 18:28:25.000232+00","phone_confirmed_at":null}
]'::json);

insert into auth.identities (id, user_id, provider, provider_id, identity_data,
                             last_sign_in_at, created_at, updated_at)
select * from json_populate_recordset(null::auth.identities, '[
{"id":"20355008-fff9-421e-a94c-b441a273cfd9","user_id":"8694dace-72e9-447b-bba1-77b1fca37742","provider":"email","provider_id":"8694dace-72e9-447b-bba1-77b1fca37742","identity_data":{"sub":"8694dace-72e9-447b-bba1-77b1fca37742","email":"silverfoxinteractive@gmail.com","email_verified":false,"phone_verified":false},"last_sign_in_at":"2026-09-29 14:41:08.572248+00","created_at":"2026-09-29 14:41:08.572336+00","updated_at":"2026-09-29 14:41:08.572336+00"},
{"id":"e51544e1-a269-4564-a22a-83e903cd3486","user_id":"f2dc4a3d-489f-4bf4-8a19-723127397cd4","provider":"email","provider_id":"f2dc4a3d-489f-4bf4-8a19-723127397cd4","identity_data":{"sub":"f2dc4a3d-489f-4bf4-8a19-723127397cd4","email":"test@gmail.com","email_verified":false,"phone_verified":false},"last_sign_in_at":"2026-10-05 15:56:22.810086+00","created_at":"2026-10-05 15:56:22.810811+00","updated_at":"2026-10-05 15:56:22.810811+00"},
{"id":"bf647714-f027-44f0-bd85-6cdf061e3271","user_id":"bfc2a07b-d0a4-45a6-b566-2db7ead6e1f1","provider":"email","provider_id":"bfc2a07b-d0a4-45a6-b566-2db7ead6e1f1","identity_data":{"sub":"bfc2a07b-d0a4-45a6-b566-2db7ead6e1f1","email":"aloi@gmail.com","username":"aloi","email_verified":false,"phone_verified":false},"last_sign_in_at":"2026-10-20 18:28:23.576215+00","created_at":"2026-10-20 18:28:23.576264+00","updated_at":"2026-10-20 18:28:23.576264+00"}
]'::json);

commit;
