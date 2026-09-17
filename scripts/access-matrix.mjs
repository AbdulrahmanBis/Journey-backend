// Access matrix — proves the role and department rules hold at the API, one request at a time.
//
//   node scripts/access-matrix.mjs [baseUrl]      default http://localhost:3000
//
// Cleanup talks to MySQL directly. Defaults match a local install; for the Docker stack:
//   MATRIX_DB_PORT=3307 MATRIX_DB_PASSWORD=<MYSQL_ROOT_PASSWORD> node scripts/access-matrix.mjs http://localhost:8080
//
// Runs against a local backend with the seed data (admin@company.io / manager@company.io /
// senior1@ / senior2@ / learner1@ …). It creates what it needs — a second department with its
// own manager, senior and learner, and an HR user — checks every rule as each role, then deletes
// everything it created. Exits 1 if any request gets a status other than the one the rules
// promise.

const BASE = (process.argv[2] || 'http://localhost:3000') + '/api';
const PASSWORD = 'Matrix-Test-1';
const results = [];
const created = { users: [], departments: [], journeys: [], packages: [] };

async function call(token, method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try { json = await res.json(); } catch { /* 204 or non-JSON */ }
  return { status: res.status, json };
}

async function login(email, password) {
  const r = await call(null, 'POST', '/auth/login', { email, password });
  if (r.status !== 200) throw new Error(`login failed for ${email}: ${r.status}`);
  return { token: r.json.token, user: r.json.user };
}

/**
 * Records one expectation.
 *
 *   check(label, who, method, path, expect, undefined?, inspect?)
 *   check(label, who, method, path, body, expect, undefined?, inspect?)
 *
 * `expect` is a status or a list of acceptable statuses; `inspect` returns a problem string or ''.
 */
async function check(label, who, method, path, ...rest) {
  let body;
  if (rest[0] !== null && typeof rest[0] === 'object' && !Array.isArray(rest[0])) {
    body = rest.shift();
  }
  const [expect, , inspect] = rest;
  const r = await call(who.token, method, path, body);
  const ok = [].concat(expect).includes(r.status);
  let note = '';
  if (ok && inspect) {
    const problem = inspect(r.json);
    if (problem) { note = problem; }
  }
  results.push({ label, who: who.name, method, path, expect: [].concat(expect).join('|'), got: r.status, ok: ok && !note, note });
  return r;
}

const ids = (list) => (list || []).map((u) => u.id);

