<?php
/**
 * Copy this file to config.php and fill in real values.
 * config.php is git-ignored so secrets stay out of source control.
 */
return [
    'db' => [
        // Hostinger gives you these in: hPanel -> Databases -> MySQL Databases.
        'host'    => 'localhost',
        'name'    => 'u123456789_uninav',
        'user'    => 'u123456789_uninav',
        'pass'    => 'CHANGE_ME',
        'charset' => 'utf8mb4',
    ],
    'mail' => [
        // Hostinger lets you create a free mailbox under your domain
        // (Emails -> Email Accounts). Use it as the From address so
        // verification messages don't get flagged as spam.
        'from_email'        => 'no-reply@yourdomain.com',
        'from_name'         => 'Uni-Vishwas',
        // The link inside the verification email lands on verify-email.php
        // hosted on your Hostinger domain. Update this once your domain
        // is wired up.
        'verify_link_base'  => 'https://yourdomain.com/verify-email.php',
    ],
    'app' => [
        'society_name'   => 'Uninav Heights',
        'session_ttl_h'  => 24 * 30,            // 30 days
        // Allowed CORS origins for the Android app. '*' is fine because the
        // Android WebView and OkHttp both ignore CORS, but if you ever serve
        // this API to a browser, lock it down to your real origin(s).
        'cors_origins'   => ['*'],
        // Set to a strong random string (e.g. via `openssl rand -hex 32`)
        // and never share it. Used to sign internal stuff in future.
        'app_secret'     => 'CHANGE_ME_TO_A_RANDOM_64_CHAR_STRING',
        // Email of the first admin. After running setup.php once, log in
        // with this email and the password you set on the setup screen.
        'bootstrap_admin_email' => 'admin@yourdomain.com',
    ],
];
