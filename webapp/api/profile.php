<?php
require __DIR__ . '/_bootstrap.php';
require_method('GET');

$user = require_user();

$stmt = db()->prepare(
    'SELECT requested_house FROM house_change_requests
     WHERE user_id = ? AND status = ? ORDER BY id DESC LIMIT 1'
);
$stmt->execute([$user['id'], 'pending']);
$pending = $stmt->fetchColumn();
$user['pending_house_number'] = $pending ?: null;

send_json(['user' => user_to_json($user)]);
