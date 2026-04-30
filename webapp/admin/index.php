<?php
/**
 * Minimal admin dashboard. Lists pending house-change requests and
 * lets an admin approve / reject each one. Auth uses the same login
 * endpoint as the Android app: log in, store the bearer token in a
 * cookie, then this page calls the JSON admin endpoints with it.
 *
 * A real production deployment would harden CSRF, rate-limiting and
 * audit logging -- this is a working starter.
 */
require __DIR__ . '/../api/_bootstrap.php';

// Pages aren't JSON, so override the Content-Type the bootstrap set.
header('Content-Type: text/html; charset=utf-8');

$action = $_POST['action'] ?? null;
$message = null;
$error = null;
$adminToken = $_COOKIE['uv_admin_token'] ?? null;
$pdo = db();

if ($action === 'login') {
    $email = strtolower(trim((string)($_POST['email'] ?? '')));
    $password = (string)($_POST['password'] ?? '');
    $stmt = $pdo->prepare('SELECT * FROM users WHERE email = ? LIMIT 1');
    $stmt->execute([$email]);
    $u = $stmt->fetch();
    if (!$u || !password_verify($password, $u['password_hash'])) {
        $error = 'Email or password is incorrect.';
    } elseif (!(int)$u['is_admin']) {
        $error = 'This account is not an administrator.';
    } else {
        $token = generate_token(32);
        $stmt = $pdo->prepare(
            'INSERT INTO sessions (user_id, token, expires_at, created_at)
             VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 12 HOUR), NOW())'
        );
        $stmt->execute([$u['id'], $token]);
        setcookie('uv_admin_token', $token, [
            'expires'  => time() + 12 * 3600,
            'path'     => '/',
            'secure'   => true,
            'httponly' => true,
            'samesite' => 'Lax',
        ]);
        header('Location: index.php');
        exit;
    }
} elseif ($action === 'logout') {
    if ($adminToken) {
        $stmt = $pdo->prepare('DELETE FROM sessions WHERE token = ?');
        $stmt->execute([$adminToken]);
    }
    setcookie('uv_admin_token', '', ['expires' => time() - 3600, 'path' => '/']);
    header('Location: index.php');
    exit;
}

// Resolve the current admin (if any) from the cookie.
$admin = null;
if ($adminToken) {
    $stmt = $pdo->prepare(
        'SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id
         WHERE s.token = ? AND s.expires_at > NOW() AND u.is_admin = 1 LIMIT 1'
    );
    $stmt->execute([$adminToken]);
    $admin = $stmt->fetch();
}

if ($admin && in_array($action, ['approve', 'reject'], true)) {
    $reqId = (int)($_POST['requestId'] ?? 0);
    $note  = trim((string)($_POST['note'] ?? ''));
    if ($reqId > 0) {
        $pdo->beginTransaction();
        $stmt = $pdo->prepare(
            'SELECT * FROM house_change_requests WHERE id = ? AND status = ? FOR UPDATE'
        );
        $stmt->execute([$reqId, 'pending']);
        $req = $stmt->fetch();
        if ($req) {
            $newStatus = $action === 'approve' ? 'approved' : 'rejected';
            $stmt = $pdo->prepare(
                'UPDATE house_change_requests SET status = ?, admin_note = ?,
                    decided_at = NOW(), decided_by = ? WHERE id = ?'
            );
            $stmt->execute([$newStatus, $note ?: null, $admin['id'], $reqId]);
            if ($action === 'approve') {
                $stmt = $pdo->prepare('UPDATE users SET house_number = ? WHERE id = ?');
                $stmt->execute([$req['requested_house'], $req['user_id']]);
                if (!empty($req['current_house'])) {
                    $stmt = $pdo->prepare(
                        'UPDATE residents SET assigned_user_id = NULL
                          WHERE society = ? AND house_number = ? AND assigned_user_id = ?'
                    );
                    $stmt->execute(['Uninav Heights', $req['current_house'], $req['user_id']]);
                }
                $stmt = $pdo->prepare(
                    'UPDATE residents SET assigned_user_id = ?
                      WHERE society = ? AND house_number = ?'
                );
                $stmt->execute([$req['user_id'], 'Uninav Heights', $req['requested_house']]);
            }
            $message = $action === 'approve' ? 'Request approved.' : 'Request rejected.';
        }
        $pdo->commit();
    }
}

$pendingCount = 0;
$requests = [];
if ($admin) {
    $stmt = $pdo->query("SELECT COUNT(*) FROM house_change_requests WHERE status = 'pending'");
    $pendingCount = (int)$stmt->fetchColumn();

    $stmt = $pdo->query(
        'SELECT r.*, u.name AS user_name, u.email AS user_email
           FROM house_change_requests r
           JOIN users u ON u.id = r.user_id
          ORDER BY (r.status = "pending") DESC, r.id DESC LIMIT 200'
    );
    $requests = $stmt->fetchAll();
}

