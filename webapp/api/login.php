<?php
require __DIR__ . '/_bootstrap.php';
require_method('POST');

$body = read_json();
$email    = strtolower(trim((string)($body['email']    ?? '')));
$password = (string)($body['password'] ?? '');

if ($email === '' || $password === '') {
    fail('Email and password are required.');
}

$pdo = db();
$stmt = $pdo->prepare('SELECT * FROM users WHERE email = ? LIMIT 1');
$stmt->execute([$email]);
$user = $stmt->fetch();

if (!$user || !password_verify($password, $user['password_hash'])) {
    fail('Email or password is incorrect.', 401);
}
if (!$user['email_verified']) {
    fail('Please verify your email before signing in.', 403);
}

$token = generate_token(32);
$ttlH  = (int)($CONFIG['app']['session_ttl_h'] ?? 24 * 30);
$stmt = $pdo->prepare(
    'INSERT INTO sessions (user_id, token, expires_at, created_at)
     VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR), NOW())'
);
$stmt->execute([$user['id'], $token, $ttlH]);

// Surface any pending house-change request alongside the user payload so
// the Profile screen can show the "pending admin approval" banner.
$stmt = $pdo->prepare(
    'SELECT requested_house FROM house_change_requests
     WHERE user_id = ? AND status = ? ORDER BY id DESC LIMIT 1'
);
$stmt->execute([$user['id'], 'pending']);
$pending = $stmt->fetchColumn();
$user['pending_house_number'] = $pending ?: null;

send_json([
    'token' => $token,
    'user'  => user_to_json($user),
]);