async function main() {
  // ── Seed accounts ─────────────────────────────────────────────────────────────────
  const admin = { name: 'Admin', ...(await login('admin@company.io', 'admin123')) };
  const mgrIt = { name: 'Manager (IT)', ...(await login('manager@company.io', 'manager123')) };
  const sr1 = { name: 'Senior 1 (IT)', ...(await login('senior1@company.io', 'senior123')) };
  const sr2 = { name: 'Senior 2 (IT)', ...(await login('senior2@company.io', 'senior123')) };
  const ln1 = { name: 'Learner 1 (IT)', ...(await login('learner1@company.io', 'learner123')) };
  const itDept = admin.user.department.id;

  // ── Fixtures, created as Admin ──────────────────────────────────────────────────────
  const dept = await call(admin.token, 'POST', '/departments', { english: 'Matrix Sales', arabic: 'مبيعات الاختبار' });
  if (dept.status !== 201) throw new Error('could not create test department: ' + dept.status);
  const salesDept = dept.json.id;
  created.departments.push(salesDept);

  const mkUser = async (name, email, role, departmentId, seniorId) => {
    const r = await call(admin.token, 'POST', '/users', { name, email, password: PASSWORD, role, departmentId, seniorId });
    if (r.status !== 201) throw new Error(`could not create ${email}: ${r.status} ${JSON.stringify(r.json)}`);
    created.users.push(r.json.id);
    return r.json;
  };
  const hrUser = await mkUser('Matrix HR', 'matrix-hr@company.io', 1005, itDept);
  const mgrSalesUser = await mkUser('Matrix Sales Manager', 'matrix-mgr@company.io', 1002, salesDept);
  const srSalesUser = await mkUser('Matrix Sales Senior', 'matrix-sr@company.io', 1003, salesDept);
  const lnSalesUser = await mkUser('Matrix Sales Learner', 'matrix-ln@company.io', 1004, salesDept, srSalesUser.id);

  const hr = { name: 'HR', ...(await login('matrix-hr@company.io', PASSWORD)) };
  const mgrSales = { name: 'Manager (Sales)', ...(await login('matrix-mgr@company.io', PASSWORD)) };
  const srSales = { name: 'Senior (Sales)', ...(await login('matrix-sr@company.io', PASSWORD)) };
  const lnSales = { name: 'Learner (Sales)', ...(await login('matrix-ln@company.io', PASSWORD)) };

  // A journey the sales learner is working on, so their journey data exists.
  const journeys = (await call(admin.token, 'GET', '/journeys')).json;
  const someJourney = journeys[0].id;
  const assigned = await call(srSales.token, 'POST', '/learner-journeys', { journeyId: someJourney, learnerId: lnSalesUser.id });
  results.push({ label: 'Senior assigns a journey to their own learner', who: 'Senior (Sales)', method: 'POST', path: '/learner-journeys', expect: '201', got: assigned.status, ok: assigned.status === 201, note: '' });
  const salesLj = assigned.json?.id;
  const salesItem = assigned.json?.items?.[0]?.progress?.id;

  const ln1Journeys = (await call(ln1.token, 'GET', `/learner-journeys?learnerId=${ln1.user.id}`)).json;
  // Some seeded items have no progress row yet; pick one that does.
  const ln1Lj = ln1Journeys.find((lj) => lj.items.some((i) => i.progress?.id));
  const ln1Item = ln1Lj.items.find((i) => i.progress?.id).progress.id;

  // ── Organisation views ────────────────────────────────────────────────────────────
  for (const who of [admin, hr, mgrIt]) await check('Org metrics', who, 'GET', '/metrics/org', 200);
  for (const who of [sr1, ln1]) await check('Org metrics', who, 'GET', '/metrics/org', 403);
  await check('Org metrics for another department', mgrIt, 'GET', `/metrics/org?departmentId=${salesDept}`, 403);
  await check('Org metrics for another department', hr, 'GET', `/metrics/org?departmentId=${salesDept}`, 200,
    undefined, (j) => (j.learnerCount === 1 ? '' : `expected 1 learner in Sales, got ${j.learnerCount}`));
  await check('Own department metrics', mgrSales, 'GET', '/metrics/org', 200,
    undefined, (j) => (j.learnerCount === 1 ? '' : `Sales manager should see 1 learner, got ${j.learnerCount}`));

  await check('Team tree', mgrIt, 'GET', '/dashboard/manager', 200, undefined,
    (j) => (j.every((s) => s.id !== srSalesUser.id) ? '' : 'IT manager can see the Sales senior'));
  await check('Team tree', mgrSales, 'GET', '/dashboard/manager', 200, undefined,
    (j) => (j.length === 1 && j[0].id === srSalesUser.id ? '' : `Sales manager should see only their senior, got ${j.length}`));
  await check('Team tree', hr, 'GET', '/dashboard/manager', 200, undefined,
    (j) => (j.some((s) => s.id === srSalesUser.id) && j.some((s) => s.id === sr1.user.id) ? '' : 'HR should see seniors of every department'));
  for (const who of [sr1, ln1]) await check('Team tree', who, 'GET', '/dashboard/manager', 403);

  await check("Senior's own team", sr1, 'GET', `/dashboard/senior/${sr1.user.id}`, 200);
  await check("Another senior's team", sr2, 'GET', `/dashboard/senior/${sr1.user.id}`, 403);
  await check("Other department's senior", mgrIt, 'GET', `/dashboard/senior/${srSalesUser.id}`, 403);
  await check("Other department's senior", mgrSales, 'GET', `/metrics/senior/${srSalesUser.id}`, 200);

  // ── People ────────────────────────────────────────────────────────────────────────
  await check('User directory', ln1, 'GET', '/users', 403);
  await check('User directory', mgrIt, 'GET', '/users', 200, undefined,
    (j) => (ids(j).includes(lnSalesUser.id) ? 'IT manager can list a Sales user' : ''));
  await check('User directory', hr, 'GET', '/users', 200, undefined,
    (j) => (ids(j).includes(lnSalesUser.id) && ids(j).includes(ln1.user.id) ? '' : 'HR should list every department'));
  await check('User directory', sr1, 'GET', '/users', 200, undefined,
    (j) => (j.every((u) => u.seniorId === sr1.user.id) ? '' : 'Senior sees people who are not their learners'));

  await check('View a person in another department', mgrIt, 'GET', `/users/${lnSalesUser.id}`, 403);
  await check('View a person in own department', mgrSales, 'GET', `/users/${lnSalesUser.id}`, 200);
  await check('View a colleague', ln1, 'GET', `/users/${lnSalesUser.id}`, 403);
  await check('View yourself', lnSales, 'GET', `/users/${lnSalesUser.id}`, 200);

  // ── Account management & escalation ───────────────────────────────────────────────────────
  await check('Manager creates a user in another department', mgrIt, 'POST', '/users',
    { name: 'x', email: 'matrix-x1@company.io', password: PASSWORD, role: 1004, departmentId: salesDept }, 403);
  await check('Manager grants HR', mgrIt, 'POST', '/users',
    { name: 'x', email: 'matrix-x2@company.io', password: PASSWORD, role: 1005, departmentId: itDept }, 403);
  await check('HR grants Admin', hr, 'POST', '/users',
    { name: 'x', email: 'matrix-x3@company.io', password: PASSWORD, role: 1001, departmentId: itDept }, 403);
  await check('HR edits an Admin account', hr, 'PUT', `/users/${admin.user.id}`, { name: 'Hijacked' }, 403);
  await check('Manager edits another department', mgrIt, 'PUT', `/users/${lnSalesUser.id}`, { name: 'x' }, 403);
  await check('Learner promotes themselves', ln1, 'PUT', `/users/${ln1.user.id}`, { role: 1001 }, 403);
  await check("Senior's learner assigned across departments", admin, 'PUT', `/users/${ln1.user.id}`, { seniorId: srSalesUser.id }, 400);
  await check('Self-service signup', { name: 'anonymous', token: null }, 'POST', '/auth/signup',
    { name: 'x', email: 'matrix-x4@company.io', password: PASSWORD }, 403);

  // ── Departments ───────────────────────────────────────────────────────────────────────
  await check('Everyone can list departments', ln1, 'GET', '/departments', 200);
  await check('Manager creates a department', mgrIt, 'POST', '/departments', { english: 'Nope', arabic: 'لا' }, 403);
  await check('Delete a department that has people', admin, 'DELETE', `/departments/${itDept}`, 409);

  // ── Journeys, progress, notes, exams ─────────────────────────────────────────────────────────
  await check("Read another learner's journeys", lnSales, 'GET', `/learner-journeys?learnerId=${ln1.user.id}`, 403);
  await check("Read own learner's journeys", sr1, 'GET', `/learner-journeys?learnerId=${ln1.user.id}`, 200);
  await check("Read another senior's learner", sr2, 'GET', `/learner-journeys?learnerId=${ln1.user.id}`, 403);
  await check('Open a journey log across departments', mgrSales, 'GET', `/learner-journeys/${ln1Lj.id}`, 403);

  await check("Change someone else's progress", lnSales, 'PATCH', `/learner-journey-items/${ln1Item}/status`, { status: 1002 }, 403);
  await check('Learner changes own progress', lnSales, 'PATCH', `/learner-journey-items/${salesItem}/status`, { status: 1002 }, 200);
  await check("Reviewer changes their learner's progress", srSales, 'PATCH', `/learner-journey-items/${salesItem}/status`, { status: 1003 }, 200);
  await check('Learner overrides journey status', lnSales, 'PATCH', `/learner-journeys/${salesLj}/status`, { status: 1005 }, 403);
  await check('Senior overrides journey status', srSales, 'PATCH', `/learner-journeys/${salesLj}/status`, { status: 1005 }, 403);
  await check("Manager overrides own department's journey", mgrSales, 'PATCH', `/learner-journeys/${salesLj}/status`, { status: 1002 }, 200);

  await check('Note posted under a forged name', lnSales, 'POST', `/learner-journey-items/${salesItem}/notes`,
    { message: 'matrix note', actorId: admin.user.id, actorName: 'Sara Hassan', actorRole: 1001 }, 201, undefined,
    (j) => (j.actorName === 'Matrix Sales Learner' && j.actorRole.code === 1004 ? '' : `note stored as ${j.actorName}`));
  await check("Note on someone else's journey", lnSales, 'POST', `/learner-journey-items/${ln1Item}/notes`, { message: 'x' }, 403);

  await check('Learner creates a journey', ln1, 'POST', '/journeys', { title: 'x', items: [{ title: 'x' }] }, 403);
  const hrJourney = await check('HR creates a journey', hr, 'POST', '/journeys',
    { title: 'Matrix journey', techTag: 'QA', createdById: admin.user.id, createdByName: 'Forged', items: [{ title: 'x' }] }, 201, undefined,
    (j) => (j.createdByName === 'Matrix HR' ? '' : `author stored as ${j.createdByName}`));
  if (hrJourney.json?.id) created.journeys.push(hrJourney.json.id);
  await check('Learner reads the company-wide journey list', ln1, 'GET', '/journeys', 200);

  await check('Learner saves an exam', ln1, 'POST', `/journeys/${someJourney}/exam`, { title: 'x', passingScorePercent: 50, questions: [] }, 403);
  await check('Learner reads an exam answer key', ln1, 'GET', `/journeys/${someJourney}/exam`, 403);
  await check('Staff read an exam answer key', sr1, 'GET', `/journeys/${someJourney}/exam`, 200);
  await check('Learner reads full item content', ln1, 'GET', `/journeys/${someJourney}/items`, 403);
  await check('Staff preview a journey', mgrIt, 'GET', `/journeys/${someJourney}/preview`, 200, undefined,
    (j) => (!j.limited && j.units.every((u) => !u.locked && u.quiz) ? '' : 'staff preview should be complete, quizzes included'));
  await check('Learner previews a journey', lnSales, 'GET', `/journeys/${someJourney}/preview`, 200, undefined,
    (j) => (j.limited && j.units.every((u, i) => u.locked === (i > 0) && !u.quiz && (i === 0 || u.items.every((it) => !it.description && !it.attachments))) && (!j.exam || !j.exam.questions)
      ? '' : 'learner preview leaked content past unit 1 or questions'));
  await check("Learner reads another learner's exam attempt", lnSales, 'GET', `/learner-journeys/${ln1Lj.id}/exam-attempt`, 403);
  await check("Someone else submits a learner's exam", srSales, 'POST', `/learner-journeys/${salesLj}/exam-attempt`, { examId: 'x', answers: [] }, 403);

  // ── Packages ──────────────────────────────────────────────────────────────────────────────
  // The Sales learner already has `someJourney` (salesLj), so the package must reuse it.
  const secondJourney = journeys.find((j) => j.id !== someJourney).id;
  const pkgBody = { title: 'Matrix Package', description: 'x', journeyIds: [someJourney, secondJourney] };
  await check('Learner creates a package', ln1, 'POST', '/packages', pkgBody, 403);
  await check('Package with a journey twice', srSales, 'POST', '/packages', { ...pkgBody, journeyIds: [someJourney, someJourney] }, 400);
  const pkg = await check('Senior creates a package', srSales, 'POST', '/packages', pkgBody, 201, undefined,
    (j) => (j.journeys.map((x) => x.journeyId).join() === [someJourney, secondJourney].join() ? '' : 'journeys not saved in order'));
  if (pkg.json?.id) created.packages.push(pkg.json.id);
  const pkgId = pkg.json?.id;
  await check('Learner reads packages', lnSales, 'GET', '/packages', 200);

  await check('Assign a package in another department', mgrIt, 'POST', '/package-assignments', { packageId: pkgId, learnerId: lnSalesUser.id }, 403);
  await check('Assign a package to a non-learner', hr, 'POST', '/package-assignments', { packageId: pkgId, learnerId: sr1.user.id }, 400);
  const pa = await check('Senior assigns a package to their learner', srSales, 'POST', '/package-assignments', { packageId: pkgId, learnerId: lnSalesUser.id }, 201, undefined,
    (j) => (j.journeys.length === 2 && j.journeys[0].learnerJourneyId === salesLj ? '' : 'existing journey was not reused'));
  const paId = pa.json?.id;
  const newLj = pa.json?.journeys?.[1]?.learnerJourneyId;
  await check('Assign the same package again', srSales, 'POST', '/package-assignments', { packageId: pkgId, learnerId: lnSalesUser.id }, 409);

  await check('Learner reads own packages', lnSales, 'GET', `/package-assignments?learnerId=${lnSalesUser.id}`, 200, undefined,
    (j) => (j.length === 1 ? '' : `expected 1 package, got ${j.length}`));
  await check("Learner reads another learner's packages", ln1, 'GET', `/package-assignments?learnerId=${lnSalesUser.id}`, 403);
  await check('Next journey in package', lnSales, 'GET', `/learner-journeys/${salesLj}/packages`, 200, undefined,
    (j) => (j.length === 1 && j[0].nextLearnerJourneyId === newLj && j[0].position === 1 && j[0].total === 2 ? '' : `unexpected context ${JSON.stringify(j)}`));
  await check('Dashboard shows the package', mgrSales, 'GET', '/dashboard/manager', 200, undefined,
    (j) => (j[0]?.learners?.[0]?.packages?.length === 1 ? '' : 'package missing from the team tree'));

  await check('Delete an assigned package', srSales, 'DELETE', `/packages/${pkgId}`, 409);
  await check('Senior cancels a package', srSales, 'POST', `/package-assignments/${paId}/cancel`, 403);
  await check('Manager cancels a package in another department', mgrIt, 'POST', `/package-assignments/${paId}/cancel`, 403);
  await check('Manager cancels a package', mgrSales, 'POST', `/package-assignments/${paId}/cancel`, 200, undefined,
    (j) => (j.status.code === 1005 && j.journeys[1].status.code === 1005 && j.journeys[0].status.code !== 1005
      ? '' : `expected only the journey the package created to be cancelled: ${j.journeys.map((x) => x.status.code)}`));
  await check('Re-assign a journey after it was cancelled', srSales, 'POST', '/learner-journeys', { journeyId: secondJourney, learnerId: lnSalesUser.id }, 201);
  await check('Assign an active journey twice', srSales, 'POST', '/learner-journeys', { journeyId: secondJourney, learnerId: lnSalesUser.id }, 409);

  // ── Catalog & self-enrollment ─────────────────────────────────────────────────────────────
  // By now the Sales learner holds someJourney and secondJourney; the package was cancelled.
  const thirdJourney = journeys.find((j) => j.id !== someJourney && j.id !== secondJourney).id;
  await check('Learner browses the catalog', lnSales, 'GET', '/catalog', 200, undefined,
    (j) => (j.reviewerName === 'Matrix Sales Senior' && j.statuses.length === 4 && j.entries.every((e) => e.mine) ? '' : 'learner view incomplete'));
  await check('Staff browse the catalog', mgrIt, 'GET', '/catalog', 200, undefined,
    (j) => (j.statuses.length === 0 && j.entries.every((e) => !e.mine) ? '' : 'staff should not get a learner status'));
  await check('Catalog search', lnSales, 'GET', '/catalog?q=' + encodeURIComponent('Matrix Package'), 200, undefined,
    (j) => (j.entries.some((e) => e.id === pkgId) ? '' : 'package not found by title'));
  await check('Staff enroll themselves', srSales, 'POST', '/catalog/enroll', { type: 1001, id: thirdJourney }, 403);
  await check('Enroll in an unknown type', lnSales, 'POST', '/catalog/enroll', { type: 9, id: thirdJourney }, 400);
  await check('Enroll in a journey already held', lnSales, 'POST', '/catalog/enroll', { type: 1001, id: someJourney }, 409);
  const enrolled = await check('Learner enrolls in a journey', lnSales, 'POST', '/catalog/enroll', { type: 1001, id: thirdJourney }, 201);
  const selfLj = enrolled.json?.learnerJourneyId;
  await check('Self-enrollment is reviewed by the senior', lnSales, 'GET', `/learner-journeys/${selfLj}`, 200, undefined,
    (j) => (j.selfEnrolled === true && j.assignedById === srSalesUser.id ? '' : `assigner ${j.assignedById}, selfEnrolled ${j.selfEnrolled}`));
  await check('Status filter shows it in progress', lnSales, 'GET', '/catalog?status=1002', 200, undefined,
    (j) => (j.entries.some((e) => e.id === thirdJourney) ? '' : 'new enrollment not listed as in progress'));
  await check('Enroll again in a cancelled package', lnSales, 'POST', '/catalog/enroll', { type: 1002, id: pkgId }, 201);
  // Notifications are dispatched asynchronously after commit, so give them a moment to land.
  for (let attempt = 0; attempt < 10; attempt++) {
    const r = await call(srSales.token, 'GET', '/notifications?size=50');
    if (r.json?.items?.some((n) => n.templateId === 'self-enrolled')) break;
    await new Promise((done) => setTimeout(done, 300));
  }
  await check('Senior is told about the enrollment', srSales, 'GET', '/notifications?size=50', 200, undefined,
    (j) => (j.items.some((n) => n.templateId === 'self-enrolled') ? ''
      : `no self-enrolled notification for the reviewer; has: ${j.items.map((n) => n.templateId).join(', ') || 'nothing'}`));

  // ── Team dashboard & due dates ────────────────────────────────────────────────────────────
  const isoIn = (days) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
  await check('Learner opens the team dashboard', lnSales, 'GET', '/team', 403);
  await check('Senior sees only their learners', srSales, 'GET', '/team', 200, undefined,
    (j) => (j.learners.length === 1 && j.learners[0].id === lnSalesUser.id && j.seniors.length === 0 ? '' : `got ${j.learners.map((l) => l.name)}`));
  await check('Manager team excludes other departments', mgrIt, 'GET', '/team', 200, undefined,
    (j) => (j.learners.every((l) => l.id !== lnSalesUser.id) ? '' : 'IT manager sees a Sales learner'));
  await check('Manager asks for another department', mgrIt, 'GET', `/team?departmentId=${salesDept}`, 403);
  await check('HR filters the team by department', hr, 'GET', `/team?departmentId=${salesDept}`, 200, undefined,
    (j) => (j.learners.length === 1 && j.learners[0].id === lnSalesUser.id ? '' : `expected the Sales learner, got ${j.learners.length}`));
  await check("Learner's own snapshot", lnSales, 'GET', `/team/learners/${lnSalesUser.id}`, 200);
  await check("Another department's learner snapshot", mgrIt, 'GET', `/team/learners/${lnSalesUser.id}`, 403);

  await check('Learner moves their own due date', lnSales, 'PATCH', `/learner-journeys/${salesLj}/due-date`, { dueDate: isoIn(30) }, 403);
  await check('Due date in the past', srSales, 'PATCH', `/learner-journeys/${salesLj}/due-date`, { dueDate: isoIn(-2) }, 400);
  await check('Senior sets a due date', srSales, 'PATCH', `/learner-journeys/${salesLj}/due-date`, { dueDate: isoIn(2) }, 200, undefined,
    (j) => (j.dueDate === isoIn(2) ? '' : `dueDate ${j.dueDate}`));
  await check('Due soon shows on the team dashboard', srSales, 'GET', '/team', 200, undefined,
    (j) => (j.learners[0].nextDueDate === isoIn(2) ? '' : `nextDueDate ${j.learners[0].nextDueDate}`));
  await check("Other department's due date", mgrIt, 'PATCH', `/learner-journeys/${salesLj}/due-date`, { dueDate: null }, 403);
  await check('Assign with a past due date', srSales, 'POST', '/learner-journeys', { journeyId: thirdJourney, learnerId: lnSalesUser.id, dueDate: isoIn(-1) }, [400, 409]);

  // ── Intro guide ───────────────────────────────────────────────────────────────────────────
  await check('Dismiss the intro with a bad version', lnSales, 'PATCH', '/users/me/intro', { version: 0 }, 400);
  await check('Dismiss the intro for good', lnSales, 'PATCH', '/users/me/intro', { version: 1 }, 204);
  await check('Dismissal is on the account', lnSales, 'GET', `/users/${lnSalesUser.id}`, 200, undefined,
    (j) => (j.introSeenVersion === 1 ? '' : `introSeenVersion ${j.introSeenVersion}`));

  // ── Announcements ─────────────────────────────────────────────────────────────────────────
  const waitFor = async (who, test) => {
    for (let attempt = 0; attempt < 10; attempt++) {
      const r = await call(who.token, 'GET', '/notifications?size=50');
      if (test(r.json?.items ?? [])) return;
      await new Promise((done) => setTimeout(done, 300));
    }
  };
  const post = (title, extra = {}) => ({ title, body: '<p>Matrix announcement body</p>', ...extra });
  const annLink = (id) => `/dashboard?announcement=${id}`;
  await check('Learner posts an announcement', lnSales, 'POST', '/announcements', post('Matrix learner post'), 403);
  await check('Senior posts an announcement', srSales, 'POST', '/announcements', post('Matrix senior post'), 403);
  const annSales = (await check('Manager posts to their department', mgrSales, 'POST', '/announcements', post('Matrix Sales update'), 201, undefined,
    (j) => (j.departments.length === 1 && j.departments[0].id === salesDept && !j.orgWide ? '' : 'expected the Sales department only'))).json?.id;
  await check('Manager posts to another department', mgrIt, 'POST', '/announcements', post('Matrix IT to Sales', { departmentIds: [salesDept] }), 403);
  await check('Manager posts company-wide', mgrIt, 'POST', '/announcements', post('Matrix IT to all', { orgWide: true }), 403);
  const annOrg = (await check('HR posts company-wide', hr, 'POST', '/announcements', post('Matrix company news', { orgWide: true }), 201)).json?.id;
  await check('HR posts without an audience', hr, 'POST', '/announcements', post('Matrix nobody'), 400);
  await check('HR posts to an unknown department', hr, 'POST', '/announcements', post('Matrix ghost', { departmentIds: ['dep-none'] }), 400);
  await check('Empty announcement', hr, 'POST', '/announcements', { title: 'Matrix empty', body: '<p><br></p>', orgWide: true }, 400);
  await check('Show-until in the past', hr, 'POST', '/announcements', post('Matrix past', { orgWide: true, showUntil: isoIn(-1) }), 400);

  await check('Sales learner dashboard', lnSales, 'GET', '/announcements/active', 200, undefined,
    (j) => (ids(j).includes(annSales) && ids(j).includes(annOrg) ? '' : 'should show the Sales and the company-wide announcement'));
  await check('IT learner dashboard', ln1, 'GET', '/announcements/active', 200, undefined,
    (j) => (ids(j).includes(annOrg) && !ids(j).includes(annSales) ? '' : 'should show only the company-wide announcement'));
  await check('IT learner opens a Sales announcement', ln1, 'GET', `/announcements/${annSales}`, 403);
  await waitFor(lnSales, (items) => items.some((n) => n.link === annLink(annSales)));
  await check('Sales learner is notified', lnSales, 'GET', '/notifications?size=50', 200, undefined,
    (j) => (j.items.some((n) => n.link === annLink(annSales)) ? '' : 'no announcement notification'));
  await check('Author is not notified of their own post', mgrSales, 'GET', '/notifications?size=50', 200, undefined,
    (j) => (j.items.some((n) => n.link === annLink(annSales)) ? 'author was notified' : ''));

  for (const who of [lnSales, srSales]) await check('Announcements page', who, 'GET', '/announcements', 403);
  await check('Announcements page', mgrIt, 'GET', '/announcements', 200, undefined,
    (j) => (ids(j).includes(annOrg) && !ids(j).includes(annSales) ? '' : 'IT manager should see company-wide but not Sales'));
  await check('Announcements page for another department', mgrIt, 'GET', `/announcements?departmentId=${salesDept}`, 403);
  await check('Announcements page for another department', hr, 'GET', `/announcements?departmentId=${salesDept}`, 200, undefined,
    (j) => (ids(j).includes(annSales) ? '' : 'HR should see the Sales announcement'));

  await check("Manager edits another department's announcement", mgrIt, 'PUT', `/announcements/${annSales}`, post('Matrix hijack'), 403);
  await check('Manager edits an HR announcement', mgrSales, 'PUT', `/announcements/${annOrg}`, post('Matrix hijack'), 403);
  await check('Manager edits their own announcement', mgrSales, 'PUT', `/announcements/${annSales}`, post('Matrix Sales update (edited)'), 200, undefined,
    (j) => (j.title === 'Matrix Sales update (edited)' && j.canEdit ? '' : 'edit not applied'));
  await check('Dismiss an announcement not addressed to you', ln1, 'POST', `/announcements/${annSales}/dismiss`, 403);
  await check('Learner dismisses an announcement', lnSales, 'POST', `/announcements/${annOrg}/dismiss`, 204);
  await check('Dismissed one leaves the dashboard', lnSales, 'GET', '/announcements/active', 200, undefined,
    (j) => (!ids(j).includes(annOrg) && ids(j).includes(annSales) ? '' : 'dismissal not applied to this learner only'));
  await check('Dismissed one still opens from its notification', lnSales, 'GET', `/announcements/${annOrg}`, 200);
  await check("Manager deletes another department's announcement", mgrIt, 'DELETE', `/announcements/${annSales}`, 403);
  await check('Manager deletes their own announcement', mgrSales, 'DELETE', `/announcements/${annSales}`, 204);
  await waitFor(lnSales, (items) => !items.some((n) => n.link === annLink(annSales)));
  await check('Deleting withdraws its notifications', lnSales, 'GET', '/notifications?size=50', 200, undefined,
    (j) => (j.items.some((n) => n.link === annLink(annSales)) ? 'notification still there' : ''));
  await check('HR deletes any announcement', hr, 'DELETE', `/announcements/${annOrg}`, 204);

  // ── Certificates ──────────────────────────────────────────────────────────────────────────
  await check('Learner lists own certificates', lnSales, 'GET', '/certificates', 200, undefined,
    (j) => (Array.isArray(j) ? '' : 'expected a list'));
  await check("Learner lists another learner's certificates", ln1, 'GET', `/certificates?learnerId=${lnSalesUser.id}`, 403);
  await check("Manager lists own department learner's certificates", mgrSales, 'GET', `/certificates?learnerId=${lnSalesUser.id}`, 200);
  await check("Manager lists another department's learner's certificates", mgrIt, 'GET', `/certificates?learnerId=${lnSalesUser.id}`, 403);
  await check("HR lists any learner's certificates", hr, 'GET', `/certificates?learnerId=${lnSalesUser.id}`, 200);
  const ln1Certs = (await call(ln1.token, 'GET', '/certificates')).json ?? [];
  if (ln1Certs.length) {
    await check("Learner opens another learner's certificate", lnSales, 'GET', `/certificates/${ln1Certs[0].id}`, 403);
    await check("Manager opens another department's certificate", mgrSales, 'GET', `/certificates/${ln1Certs[0].id}`, 403);
  }

  // ── Email check (Admin) ───────────────────────────────────────────────────────────────────
  for (const who of [ln1, mgrIt, hr]) await check('Mail status', who, 'GET', '/notifications/mail-status', 403);
  await check('Mail status', admin, 'GET', '/notifications/mail-status', 200, undefined,
    (j) => (typeof j.enabled === 'boolean' && typeof j.configured === 'boolean' && !('password' in j) ? '' : 'unexpected shape'));
  await check('Send a test email', hr, 'POST', '/notifications/test-email', 403);

  // ── Getting started ───────────────────────────────────────────────────────────────────────
  await check('New manager checklist', mgrSales, 'GET', '/getting-started', 200, undefined,
    (j) => (j.role.code === 1002 && j.steps.length === 5 && j.steps.find((s) => s.key === 'SENIORS')?.done && !j.dismissed ? '' : JSON.stringify(j)));
  await check('Senior checklist', srSales, 'GET', '/getting-started', 200, undefined,
    (j) => (j.steps.length === 4 && j.steps.find((s) => s.key === 'HAS_LEARNERS')?.done ? '' : JSON.stringify(j)));
  await check('Learner has no checklist', lnSales, 'GET', '/getting-started', 200, undefined,
    (j) => (j.steps.length === 0 ? '' : 'learner got steps'));
  await check('Manager hides the checklist', mgrSales, 'POST', '/getting-started/dismiss', 204);
  await check('Hidden checklist stays hidden', mgrSales, 'GET', '/getting-started', 200, undefined,
    (j) => (j.dismissed ? '' : 'not dismissed'));

  // ── Report ────────────────────────────────────────────────────────────────────────────
  const failed = results.filter((r) => !r.ok);
  for (const r of results) {
    const mark = r.ok ? '✔' : '✘';
    console.log(`${mark} ${String(r.got).padEnd(3)} (want ${r.expect.padEnd(3)}) ${r.who.padEnd(17)} ${r.label}${r.note ? '  ← ' + r.note : ''}`);
  }
  console.log(`\naccess matrix: ${results.length - failed.length}/${results.length} as expected`);
  return failed.length;
}