function e($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Uni-Vishwas Admin</title>
<style>
:root { color-scheme: light dark; }
body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
       max-width: 960px; margin: 24px auto; padding: 0 16px; line-height: 1.45; }
header { display: flex; justify-content: space-between; align-items: baseline; }
.banner { padding: 10px 14px; border-radius: 8px; margin: 12px 0;
          background: #e7f5ec; color: #105a2c; }
.error  { background: #fdeaea; color: #8a1010; }
table { border-collapse: collapse; width: 100%; margin-top: 12px; }
th, td { border-bottom: 1px solid #ddd; padding: 8px; text-align: left;
         vertical-align: top; font-size: 14px; }
th { background: #f4f4f4; }
.status-pending  { color: #8a6d00; font-weight: 600; }
.status-approved { color: #186b2c; font-weight: 600; }
.status-rejected { color: #8a1010; font-weight: 600; }
form.inline { display: inline; }
button { font-size: 14px; padding: 6px 10px; border-radius: 6px;
         border: 1px solid #888; background: #fff; cursor: pointer; }
button.primary { background: #186b2c; color: #fff; border-color: #186b2c; }
button.danger  { background: #8a1010; color: #fff; border-color: #8a1010; }
input[type=text], input[type=email], input[type=password] {
    padding: 8px; border: 1px solid #aaa; border-radius: 6px; width: 100%;
    box-sizing: border-box; font-size: 14px; }
.login { max-width: 360px; margin: 80px auto; }
.login label { display: block; margin-top: 12px; font-size: 13px; }
@media (prefers-color-scheme: dark) {
  body { background: #181818; color: #ddd; }
  th { background: #222; }
  th, td { border-color: #333; }
  .banner { background: #143a23; color: #cfe9d6; }
  .error  { background: #3a1717; color: #f5c4c4; }
  button { background: #2a2a2a; color: #ddd; border-color: #555; }
  input { background: #222; color: #ddd; border-color: #555; }
}
</style>
</head>
<body>

<?php if (!$admin): ?>
  <div class="login">
    <h1>Uni-Vishwas Admin</h1>
    <p>Sign in with your admin email and password.</p>
    <?php if ($error): ?><div class="banner error"><?= e($error) ?></div><?php endif; ?>
    <form method="post">
      <input type="hidden" name="action" value="login">
      <label>Email
        <input type="email" name="email" required autocomplete="email">
      </label>
      <label>Password
        <input type="password" name="password" required autocomplete="current-password">
      </label>
      <button type="submit" class="primary" style="margin-top:16px;">Sign in</button>
    </form>
  </div>

<?php else: ?>
  <header>
    <div>
      <h1 style="margin:0;">Uni-Vishwas Admin</h1>
      <small>Signed in as <?= e($admin['email']) ?></small>
    </div>
    <form method="post" class="inline">
      <input type="hidden" name="action" value="logout">
      <button type="submit">Log out</button>
    </form>
  </header>

  <?php if ($message): ?><div class="banner"><?= e($message) ?></div><?php endif; ?>

  <h2>House-number change requests
    <small>(<?= (int)$pendingCount ?> pending)</small>
  </h2>

  <?php if (!$requests): ?>
    <p>No requests yet.</p>
  <?php else: ?>
    <table>
      <thead>
        <tr>
          <th>#</th><th>Resident</th><th>Current</th><th>Requested</th>
          <th>Status</th><th>Submitted</th><th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <?php foreach ($requests as $r): ?>
          <tr>
            <td><?= (int)$r['id'] ?></td>
            <td>
              <?= e($r['user_name']) ?><br>
              <small><?= e($r['user_email']) ?></small>
            </td>
            <td><?= e($r['current_house'] ?: '—') ?></td>
            <td><strong><?= e($r['requested_house']) ?></strong></td>
            <td><span class="status-<?= e($r['status']) ?>"><?= e($r['status']) ?></span></td>
            <td><small><?= e($r['created_at']) ?></small></td>
            <td>
              <?php if ($r['status'] === 'pending'): ?>
                <form method="post" class="inline">
                  <input type="hidden" name="action" value="approve">
                  <input type="hidden" name="requestId" value="<?= (int)$r['id'] ?>">
                  <button type="submit" class="primary">Approve</button>
                </form>
                <form method="post" class="inline">
                  <input type="hidden" name="action" value="reject">
                  <input type="hidden" name="requestId" value="<?= (int)$r['id'] ?>">
                  <button type="submit" class="danger">Reject</button>
                </form>
              <?php else: ?>
                <small><?= e($r['admin_note'] ?: '') ?></small>
              <?php endif; ?>
            </td>
          </tr>
        <?php endforeach; ?>
      </tbody>
    </table>
  <?php endif; ?>
<?php endif; ?>

</body>
</html>
