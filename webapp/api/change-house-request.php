<?php
require __DIR__ . '/_bootstrap.php';
require_method('POST');

$user = require_user();
$body = read_json();
$requested = trim((string)($body['requestedHouseNumber'] ?? ''));

if ($requested === '') {
    fail('Enter a house number.');
}
if (strcasecmp($requested, (string)$user['house_number']) === 0) {
    fail('That is already your current house number.');
}

$pdo = db();
$stmt = $pdo->prepare(
    'SELECT id FROM house_change_requests
     WHERE user_id = ? AND status = ? LIMIT 1'
);
$stmt->execute([$user['id'], 'pending']);
if ($stmt->fetch()) {
    fail('You already have a pending request. Wait for an admin to approve it.', 409);
}

$stmt = $pdo->prepare(
    'INSERT INTO house_change_requests
        (user_id, current_house, requested_house, status, created_at)
     VALUES (?, ?, ?, ?, NOW())'
);
$stmt->execute([$user['id'], $user['house_number'], $requested, 'pending']);

send_json([
    'status'              => 'pending_admin_approval',
    'pendingHouseNumber'  => $requested,
    'message'             => 'Request submitted. An admin will review it shortly.',
]);
