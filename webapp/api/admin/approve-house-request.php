<?php
require __DIR__ . '/../_bootstrap.php';
require_method('POST');

$admin = require_admin();
$body = read_json();
$requestId = (int)($body['requestId'] ?? 0);
$decision  = (string)($body['decision'] ?? '');
$note      = trim((string)($body['adminNote'] ?? ''));

if ($requestId <= 0)                                    fail('Invalid request id.');
if (!in_array($decision, ['approve', 'reject'], true))  fail('Decision must be "approve" or "reject".');

$pdo = db();
$pdo->beginTransaction();

$stmt = $pdo->prepare(
    'SELECT * FROM house_change_requests WHERE id = ? AND status = ? FOR UPDATE'
);
$stmt->execute([$requestId, 'pending']);
$req = $stmt->fetch();
if (!$req) {
    $pdo->rollBack();
    fail('Request not found or already decided.', 404);
}

$newStatus = $decision === 'approve' ? 'approved' : 'rejected';

$stmt = $pdo->prepare(
    'UPDATE house_change_requests
        SET status = ?, admin_note = ?, decided_at = NOW(), decided_by = ?
      WHERE id = ?'
);
$stmt->execute([$newStatus, $note ?: null, $admin['id'], $requestId]);

if ($decision === 'approve') {
    // Move the user to the new house number; clear residents.assigned_user_id
    // for the old slot if any, and stake the new one.
    $stmt = $pdo->prepare('UPDATE users SET house_number = ? WHERE id = ?');
    $stmt->execute([$req['requested_house'], $req['user_id']]);

    if (!empty($req['current_house'])) {
        $stmt = $pdo->prepare(
            'UPDATE residents SET assigned_user_id = NULL
              WHERE society = ? AND house_number = ? AND assigned_user_id = ?'
        );
        $stmt->execute([
            $admin['society'] ?? 'Uninav Heights',
            $req['current_house'],
            $req['user_id'],
        ]);
    }
    $stmt = $pdo->prepare(
        'UPDATE residents SET assigned_user_id = ?
          WHERE society = ? AND house_number = ?'
    );
    $stmt->execute([
        $req['user_id'],
        $admin['society'] ?? 'Uninav Heights',
        $req['requested_house'],
    ]);
}

$pdo->commit();
send_json(['status' => $newStatus]);
