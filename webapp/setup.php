<?php
/**
 * One-time installer. Open this URL in your browser the first time
 * you upload the webapp to Hostinger. It:
 *   1. Verifies config.php is in place.
 *   2. Creates all tables from setup/schema.sql.
 *   3. Optionally seeds residents from setup/seed_residents.sql.
 *   4. Creates the bootstrap admin account so you can sign in to /admin/.
 *
 * After it runs successfully, DELETE this file from your server. Leaving
 * it accessible is a security risk.
 */
require __DIR__ . '/api/_bootstrap.php';
header('Content-Type: text/html; charset=utf-8');

$step = $_POST['step'] ?? null;
$messages = [];
$errors = [];

try {
    $pdo = db();
} catch (Throwable $e) {
    $errors[] = 'Could not connect to MySQL: ' . $e->getMessage();
}

if (!$errors && $step === 'install') {
    $adminEmail = strtolower(trim((string)($_POST['admin_email'] ?? '')));
    $adminPass  = (string)($_POST['admin_password'] ?? '');
    $adminName  = trim((string)($_POST['admin_name'] ?? 'Administrator'));
    $seedDemo   = !empty($_POST['seed_demo']);

    if (!filter_var($adminEmail, FILTER_VALIDATE_EMAIL)) $errors[] = 'Admin email is invalid.';
    if (strlen($adminPass) < 8) $errors[] = 'Admin password must be at least 8 characters.';

    if (!$errors) {
        $sql = file_get_contents(__DIR__ . '/setup/schema.sql');
        foreach (array_filter(array_map('trim', explode(';', $sql))) as $stmt) {
            $pdo->exec($stmt);
        }
        $messages[] = 'Schema applied.';

        if ($seedDemo) {
            $sql = file_get_contents(__DIR__ . '/setup/seed_residents.sql');
            foreach (array_filter(array_map('trim', explode(';', $sql))) as $stmt) {
                if ($stmt) $pdo->exec($stmt);
            }
            $messages[] = 'Sample residents inserted.';
        }

        // Create or upgrade bootstrap admin.
        $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ?');
        $stmt->execute([$adminEmail]);
        $existing = $stmt->fetch();
        $hash = password_hash($adminPass, PASSWORD_BCRYPT);
        if ($existing) {
            $stmt = $pdo->prepare(
                'UPDATE users SET password_hash = ?, is_admin = 1, email_verified = 1
                  WHERE id = ?'
            );
            $stmt->execute([$hash, $existing['id']]);
            $messages[] = 'Existing user upgraded to admin.';
        } else {
            $stmt = $pdo->prepare(
                'INSERT INTO users
                    (name, email, password_hash, society, is_admin, email_verified, created_at)
                 VALUES (?, ?, ?, ?, 1, 1, NOW())'
            );
            $stmt->execute([$adminName, $adminEmail, $hash, $CONFIG['app']['society_name']]);
            $messages[] = 'Bootstrap admin account created.';
        }
    }
}

function e($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Uni-Vishwas - Setup</title>
<style>
body { font-family: system-ui, sans-serif; max-width: 560px; margin: 40px auto;
       padding: 0 20px; line-height: 1.5; }
label { display: block; margin-top: 12px; font-size: 14px; }
input[type=text], input[type=email], input[type=password] {
    padding: 8px; border: 1px solid #aaa; border-radius: 6px; width: 100%;
    box-sizing: border-box; font-size: 14px; }
.banner { padding: 10px 14px; border-radius: 6px; margin: 8px 0; }
.ok    { background: #e7f5ec; color: #105a2c; }
.error { background: #fdeaea; color: #8a1010; }
button { margin-top: 16px; padding: 10px 16px; font-size: 14px;
         border-radius: 6px; border: none; background: #186b2c; color: #fff; cursor: pointer; }
.warn { background: #fff5d6; padding: 10px 14px; border-radius: 6px; }
</style>
</head>
<body>
<h1>Uni-Vishwas - Setup</h1>

<?php foreach ($messages as $m): ?><div class="banner ok"><?= e($m) ?></div><?php endforeach; ?>
<?php foreach ($errors as $err): ?><div class="banner error"><?= e($err) ?></div><?php endforeach; ?>

<?php if ($step === 'install' && !$errors): ?>
  <p>Setup complete. You can now:</p>
  <ul>
    <li>Open <code>/admin/</code> and sign in with the admin email + password you just set.</li>
    <li>Replace the sample residents in the <code>residents</code> table with real Uninav Heights data.</li>
    <li><strong>Delete this <code>setup.php</code> file from your server now.</strong></li>
  </ul>
<?php else: ?>
  <p class="warn">
    Run this once. After the success screen, delete <code>setup.php</code> from your server -
    leaving it accessible is a security risk.
  </p>
  <form method="post">
    <input type="hidden" name="step" value="install">
    <label>Admin name
      <input type="text" name="admin_name" value="Administrator" required>
    </label>
    <label>Admin email
      <input type="email" name="admin_email" required>
    </label>
    <label>Admin password (8+ chars)
      <input type="password" name="admin_password" required minlength="8">
    </label>
    <label>
      <input type="checkbox" name="seed_demo" checked>
      Insert 5 sample residents (you can replace them later)
    </label>
    <button type="submit">Install</button>
  </form>
<?php endif; ?>
</body>
</html>
