// Error sweep — throws bad input at every endpoint and fails if anything answers 500 or without an error code.
//
//   node scripts/error-sweep.mjs --yes [baseUrl]      default http://localhost:3000
//
// For every endpoint, as Admin, Manager, Senior and Learner, with a real id and a missing one, it sends: no body,
// broken JSON, the wrong JSON shape, all-null fields, empty values, wrong types, lists of nulls, oversized values
// and a "nearly valid" body with bad references. Every non-2xx answer must be JSON with an ERR- code.
//
// It WRITES to the database (a nearly-valid body can create a journey or a user), so run it against a throwaway
// copy: back up first and restore afterwards. That is why it refuses to run without --yes. It never sends
// DELETE with a real id.
//
// DB access (to find real ids) uses the same variables as the other scripts:
//   MATRIX_DB_PORT / MATRIX_DB_PASSWORD / MATRIX_DB_USER / MATRIX_DB_HOST / MATRIX_MYSQL_BIN

import { execFileSync } from 'node:child_process';

const args = process.argv.slice(2);
if (!args.includes('--yes')) {
  console.error('This sweep writes junk into the database. Back it up, then run with --yes (and restore afterwards).');
  process.exit(2);
}
const BASE = (args.find((a) => !a.startsWith('--')) || 'http://localhost:3000') + '/api';
const { env } = process;
const MYSQL = env.MATRIX_MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_ARGS = ['-u', env.MATRIX_DB_USER || 'root', `-p${env.MATRIX_DB_PASSWORD ?? '12345'}`,
  '-h', env.MATRIX_DB_HOST || '127.0.0.1', '-P', env.MATRIX_DB_PORT || '3306', '-N', '-B', 'journey_db'];

function one(query) {
  return execFileSync(MYSQL, [...DB_ARGS, '-e', query], { stdio: ['ignore', 'pipe', 'ignore'] })
    .toString().replace(/\r\n/g, '\n').trim().split('\n')[0] || 'missing';
}

// ─── Endpoints (method, path) ───────────────────────────────────────────────────────────────────────────────
const ENDPOINTS = `
GET /announcements|POST /announcements|GET /announcements/active|DELETE /announcements/{announcement}
GET /announcements/{announcement}|PUT /announcements/{announcement}|POST /announcements/{announcement}/dismiss
POST /auth/login|POST /auth/signup|GET /catalog|POST /catalog/enroll|GET /catalog/journeys/{journey}
GET /catalog/packages/{package}|GET /certificates|GET /certificates/{certificate}|GET /dashboard/manager
GET /dashboard/senior/{senior}|GET /departments|POST /departments|DELETE /departments/{department}
PUT /departments/{department}|PATCH /exam-attempts/{attempt}/grade|POST /files|GET /files/{fileKey}
GET /getting-started|POST /getting-started/dismiss|GET /journeys|POST /journeys|DELETE /journeys/{journey}
GET /journeys/{journey}|PUT /journeys/{journey}|GET /journeys/{journey}/items|GET /journeys/{journey}/preview
GET /journeys/{journey}/units|DELETE /journeys/{examJourney}/exam|GET /journeys/{examJourney}/exam
POST /journeys/{examJourney}/exam|GET /learner-journey-items/{item}|POST /learner-journey-items/{item}/complete
POST /learner-journey-items/{item}/notes|POST /learner-journey-items/{item}/open
PATCH /learner-journey-items/{item}/status|POST /learner-journey-items/{item}/time
POST /learner-journey-units/{unit}/notes|GET /learner-journey-units/{unit}/quiz
POST /learner-journey-units/{unit}/quiz|PATCH /learner-journey-units/{unit}/status
POST /learner-journey-units/{unit}/submit|GET /learner-journeys|POST /learner-journeys|GET /learner-journeys/{lj}
PATCH /learner-journeys/{lj}/due-date|GET /learner-journeys/{lj}/outline|GET /learner-journeys/{lj}/packages
PATCH /learner-journeys/{lj}/status|GET /learner-journeys/{examLj}/exam-attempt|POST /learner-journeys/{examLj}/exam-attempt
GET /metrics/learner/{learner}|GET /metrics/org|GET /metrics/senior/{senior}|GET /notifications
GET /notifications/mail-status|POST /notifications/test-email|PATCH /notifications/read-all|GET /notifications/recent
GET /notifications/unread-count|PATCH /notifications/{notification}/read|GET /package-assignments
POST /package-assignments|GET /package-assignments/{assignment}|POST /package-assignments/{assignment}/cancel
GET /packages|POST /packages|DELETE /packages/{package}|GET /packages/{package}|PUT /packages/{package}
GET /team|GET /team/learners/{learner}|GET /users|POST /users|PATCH /users/me/intro|PATCH /users/me/language
DELETE /users/{user}|GET /users/{user}|PATCH /users/{user}|PUT /users/{user}`
  .split(/[|\n]/).map((s) => s.trim()).filter(Boolean).map((s) => { const [method, path] = s.split(' '); return { method, path }; });

