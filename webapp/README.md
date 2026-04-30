# Uni-Vishwas Backend (PHP + MySQL, Hostinger-ready)

Drop-in starter backend for the Uni-Vishwas Android app. Runs on
Hostinger shared hosting (or any PHP 8.0+ host with MySQL/MariaDB).

## What's inside

```
webapp/
├── api/
│   ├── _bootstrap.php                      ← shared CORS/DB/auth helpers
│   ├── register.php                        ← POST  /api/register.php
│   ├── login.php                           ← POST  /api/login.php
│   ├── verify-email.php                    ← POST  /api/verify-email.php
│   ├── profile.php                         ← GET   /api/profile.php
│   ├── change-house-request.php            ← POST  /api/change-house-request.php
│   └── admin/
│       ├── house-requests.php              ← GET   /api/admin/house-requests.php
│       └── approve-house-request.php       ← POST  /api/admin/approve-house-request.php
├── admin/
│   └── index.php                           ← Web admin dashboard at /admin/
├── verify-email.php                        ← Browser landing page for email links
├── setup.php                               ← One-time installer (delete after running)
├── setup/
│   ├── schema.sql
│   └── seed_residents.sql
├── config.example.php                      ← Copy to config.php and edit
├── .htaccess
└── README.md
```

## Deploy to Hostinger in 5 minutes

### 1. Create a MySQL database

In **hPanel → Databases → MySQL Databases** create a database and a user.
Note the four values (`db host`, `name`, `user`, `pass`) — Hostinger
typically prefixes the latter three with your account number, e.g.
`u123456789_uninav`.

### 2. Upload the webapp

Either:

- Upload **the contents of this `webapp/` folder** (not the folder
  itself) into `public_html/` via the Hostinger File Manager, OR
- Drop them on a subdomain root like `api.yourdomain.com`'s
  `public_html/`.

If you'd rather have it on a sub-path (e.g. `yourdomain.com/uninav/`),
copy into `public_html/uninav/` — every path in this README and in the
Android app's `API_BASE_URL` then needs to be prefixed with `/uninav`.

### 3. Configure

```bash
cp config.example.php config.php
```

…then edit `config.php` and fill in:

- `db.host` / `db.name` / `db.user` / `db.pass` — from step 1.
- `mail.from_email` — a real mailbox on your domain (create one in
  hPanel → Emails → Email Accounts; Hostinger's outbound mail
  delivers via that mailbox by default).
- `mail.verify_link_base` — the URL on your domain that points at
  `verify-email.php`. E.g. `https://yourdomain.com/verify-email.php`.
- `app.app_secret` — a random 64-char string. Run
  `openssl rand -hex 32` on any Linux/Mac to generate one.
- `app.bootstrap_admin_email` — the email you'll use as the first admin.

### 4. Run setup

Open `https://yourdomain.com/setup.php` in your browser. It will:

1. Create all tables (`users`, `sessions`, `residents`,
   `house_change_requests`).
2. Optionally seed 5 sample residents.
3. Create the bootstrap admin account.

When the success screen appears, **delete `setup.php` from the server**
(via the File Manager or FTP). Leaving it accessible is a security risk.

### 5. Replace sample residents with the real list

The `residents` table is the source of truth for auto-allocating house
numbers when someone signs up. Replace the sample rows with your real
Uninav Heights roster, either:

- **In phpMyAdmin** → `residents` → Insert / Import a CSV with
  columns `name`, `house_number`, `society`. Leave `assigned_user_id`
  empty for unallocated slots; setting it stops further matches.
- **By editing `setup/seed_residents.sql`** to your real data and
  re-running just that file.

### 6. Sign in to the admin dashboard

Visit `https://yourdomain.com/admin/` and log in with the admin email
and password you set during setup. You'll see the house-number change
request queue — approve or reject as residents submit corrections from
the Android app's Profile screen.

### 7. Point the Android app at your backend

In `android-wrapper/app/src/main/java/com/univishwas/app/auth/ApiClient.java`
update:

```java
public static final String API_BASE_URL =
        "https://yourdomain.com/api";
```

…then rebuild the AAB. The endpoint paths in this README match the
ones the app will hit.

## Endpoints at a glance

All endpoints accept and return JSON. Successful responses are 2xx;
errors carry a JSON body `{"error": "..."}` with an appropriate code.

### Auth

| Method | Path | Body |
| --- | --- | --- |
| `POST` | `/api/register.php` | `{name, email, password}` |
| `POST` | `/api/verify-email.php` | `{token}` |
| `POST` | `/api/login.php` | `{email, password}` → `{token, user}` |

### User (requires `Authorization: Bearer <token>`)

| Method | Path | Body |
| --- | --- | --- |
| `GET`  | `/api/profile.php` | – |
| `POST` | `/api/change-house-request.php` | `{requestedHouseNumber}` |

### Admin (requires admin bearer token)

| Method | Path | Body |
| --- | --- | --- |
| `GET`  | `/api/admin/house-requests.php?status=pending` | – |
| `POST` | `/api/admin/approve-house-request.php` | `{requestId, decision: "approve"\|"reject", adminNote?}` |

## Local testing

Any PHP 8.0+ install works. From the project root:

```bash
cd webapp
cp config.example.php config.php   # edit DB creds, point at a local MySQL
php -S 127.0.0.1:8080
```

Open `http://127.0.0.1:8080/setup.php`, then test endpoints with
`curl`:

```bash
curl -s -X POST http://127.0.0.1:8080/api/register.php \
     -H 'Content-Type: application/json' \
     -d '{"name":"Test User","email":"test@example.com","password":"secret123"}'
```

## Security notes

- Passwords are stored as bcrypt hashes (`password_hash`).
- Sessions are random 64-hex-char tokens with a configurable TTL
  (default 30 days). Rotate by deleting rows from `sessions`.
- The web admin dashboard uses `httponly + secure + samesite=Lax`
  cookies; serve over HTTPS (Hostinger's free SSL via Let's Encrypt is
  enabled by default).
- `setup.php` is a privileged installer — delete after first run.
- The starter intentionally has no rate-limiting, no CAPTCHA, and no
  CSRF protection on the admin form. Add fail2ban / Cloudflare /
  CSRF tokens before going to production at scale.
