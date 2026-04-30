<?php
/**
 * Common bootstrap for every JSON endpoint. Loads config, sets CORS +
 * JSON headers, opens a single shared PDO connection, and exposes
 * helper functions used throughout the API.
 *
 * Every endpoint should start with: require __DIR__ . '/_bootstrap.php';
 */

// --- error handling: never leak stack traces to the client ---
ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

set_exception_handler(function (Throwable $e) {
    error_log('[uninav] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
    }
    echo json_encode(['error' => 'Server error. Please try again later.']);
    exit;
});

// --- config ---
$CONFIG_PATH = dirname(__DIR__) . '/config.php';
if (!file_exists($CONFIG_PATH)) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'error' => 'Server is not configured. Copy config.example.php to '
                . 'config.php and fill in your database credentials.',
    ]);
    exit;
}
$CONFIG = require $CONFIG_PATH;

// --- CORS ---
$origin = $_SERVER['HTTP_ORIGIN'] ?? '';
$allowed = $CONFIG['app']['cors_origins'] ?? ['*'];
if (in_array('*', $allowed, true)) {
    header('Access-Control-Allow-Origin: *');
} elseif ($origin && in_array($origin, $allowed, true)) {
    header('Access-Control-Allow-Origin: ' . $origin);
    header('Vary: Origin');
}
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Content-Type: application/json; charset=utf-8');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// --- helpers ---
function db(): PDO {
    static $pdo = null;
    if ($pdo !== null) return $pdo;
    global $CONFIG;
    $dsn = sprintf(
        'mysql:host=%s;dbname=%s;charset=%s',
        $CONFIG['db']['host'], $CONFIG['db']['name'], $CONFIG['db']['charset']
    );
    $pdo = new PDO($dsn, $CONFIG['db']['user'], $CONFIG['db']['pass'], [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]);
    return $pdo;
}

function send_json(array $data, int $code = 200): void {
    http_response_code($code);
    echo json_encode($data);
    exit;
}

function fail(string $message, int $code = 400): void {
    send_json(['error' => $message], $code);
}

function read_json(): array {
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

function require_method(string $method): void {
    if ($_SERVER['REQUEST_METHOD'] !== $method) {
        fail($method . ' only.', 405);
    }
}

function generate_token(int $bytes = 32): string {
    return bin2hex(random_bytes($bytes));
}

function require_user(): array {
    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (function_exists('apache_request_headers') && !$auth) {
        $h = apache_request_headers();
        $auth = $h['Authorization'] ?? $h['authorization'] ?? '';
    }
    if (!preg_match('/^Bearer\s+(.+)$/i', $auth, $m)) {
        fail('Missing or malformed Authorization header.', 401);
    }
    $token = $m[1];
    $stmt = db()->prepare(
        'SELECT u.* FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token = ? AND s.expires_at > NOW() LIMIT 1'
    );
    $stmt->execute([$token]);
    $user = $stmt->fetch();
    if (!$user) fail('Session expired. Please log in again.', 401);
    $user['_token'] = $token;
    return $user;
}

function require_admin(): array {
    $user = require_user();
    if ((int)($user['is_admin'] ?? 0) !== 1) {
        fail('Admin access required.', 403);
    }
    return $user;
}

function user_to_json(array $row): array {
    return [
        'id'                  => (int)$row['id'],
        'name'                => $row['name'],
        'email'               => $row['email'],
        'society'             => $row['society'],
        'houseNumber'         => $row['house_number'],
        'pendingHouseNumber'  => $row['pending_house_number'] ?? null,
        'emailVerified'       => (bool)$row['email_verified'],
        'isAdmin'             => (bool)($row['is_admin'] ?? 0),
    ];
}