const QUERY_JUNK = ['?size=abc&page=-1', '?learnerId=nope&departmentId=nope&status=abc&type=abc&q=%00', '?lang=xx&seniorId=%27%22'];

// ─── Bodies ─────────────────────────────────────────────────────────────────────────────────────────────────
const STRING_KEYS = ['title', 'body', 'email', 'password', 'name', 'id', 'english', 'arabic', 'examId', 'description',
  'techTag', 'packageId', 'learnerId', 'message', 'journeyId', 'departmentId', 'seniorId', 'language', 'questionId'];
const NUMBER_KEYS = ['type', 'passingScorePercent', 'targetDays', 'status', 'timeSpentHours', 'role', 'seconds', 'version'];
const BOOL_KEYS = ['orgWide', 'notifyAgain', 'passed', 'clearSenior'];
const DATE_KEYS = ['showUntil', 'dueDate'];
const LIST_KEYS = ['departmentIds', 'marks', 'questions', 'answers', 'items', 'units', 'journeyIds', 'quiz', 'options'];
const ALL_KEYS = [...STRING_KEYS, ...NUMBER_KEYS, ...BOOL_KEYS, ...DATE_KEYS, ...LIST_KEYS];
const fill = (fn) => Object.fromEntries(ALL_KEYS.map((k) => [k, fn(k)]));

const BODIES = {
  none: undefined,
  broken: { raw: '{"title": ' },
  array: { raw: '[1,2]' },
  nullLiteral: { raw: 'null' },
  empty: {},
  nulls: fill(() => null),
  blanks: fill((k) => (LIST_KEYS.includes(k) ? [] : STRING_KEYS.includes(k) ? '' : 0)),
  wrongTypes: fill((k) => (LIST_KEYS.includes(k) ? { a: 1 } : STRING_KEYS.includes(k) ? { a: 1 } : 'abc')),
  listOfNulls: fill((k) => (LIST_KEYS.includes(k) ? [null] : null)),
  listOfEmpties: fill((k) => (LIST_KEYS.includes(k) ? [{}] : null)),
  huge: fill((k) => (LIST_KEYS.includes(k) ? Array(3).fill('x'.repeat(70000)) : STRING_KEYS.includes(k) ? 'x'.repeat(70000)
    : NUMBER_KEYS.includes(k) ? 99999999999 : DATE_KEYS.includes(k) ? '+999999-01-01' : true)),
  nearlyValid: {
    title: 'Sweep', body: '<p>Sweep</p>', name: 'Sweep', english: 'Sweep dept', arabic: 'قسم', email: 'sweep@x.io',
    password: 'Sweep-12345', description: 'd', techTag: 'Nope', targetDays: 0, passingScorePercent: 500,
    status: 1004, role: 9999, type: 9999, id: 'nope', journeyId: 'nope', learnerId: 'nope', packageId: 'nope',
    departmentId: 'nope', seniorId: 'nope', examId: 'nope', message: 'm', seconds: -5, version: -1, language: 'fr',
    timeSpentHours: -3, dueDate: '1999-01-01', showUntil: '2999-01-01', orgWide: false, departmentIds: ['nope', null],
    journeyIds: ['nope', 'nope'], passed: true,
    items: [{ title: 'i', attachments: [{ kind: 9999 }, { kind: 1001 }, null] }, { title: null }],
    units: [{ title: 'u', items: [{ title: null, attachments: [{}] }], quiz: [{ type: 1001, prompt: 'q', options: ['a'], correctOptionIndex: 9 }, { type: null }] }],
    questions: [{ type: 1001, prompt: 'q', options: [], correctOptionIndex: -1 }, { type: 9999 }, null],
    answers: [{ questionId: 'nope' }, { questionId: null, selectedOptionIndex: 99, selectedOptionCode: 99999 }, null],
    marks: [{ questionId: 'nope', awardedPoints: -5 }, null],
  },
};
const FEW_BODIES = ['empty', 'nulls', 'listOfEmpties', 'nearlyValid'];

