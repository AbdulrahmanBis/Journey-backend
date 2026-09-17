// Units flow — the unit-based journey log end to end, one step at a time: authoring units and quizzes,
// opening and completing items, time tracking, the quiz, sending for review, sending back, resubmitting,
// completing, and editing the journey without losing learners' progress.
//
//   node scripts/units-flow.mjs [baseUrl]      default http://localhost:3000
//
// Uses the seed accounts (admin@, senior1@) and creates a throwaway learner and journey, which it deletes
// afterwards. Cleanup and progress checks talk to MySQL directly; configure like the access matrix:
//   MATRIX_DB_PORT=3307 MATRIX_DB_PASSWORD=<MYSQL_ROOT_PASSWORD> node scripts/units-flow.mjs http://localhost:8080
// Exits 1 if any step fails.
import { execFileSync } from 'node:child_process';

const B = (process.argv[2] || 'http://localhost:3000') + '/api';
const env = process.env;
const MYSQL = env.MATRIX_MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
const sql = (q) => execFileSync(MYSQL, [
  '-u', env.MATRIX_DB_USER || 'root', `-p${env.MATRIX_DB_PASSWORD ?? '12345'}`,
  '-h', env.MATRIX_DB_HOST || '127.0.0.1', '-P', env.MATRIX_DB_PORT || '3306',
  '-N', 'journey_db', '-e', q,
], { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();

let failures = 0;
const ok = (label, cond, detail = '') => { console.log(`${cond ? '✔' : '✘'} ${label}${cond ? '' : '  ← ' + detail}`); if (!cond) failures++; };
async function call(token, method, path, body) {
  const r = await fetch(B + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
  let json = null; try { json = await r.json(); } catch {}
  return { status: r.status, json };
}
const login = async (email, password) => (await call(null, 'POST', '/auth/login', { email, password })).json.token;
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const state = {};
try {
  const admin = await login('admin@company.io', 'admin123');
  const sr = await login('senior1@company.io', 'senior123');
  const learner = (await call(admin, 'POST', '/users', { name: 'Tmp Units Learner', email: 'tmp-units@company.io', password: 'Tmp-Test-1', role: 1004, departmentId: 'dep-1', seniorId: 'u-sr-1' })).json;
  state.learnerId = learner.id;
  const ln = await login('tmp-units@company.io', 'Tmp-Test-1');

  // ── Authoring ─────────────────────────────────────────────────────────────
  const created = await call(sr, 'POST', '/journeys', {
    title: 'Tmp Units Journey', description: 'x', techTag: 'General', targetDays: null,
    units: [
      { title: 'Basics', description: 'first', items: [{ title: 'Read A', description: '<p>A</p>' }, { title: 'Read B', description: '<p>B</p>' }],
        quiz: [
          { type: 1001, prompt: 'Pick two', options: ['one', 'two', 'three'], correctOptionIndex: 1002 },
          { type: 1002, prompt: 'Is it yes?', options: [], correctBoolAnswer: true },
        ] },
      { title: 'Deeper', items: [{ title: 'Read C', description: '<p>C</p>' }] },
    ],
  });
  ok('Senior creates a journey with units', created.status === 201, JSON.stringify(created.json));
  state.journeyId = created.json.id;
  const openQuiz = await call(sr, 'POST', '/journeys', { title: 'x', techTag: 'General', units: [{ title: 'u', items: [{ title: 'i' }], quiz: [{ type: 1003, prompt: 'Explain' }] }] });
  ok('Open questions are refused in unit quizzes', openQuiz.status === 400, openQuiz.status);
  const units = (await call(sr, 'GET', `/journeys/${state.journeyId}/units`)).json;
  ok('Units come back in order with items and quiz', units.length === 2 && units[0].items.length === 2 && units[0].quiz.length === 2 && units[1].items.length === 1, JSON.stringify(units).slice(0, 200));
  ok('Learners cannot read quiz answers through the authoring endpoint', (await call(ln, 'GET', `/journeys/${state.journeyId}/units`)).status === 403);

  // ── Assign and outline ────────────────────────────────────────────────────
  const lj = (await call(sr, 'POST', '/learner-journeys', { journeyId: state.journeyId, learnerId: learner.id })).json;
  let outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('Outline has both units, all new', outline.units.length === 2 && outline.units.every((u) => u.status.code === 1001), JSON.stringify(outline.units?.map((u) => u.status)));
  ok('Outline has no item content', !JSON.stringify(outline).includes('<p>A</p>'));
  const [u1, u2] = outline.units;
  const [i1, i2] = u1.items;
  const i3 = u2.items[0];

  // ── Items ─────────────────────────────────────────────────────────────────
  ok('Senior cannot open a learner\'s item', (await call(sr, 'POST', `/learner-journey-items/${i1.progressId}/open`)).status === 403);
  const opened = await call(ln, 'POST', `/learner-journey-items/${i1.progressId}/open`);
  ok('Opening an item marks it in progress', opened.json?.status?.code === 1002, JSON.stringify(opened.json));
  await call(ln, 'POST', `/learner-journey-items/${i1.progressId}/time`, { seconds: 500 });
  const content = (await call(ln, 'GET', `/learner-journey-items/${i1.progressId}`)).json;
  ok('Item content loads with neighbours', content.description === '<p>A</p>' && content.nextProgressId === i2.progressId && !content.previousProgressId, JSON.stringify(content).slice(0, 200));
  ok('Time is capped per heartbeat', Math.abs(content.timeSpentHours - 120 / 3600) < 0.001, content.timeSpentHours);
  ok('Reviewer can read the item', (await call(sr, 'GET', `/learner-journey-items/${i1.progressId}`)).status === 200);
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('Unit and journey move to in progress', outline.units[0].status.code === 1002 && outline.status.code === 1002, `${outline.units[0].status.code} ${outline.status.code}`);

  await call(ln, 'POST', `/learner-journey-items/${i1.progressId}/complete`);
  await call(ln, 'POST', `/learner-journey-items/${i2.progressId}/complete`);
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('Items done but quiz pending: not sent for review', outline.units[0].status.code === 1002 && !outline.units[0].readyForReview, JSON.stringify(outline.units[0].status));

  // ── Quiz ──────────────────────────────────────────────────────────────────
  const quiz = (await call(ln, 'GET', `/learner-journey-units/${u1.learnerUnitId}/quiz`)).json;
  ok('Quiz hides answers before submitting', quiz.questions.length === 2 && quiz.questions.every((q) => q.correctOptionIndex == null && q.correctBoolAnswer == null), JSON.stringify(quiz.questions));
  ok('Unanswered quiz is refused', (await call(ln, 'POST', `/learner-journey-units/${u1.learnerUnitId}/quiz`, { answers: [{ questionId: quiz.questions[0].id, selectedOptionIndex: 1002 }] })).status === 400);
  const graded = (await call(ln, 'POST', `/learner-journey-units/${u1.learnerUnitId}/quiz`, { answers: [
    { questionId: quiz.questions[0].id, selectedOptionIndex: 1002 },
    { questionId: quiz.questions[1].id, boolAnswer: false },
  ] })).json;
  ok('Quiz graded instantly: 50%, with feedback', graded.scorePercent === 50 && graded.questions[0].correct === true && graded.questions[1].correct === false && graded.questions[1].correctBoolAnswer === true, JSON.stringify(graded));
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('Unit goes to waiting for review once the quiz is answered', outline.units[0].status.code === 1003, outline.units[0].status.code);

  await wait(1500);
  const team = (await call(sr, 'GET', '/team')).json;
  ok('Team dashboard shows the unit waiting for review', team.attention.some((a) => a.type.code === 1003 && a.itemTitle === 'Basics' && a.learnerId === learner.id), JSON.stringify(team.attention.filter((a) => a.learnerId === learner.id)));
  const srNotes = (await call(sr, 'GET', '/notifications?size=30')).json;
  ok('Reviewer is notified', srNotes.items.some((n) => n.templateId === 'unit-submitted'), srNotes.items.map((n) => n.templateId).join());

  // ── Review ────────────────────────────────────────────────────────────────
  ok('Learner cannot review their own unit', (await call(ln, 'PATCH', `/learner-journey-units/${u1.learnerUnitId}/status`, { status: 1004 })).status === 403);
  ok('Reviewer sends it back', (await call(sr, 'PATCH', `/learner-journey-units/${u1.learnerUnitId}/status`, { status: 1002 })).json?.status?.code === 1002);
  ok('Reviewer leaves unit feedback', (await call(sr, 'POST', `/learner-journey-units/${u1.learnerUnitId}/notes`, { message: 'Retake the quiz' })).status === 201);
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('Sent back: in progress, feedback visible, still ready to resubmit', outline.units[0].status.code === 1002 && outline.units[0].notes.length === 1 && outline.units[0].readyForReview, JSON.stringify(outline.units[0]).slice(0, 300));
  ok('Learner resubmits', (await call(ln, 'POST', `/learner-journey-units/${u1.learnerUnitId}/submit`)).json?.status?.code === 1003);
  ok('Resubmitting again is a conflict', (await call(ln, 'POST', `/learner-journey-units/${u1.learnerUnitId}/submit`)).status === 409);
  await call(sr, 'PATCH', `/learner-journey-units/${u1.learnerUnitId}/status`, { status: 1004 });

  await call(ln, 'POST', `/learner-journey-items/${i3.progressId}/open`);
  await call(ln, 'POST', `/learner-journey-items/${i3.progressId}/complete`);
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('A unit without a quiz goes to review when its items are done', outline.units[1].status.code === 1003, outline.units[1].status.code);
  await call(sr, 'PATCH', `/learner-journey-units/${u2.learnerUnitId}/status`, { status: 1004 });
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('All units completed (no exam): journey completed', outline.status.code === 1004 && outline.percentComplete === 100, `${outline.status.code} ${outline.percentComplete}`);


  // ── Certificates ─────────────────────────────────────────────────────────
  const certs = (await call(ln, 'GET', '/certificates')).json;
  const cert = certs.find((c) => c.learnerJourneyId === lj.id);
  ok('Completing the journey issues a certificate', !!cert && /^JRN-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(cert.code) && cert.learnerName === 'Tmp Units Learner', JSON.stringify(certs));
  ok('The reviewer can open it', (await call(sr, 'GET', `/certificates/${cert?.id}`)).status === 200);
  await wait(800);
  const lnNotes = (await call(ln, 'GET', '/notifications?size=30')).json;
  ok('Learner is told about the certificate', lnNotes.items.some((n) => n.templateId === 'certificate-issued' && n.link === `/certificates/${cert?.id}`), lnNotes.items.map((n) => n.templateId).join());
  await call(admin, 'PATCH', `/learner-journeys/${lj.id}/status`, { status: 1002 });
  ok('Reopening the journey removes the certificate', !(await call(ln, 'GET', '/certificates')).json.some((c) => c.learnerJourneyId === lj.id));
  await call(admin, 'PATCH', `/learner-journeys/${lj.id}/status`, { status: 1004 });
  const reissued = (await call(ln, 'GET', '/certificates')).json.find((c) => c.learnerJourneyId === lj.id);
  ok('Completing it again issues a new one', !!reissued && reissued.code !== cert?.code, JSON.stringify(reissued));

  // ── Editing keeps progress ─────────────────────────────────────────────────
  const before = sql(`SELECT COUNT(*) FROM learner_journey_items WHERE learner_journey_id='${lj.id}' AND status=1004`);
  const edited = await call(sr, 'PUT', `/journeys/${state.journeyId}`, {
    title: 'Tmp Units Journey (edited)', techTag: 'General',
    units: [
      { id: units[0].id, title: 'Basics renamed', items: units[0].items.map((i) => ({ id: i.id, title: i.title, description: i.description })),
        quiz: units[0].quiz.map((q) => ({ id: q.id, type: q.type.code, prompt: q.prompt, options: q.options, correctOptionIndex: q.correctOptionIndex, correctBoolAnswer: q.correctBoolAnswer })) },
      { id: units[1].id, title: 'Deeper', items: [...units[1].items.map((i) => ({ id: i.id, title: i.title, description: i.description })), { title: 'Read D (new)', description: '<p>D</p>' }] },
    ],
  });
  ok('Editing the journey succeeds', edited.status === 200, JSON.stringify(edited.json));
  const after = sql(`SELECT COUNT(*) FROM learner_journey_items WHERE learner_journey_id='${lj.id}' AND status=1004`);
  ok('Editing the journey keeps learner progress', before === '3' && after === '3', `before ${before} after ${after}`);
  outline = (await call(ln, 'GET', `/learner-journeys/${lj.id}/outline`)).json;
  ok('New item appears as new, renamed unit keeps its status', outline.units[1].items.length === 2 && outline.units[1].items[1].status.code === 1001 && outline.units[0].title === 'Basics renamed' && outline.units[0].status.code === 1004,
    JSON.stringify(outline.units.map((u) => [u.title, u.status.code, u.items.map((i) => i.status.code)])));
  const flat = await call(sr, 'PUT', `/journeys/${state.journeyId}`, { title: 'Tmp flat', techTag: 'General', items: [{ id: units[0].items[0].id, title: 'Only A' }] });
  ok('Old flat item list still saves (collapses to one unit)', flat.status === 200, JSON.stringify(flat.json));
  const kept = sql(`SELECT status FROM learner_journey_items lji WHERE learner_journey_id='${lj.id}' AND journey_item_id='${units[0].items[0].id}'`);
  ok('…and the kept item keeps its progress', kept === '1004', kept);
} catch (e) {
  console.error('flow aborted:', e.message);
  failures++;
} finally {
  if (state.journeyId) sql(`DELETE FROM journeys WHERE id='${state.journeyId}'`);
  sql(`DELETE FROM journeys WHERE title='x' AND created_by_name='Ali Tarek'`);
  if (state.learnerId) {
    sql(`DELETE FROM notifications WHERE recipient_id='${state.learnerId}' OR variables_json LIKE '%Tmp Units%'`);
    sql(`DELETE FROM users WHERE id='${state.learnerId}'`);
  }
  console.log(`\nunits flow: ${failures === 0 ? 'all passed' : failures + ' failed'}`);
  process.exit(failures ? 1 : 0);
}
