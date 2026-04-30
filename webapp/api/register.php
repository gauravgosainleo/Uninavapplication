<?php
require __DIR__ . '/_bootstrap.php';
require_method('POST');

$body = read_json();
$name     = trim((string)($body['name']     ?? ''));
$email    = strtolower(trim((string)($body['email']    ?? '')));
$password = (string)($body['password'] ?? '');

if ($name === '')                               fail('Name is required.');
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Enter a valid email address.');
if (strlen($password) < 8)                      fail('Password must be at least 8 characters.');

$pdo = db();
$stmt = $pdo->prepare('SELECT id FROM users WHERE email = ?');
$stmt->execute([$email]);
if ($stmt->fetch()) fail('An account with this email already exists.', 409);

$society = $CONFIG['app']['society_name'];

// Auto-allocate from residents roster if name matches an unassigned slot.
$stmt = $pdo->prepare(
    'SELECT id, house_number FROM residents
     WHERE LOWER(TRIM(name)) = LOWER(TRIM(?))
       AND society = ?
       AND assigned_user_id IS NULL
     ORDER BY id ASC LIMIT 1'
);
$stmt->execute([$name, $society]);
$resident = $stmt->fetch();
$houseNumber = $resident['house_number'] ?? null;

$verifyToken  = generate_token(24);
$passwordHash = password_hash($password, PASSWORD_BCRYPT);

$pdo->beginTransaction();
$stmt = $pdo->prepare(
    'INSERT INTO users
        (name, email, password_hash, society, house_number,
         email_verified, verify_token, created_at)
     VALUES (?, ?, ?, ?, ?, 0, ?, NOW())'
);
$stmt->execute([$name, $email, $passwordHash, $society, $houseNumber, $verifyToken]);
$userId = (int)$pdo->lastInsertId();

if ($resident) {
    $stmt = $pdo->prepare(
        'UPDATE residents SET assigned_user_id = ? WHERE id = ?'
    );
    $stmt->execute([$userId, $resident['id']]);
}
$pdo->commit();

// Send verification email. Hostinger's PHP mail() uses your domain's
// MX-configured outbound mailer; no extra setup needed.
$link = $CONFIG['mail']['verify_link_base'] . '?token=' . urlencode($verifyToken);
$subject = 'Verify your Uni-Vishwas email';
$msg  = "Hi $name,\r\n\r\n";
$msg .= "Welcome to Uni-Vishwas. Please verify your email by opening this link:\r\n\r\n";
$msg .= "$link\r\n\r\n";
$msg .= "If you did not sign up, you can ignore this email.\r\n\r\n";
$msg .= "-- Uni-Vishwas";
$headers  = 'From: ' . $CONFIG['mail']['from_name'] . ' <' . $CONFIG['mail']['from_email'] . ">\r\n";
$headers .= 'Reply-To: ' . $CONFIG['mail']['from_email'] . "\r\n";
$headers .= "Content-Type: text/plain; charset=utf-8\r\n";
@mail($email, $subject, $msg, $headers);

send_json([
    'status'  => 'ok',
    'message' => 'Account created. Check your email for the verification link.',
    'autoAllocatedHouseNumber' => $houseNumber,
]);