// ─── Calls ──────────────────────────────────────────────────────────────────────────────────────────────────
async function call(token, method, path, body) {
  const headers = { ...(token ? { Authorization: `Bearer ${token}` } : {}) };
  let payload;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = body && body.raw !== undefined ? body.raw : JSON.stringify(body);
  }
  const res = await fetch(BASE + path, { method, headers, body: payload });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, json, text, type: res.headers.get('content-type') || '' };
}

async function login(email, password) {
  const r = await call(null, 'POST', '/auth/login', { email, password });
  if (r.status !== 200) throw new Error(`login failed for ${email}: ${r.status} ${r.text}`);
  return r.json.token;
}

const problems = new Map();   // key → { count, example }
const accepted = new Map();   // junk that got a 2xx, for review
let total = 0;

function record(map, key, example) {
  const entry = map.get(key) || { count: 0, example };
  entry.count++;
  map.set(key, entry);
}

async function probe(who, token, method, path, bodyName) {
  const r = await call(token, method, path, BODIES[bodyName]);
  total++;
  const label = `${method} ${path.replace(/\?.*/, '?…')}`;
  if (r.status >= 500) {
    record(problems, `${label} → ${r.status} ${r.json?.code ?? ''}`, `${who}, body=${bodyName}, trace=${r.json?.traceId}`);
  } else if (r.status >= 400) {
    // Tomcat refuses encoded slashes and NUL in the path itself, before the application sees the request.
    const refusedByServer = r.status === 400 && /%2F|%2e|%00/i.test(path) && r.type.includes('text/html');
    if (!refusedByServer && (!r.json || typeof r.json.code !== "string" || !r.json.code.startsWith("ERR-") || !r.json.english || !r.json.arabic)) {
      record(problems, `${label} → ${r.status} without an error code`, `${who}, body=${bodyName}: ${r.text.slice(0, 120)}`);
    }
  } else if (bodyName !== 'none' && bodyName !== 'empty' && !['GET'].includes(method)) {
    record(accepted, `${label} ← ${bodyName} (${who})`, `${r.status}`);
  }
}

