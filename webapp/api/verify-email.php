<?php
/**
 * JSON variant of email verification, intended for in-app use.
 * The browser-facing version (the link target inside the email) is
 * /verify-email.php at the document root.
 */
require __DIR__ . '/_bootstrap.php';

$token = $_GET['token'] ?? '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = read_json();
    $token = (string)($body['token'] ?? $token);
}
$token = trim((string)$token);
if ($token === '') fail('Verification token is required.');

$pdo = db();
$stmt = $pdo->prepare('SELECT id FROM users WHERE verify_token = ? AND email_verified = 0');
$stmt->execute([$token]);
$user = $stmt->fetch();
if (!$user) fail('This verification link is invalid or has already been used.', 404);

$stmt = $pdo->prepare(
    'UPDATE users SET email_verified = 1, verify_token = NULL WHERE id = ?'
);
$stmt->execute([$user['id']]);

send_json(['status' => 'ok', 'message' => 'Email verified. You can now log in.']);
