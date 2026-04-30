<?php
/**
 * Browser-facing landing page for the email-verification link.
 * Marks the user verified and shows a friendly confirmation page.
 */
require __DIR__ . '/api/_bootstrap.php';
header('Content-Type: text/html; charset=utf-8');

$token = trim((string)($_GET['token'] ?? ''));
$ok = false;
$message = '';

if ($token === '') {
    $message = 'No verification token provided.';
} else {
    $pdo = db();
    $stmt = $pdo->prepare('SELECT id FROM users WHERE verify_token = ? AND email_verified = 0');
    $stmt->execute([$token]);
    $u = $stmt->fetch();
    if ($u) {
        $stmt = $pdo->prepare('UPDATE users SET email_verified = 1, verify_token = NULL WHERE id = ?');
        $stmt->execute([$u['id']]);
        $ok = true;
        $message = 'Email verified! You can now open the Uni-Vishwas app and log in.';
    } else {
        $message = 'This link is invalid or has already been used.';
    }
}
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Email verification - Uni-Vishwas</title>
<style>
:root { color-scheme: light dark; }
body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
       max-width: 420px; margin: 80px auto; padding: 0 24px; text-align: center; line-height: 1.5; }
.icon { font-size: 56px; margin-bottom: 8px; }
.ok    { color: #186b2c; }
.error { color: #8a1010; }
</style>
</head>
<body>
  <div class="icon"><?= $ok ? '✅' : '⚠️' ?></div>
  <h1 class="<?= $ok ? 'ok' : 'error' ?>">
    <?= $ok ? 'Email verified' : 'Could not verify' ?>
  </h1>
  <p><?= htmlspecialchars($message, ENT_QUOTES, 'UTF-8') ?></p>
</body>
</html>