async function main() {
  const ids = {
    announcement: one('SELECT id FROM announcements ORDER BY created_at DESC LIMIT 1'),
    journey: one('SELECT id FROM journeys ORDER BY created_at LIMIT 1'),
    examJourney: one('SELECT journey_id FROM exams LIMIT 1'),
    package: one('SELECT id FROM packages LIMIT 1'),
    certificate: one("SELECT c.id FROM certificates c JOIN users u ON u.id = c.learner_id WHERE u.email = 'learner1@company.io' LIMIT 1"),
    senior: one("SELECT id FROM users WHERE email = 'senior1@company.io'"),
    learner: one("SELECT id FROM users WHERE email = 'learner1@company.io'"),
    user: one("SELECT id FROM users WHERE email = 'learner2@company.io'"),
    department: one('SELECT id FROM departments LIMIT 1'),
    lj: one("SELECT lj.id FROM learner_journeys lj JOIN users u ON u.id = lj.learner_id WHERE u.email = 'learner1@company.io' AND lj.status IN (1001,1002) LIMIT 1"),
    examLj: one("SELECT lj.id FROM learner_journeys lj JOIN exams e ON e.journey_id = lj.journey_id JOIN users u ON u.id = lj.learner_id WHERE u.email = 'learner1@company.io' LIMIT 1"),
    item: one("SELECT i.id FROM learner_journey_items i JOIN learner_journeys lj ON lj.id = i.learner_journey_id JOIN users u ON u.id = lj.learner_id WHERE u.email = 'learner1@company.io' LIMIT 1"),
    unit: one("SELECT un.id FROM learner_journey_units un JOIN learner_journeys lj ON lj.id = un.learner_journey_id JOIN users u ON u.id = lj.learner_id WHERE u.email = 'learner1@company.io' LIMIT 1"),
    attempt: one('SELECT id FROM exam_attempts LIMIT 1'),
    assignment: one('SELECT id FROM package_assignments LIMIT 1'),
    notification: one("SELECT n.id FROM notifications n JOIN users u ON u.id = n.recipient_id WHERE u.email = 'admin@company.io' LIMIT 1"),
    fileKey: '..%2F..%2Fapplication.properties',
  };

  const roles = [
    ['Admin', await login('admin@company.io', 'admin123'), Object.keys(BODIES)],
    ['Manager', await login('manager@company.io', 'manager123'), FEW_BODIES],
    ['Senior', await login('senior1@company.io', 'senior123'), FEW_BODIES],
    ['Learner', await login('learner1@company.io', 'learner123'), FEW_BODIES],
    ['Anonymous', null, ['nulls']],
  ];

  for (const { method, path } of ENDPOINTS) {
    const variants = new Set([path.replace(/\{(\w+)\}/g, (_, k) => ids[k] ?? 'missing')]);
    if (path.includes('{')) variants.add(path.replace(/\{\w+\}/g, 'missing-id'));
    for (const [who, token, bodies] of roles) {
      for (const real of variants) {
        // Never delete real data, and never sign the sweep's own accounts out of the org.
        if (method === 'DELETE' && !real.includes('missing-id')) continue;
        if (method === 'GET') {
          await probe(who, token, method, real, 'none');
          for (const q of QUERY_JUNK) await probe(who, token, method, real + q, 'none');
          continue;
        }
        for (const bodyName of bodies) await probe(who, token, method, real, bodyName);
      }
    }
  }

  // Hostile file keys and uploads.
  const admin = roles[0][1];
  for (const key of ['..%2F..%2Fpom.xml', '%2e%2e/%2e%2e/pom.xml', 'nope', '%00', 'a/../../b']) {
    await probe('Admin', admin, 'GET', `/files/${key}`, 'none');
  }
  const form = new FormData();
  form.append('other', 'x');
  const upload = await fetch(BASE + '/files', { method: 'POST', headers: { Authorization: `Bearer ${admin}` }, body: form });
  total++;
  if (upload.status >= 500) record(problems, `POST /files (no file part) → ${upload.status}`, await upload.text());
  const exe = new FormData();
  exe.append('file', new Blob(['MZ'], { type: 'application/x-msdownload' }), 'evil.exe');
  const bad = await fetch(BASE + '/files', { method: 'POST', headers: { Authorization: `Bearer ${admin}` }, body: exe });
  total++;
  if (bad.status < 400) record(problems, `POST /files accepted an .exe → ${bad.status}`, await bad.text());

  console.log(`\n${total} requests.`);
  if (accepted.size) {
    console.log(`\nJunk accepted with a 2xx (review — may be fine, may be missing validation):`);
    for (const [k, v] of accepted) console.log(`  ${k}  ×${v.count} → ${v.example}`);
  }
  if (problems.size) {
    console.log(`\nPROBLEMS (${problems.size}):`);
    for (const [k, v] of problems) console.log(`  ✗ ${k}  ×${v.count}  e.g. ${v.example}`);
    process.exit(1);
  }
  console.log('\nNo 500s, and every error carried a code in both languages.');
}

main().catch((e) => { console.error(e); process.exit(1); });