async function cleanup() {
  try {
    const admin = await login('admin@company.io', 'admin123');
    for (const id of created.journeys) await call(admin.token, 'DELETE', `/journeys/${id}`);
    // Learner journeys, notes and notifications of the test users go first, then the users.
    const { execFileSync } = await import('node:child_process');
    const mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
    const list = created.users.map((id) => `'${id}'`).join(',');
    if (list) {
      const sql = `
        DELETE FROM journey_notes WHERE learner_journey_item_id IN (SELECT id FROM learner_journey_items WHERE learner_journey_id IN (SELECT id FROM learner_journeys WHERE learner_id IN (${list})));
        DELETE FROM learner_journey_items WHERE learner_journey_id IN (SELECT id FROM learner_journeys WHERE learner_id IN (${list}));
        DELETE FROM learner_journeys WHERE learner_id IN (${list});
        DELETE FROM notifications WHERE recipient_id IN (${list}) OR variables_json LIKE '%Matrix%';
        DELETE FROM announcements WHERE author_id IN (${list});
        UPDATE users SET senior_id = NULL WHERE senior_id IN (${list});
        DELETE FROM users WHERE id IN (${list});`;
      const env = process.env;
      execFileSync(env.MATRIX_MYSQL_BIN || mysql, [
        '-u', env.MATRIX_DB_USER || 'root', `-p${env.MATRIX_DB_PASSWORD ?? '12345'}`,
        '-h', env.MATRIX_DB_HOST || '127.0.0.1', '-P', env.MATRIX_DB_PORT || '3306',
        'journey_db', '-e', sql,
      ], { stdio: 'pipe' });
    }
    // Deleting the users cascaded their package assignments, so the packages are deletable now.
    for (const id of created.packages) await call(admin.token, 'DELETE', `/packages/${id}`);
    for (const id of created.departments) await call(admin.token, 'DELETE', `/departments/${id}`);
    console.log(`cleaned up: ${created.users.length} users, ${created.departments.length} department(s), ${created.journeys.length} journey(s), ${created.packages.length} package(s)`);
  } catch (e) {
    console.error('cleanup failed — remove matrix-* users and "Matrix Sales" by hand:', e.message);
  }
}

let failures = 1;
try {
  failures = await main();
} catch (e) {
  console.error('matrix aborted:', e.message);
} finally {
  await cleanup();
}
process.exit(failures === 0 ? 0 : 1);
