<?php
require __DIR__ . '/../_bootstrap.php';
require_method('GET');

require_admin();

$status = $_GET['status'] ?? 'pending';
if (!in_array($status, ['pending', 'approved', 'rejected', 'all'], true)) {
    fail('Invalid status filter.');
}

$sql = 'SELECT r.id, r.user_id, u.name AS user_name, u.email AS user_email,
               r.current_house, r.requested_house, r.status, r.admin_note,
               r.created_at, r.decided_at
        FROM house_change_requests r
        JOIN users u ON u.id = r.user_id ';
$params = [];
if ($status !== 'all') {
    $sql .= 'WHERE r.status = ? ';
    $params[] = $status;
}
$sql .= 'ORDER BY r.id DESC LIMIT 200';

$stmt = db()->prepare($sql);
$stmt->execute($params);
send_json(['requests' => $stmt->fetchAll()]);
