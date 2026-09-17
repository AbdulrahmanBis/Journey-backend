// Demo data — a realistic organisation with three months of onboarding history.
//
//   node scripts/seed-demo.mjs --yes [baseUrl]      default http://localhost:3000
//
// DESTRUCTIVE: deletes every journey, package, assignment, exam, note, announcement, certificate and notification first. Users are
// kept (and extended to the roster below); departments other than those below are removed.
//
// Everything is created through the API as the person who would really do it — authors write journeys,
// seniors assign and review, learners read, answer quizzes and sit exams — so every status is one the app
// itself produces. Only timestamps are then moved into the past with SQL, so the history spans ~3 months.
//
// MySQL access is configured like the access matrix (MATRIX_DB_HOST / _PORT / _USER / _PASSWORD).
// New accounts use the seed convention: learner123, senior123, manager123, hr123.

import { execFileSync } from 'node:child_process';

if (!process.argv.includes('--yes')) {
  console.error('This deletes all journeys, assignments, packages, exams, notes and notifications. Re-run with --yes.');
  process.exit(1);
}
const BASE = (process.argv.find((a) => a.startsWith('http')) || 'http://localhost:3000') + '/api';
const env = process.env;
const MYSQL = env.MATRIX_MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_ARGS = ['-u', env.MATRIX_DB_USER || 'root', `-p${env.MATRIX_DB_PASSWORD ?? '12345'}`,
  '-h', env.MATRIX_DB_HOST || '127.0.0.1', '-P', env.MATRIX_DB_PORT || '3306', '--default-character-set=utf8mb4', 'journey_db'];
const sql = (q) => execFileSync(MYSQL, [...DB_ARGS, '-N', '-e', q], { stdio: ['ignore', 'pipe', 'pipe'] }).toString().replace(/\r\n/g, '\n').trim();

// ── Deterministic randomness, so a re-run produces the same organisation ─────────────
let seed = 20260917;
const rand = () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const between = (a, b) => a + (b - a) * rand();
const DAY = 86_400_000;
const NOW = Date.now();
const daysAgo = (d) => new Date(NOW - d * DAY);
const pad = (n) => String(n).padStart(2, '0');
const dt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
const dateOnly = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
/** Moves a moment into working hours (09:00–17:30) on the same day. */
const workHours = (d) => { const x = new Date(d); x.setHours(9 + Math.floor(rand() * 8), Math.floor(rand() * 60), Math.floor(rand() * 60)); return x; };
const q = (s) => (s == null ? 'NULL' : `'${String(s).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`);

// ── API ─────────────────────────────────────────────────────────────────────────────
async function call(token, method, path, body, form) {
  const res = await fetch(BASE + path, {
    method,
    headers: { ...(form ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  let json = null; try { json = await res.json(); } catch { /* empty */ }
  if (res.status >= 300) throw new Error(`${method} ${path} → ${res.status} ${JSON.stringify(json)}`);
  return json;
}
const tokens = {};
async function as(email) {
  if (!tokens[email]) {
    const password = { 'admin@company.io': 'admin123', 'manager@company.io': 'manager123', 'senior1@company.io': 'senior123',
      'senior2@company.io': 'senior123', 'learner1@company.io': 'learner123', 'learner2@company.io': 'learner123',
      'learner3@company.io': 'learner123' }[email] ?? PASSWORDS[email];
    tokens[email] = (await call(null, 'POST', '/auth/login', { email, password })).token;
  }
  return tokens[email];
}
const PASSWORDS = {};

// ════════════════════════════════════════ Roster ════════════════════════════════════════
const R = { ADMIN: 1001, MANAGER: 1002, SENIOR: 1003, LEARNER: 1004, HR: 1005 };
const DEPARTMENTS = {
  it: { english: 'Information Technology', arabic: 'تقنية المعلومات', id: 'dep-1' },
  sales: { english: 'Sales', arabic: 'المبيعات' },
  hr: { english: 'Human Resources', arabic: 'الموارد البشرية' },
  delivery: { english: 'Delivery', arabic: 'التسليم والتنفيذ' },
};
// Existing accounts are kept as they are; `joinedDaysAgo` only applies to new ones.
const PEOPLE = [
  { email: 's@s.com', role: R.HR, dept: 'hr', existing: true },
  { email: 'noura.otaibi@company.io', name: 'Noura Al-Otaibi', role: R.HR, dept: 'hr', joinedDaysAgo: 400 },

  { email: 'manager@company.io', role: R.MANAGER, dept: 'it', existing: true },
  { email: 'faisal.harbi@company.io', name: 'Faisal Al-Harbi', role: R.MANAGER, dept: 'sales', joinedDaysAgo: 520 },
  { email: 'reem.qahtani@company.io', name: 'Reem Al-Qahtani', role: R.MANAGER, dept: 'delivery', joinedDaysAgo: 610 },

  { email: 'senior1@company.io', role: R.SENIOR, dept: 'it', existing: true },
  { email: 'senior2@company.io', role: R.SENIOR, dept: 'it', existing: true },
  { email: 'khalid.shehri@company.io', name: 'Khalid Al-Shehri', role: R.SENIOR, dept: 'it', joinedDaysAgo: 300 },
  { email: 'majed.dossary@company.io', name: 'Majed Al-Dossary', role: R.SENIOR, dept: 'sales', joinedDaysAgo: 350 },
  { email: 'hessa.mutairi@company.io', name: 'Hessa Al-Mutairi', role: R.SENIOR, dept: 'sales', joinedDaysAgo: 280 },
  { email: 'saud.malki@company.io', name: 'Saud Al-Malki', role: R.SENIOR, dept: 'delivery', joinedDaysAgo: 450 },
  { email: 'dana.rashid@company.io', name: 'Dana Al-Rashid', role: R.SENIOR, dept: 'delivery', joinedDaysAgo: 330 },

  { email: 'learner1@company.io', role: R.LEARNER, dept: 'it', senior: 'senior1@company.io', existing: true },
  { email: 'learner2@company.io', role: R.LEARNER, dept: 'it', senior: 'senior2@company.io', existing: true },
  { email: 'learner3@company.io', role: R.LEARNER, dept: 'it', senior: 'senior1@company.io', existing: true },
  { email: 'a@a.com', role: R.LEARNER, dept: 'it', senior: 'senior1@company.io', existing: true },
  { email: 'rana.zahrani@company.io', name: 'Rana Al-Zahrani', role: R.LEARNER, dept: 'it', senior: 'khalid.shehri@company.io', joinedDaysAgo: 13 },

  { email: 'abdullah.qahtani@company.io', name: 'Abdullah Al-Qahtani', role: R.LEARNER, dept: 'sales', senior: 'majed.dossary@company.io', joinedDaysAgo: 72 },
  { email: 'lama.ghamdi@company.io', name: 'Lama Al-Ghamdi', role: R.LEARNER, dept: 'sales', senior: 'majed.dossary@company.io', joinedDaysAgo: 42 },
  { email: 'turki.anazi@company.io', name: 'Turki Al-Anazi', role: R.LEARNER, dept: 'sales', senior: 'hessa.mutairi@company.io', joinedDaysAgo: 27 },
  { email: 'maha.shammari@company.io', name: 'Maha Al-Shammari', role: R.LEARNER, dept: 'sales', senior: 'hessa.mutairi@company.io', joinedDaysAgo: 52 },
  { email: 'nawaf.subaie@company.io', name: 'Nawaf Al-Subaie', role: R.LEARNER, dept: 'sales', senior: 'hessa.mutairi@company.io', joinedDaysAgo: 6 },

  { email: 'fahad.juhani@company.io', name: 'Fahad Al-Juhani', role: R.LEARNER, dept: 'delivery', senior: 'saud.malki@company.io', joinedDaysAgo: 88 },
  { email: 'joud.harthi@company.io', name: 'Joud Al-Harthi', role: R.LEARNER, dept: 'delivery', senior: 'saud.malki@company.io', joinedDaysAgo: 57 },
  { email: 'hamad.mansour@company.io', name: 'Hamad Al-Mansour', role: R.LEARNER, dept: 'delivery', senior: 'saud.malki@company.io', joinedDaysAgo: 32 },
  { email: 'ibrahim.omari@company.io', name: 'Ibrahim Al-Omari', role: R.LEARNER, dept: 'delivery', senior: 'dana.rashid@company.io', joinedDaysAgo: 47 },
  { email: 'ghada.sulami@company.io', name: 'Ghada Al-Sulami', role: R.LEARNER, dept: 'delivery', senior: 'dana.rashid@company.io', joinedDaysAgo: 17 },
];
for (const p of PEOPLE) {
  if (!p.existing) PASSWORDS[p.email] = { [R.LEARNER]: 'learner123', [R.SENIOR]: 'senior123', [R.MANAGER]: 'manager123', [R.HR]: 'hr123' }[p.role];
}

// ════════════════════════════════════════ Content ════════════════════════════════════════
const link = (label, url) => ({ kind: 1001, label, url });
const pdf = (label, title, lines) => ({ kind: 1003, label, pdf: { title, lines } });
const mc = (prompt, options, correct) => ({ type: 1001, prompt, options, correctOptionIndex: 1001 + correct });
const yn = (prompt, answer) => ({ type: 1002, prompt, options: [], correctBoolAnswer: answer });
const open = (prompt) => ({ type: 1003, prompt });
const item = (title, html, attachments = []) => ({ title, description: html, attachments });
const unit = (title, description, items, quiz = []) => ({ title, description, items, quiz });
const p = (...paragraphs) => paragraphs.map((x) => (x.startsWith('<') ? x : `<p>${x}</p>`)).join('');
const ul = (...li) => `<ul>${li.map((x) => `<li>${x}</li>`).join('')}</ul>`;

const JOURNEYS = [
  // ── IT ──────────────────────────────────────────────────────────────────────────────
  { key: 'aws', author: 'senior1@company.io', title: 'AWS DevOps Fundamentals', tag: 'Amazon DevOps', targetDays: 21,
    description: 'Core skills for operating and deploying our services on AWS: identity, compute, CI/CD and observability.',
    units: [
      unit('Identity & access', 'Least privilege, roles and how our accounts are organised.', [
        item('IAM basics', p('Every person and service gets only the permissions it needs.', ul('Users, groups and roles', 'Policies and the policy simulator', 'Why we never use the root account')), [link('IAM user guide', 'https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction.html')]),
        item('Our account structure', p('We run separate AWS accounts for sandbox, staging and production, joined through AWS Organizations.', ul('Request sandbox access through the service desk', 'Production access is time-boxed and approved')), [pdf('Account map (PDF)', 'AWS account map', ['sandbox  - experiments, reset weekly', 'staging  - release candidates', 'production - customer traffic, change-controlled'])]),
      ], [mc('Which AWS service manages least-privilege access?', ['S3', 'IAM', 'CloudFront', 'Route 53'], 1), yn('Should day-to-day work use the root account?', false)]),
      unit('Compute & scaling', 'EC2, launch templates and Auto Scaling groups.', [
        item('EC2 & launch templates', p('Launch templates describe how an instance starts: AMI, size, network and user data.'), [link('EC2 launch templates', 'https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-launch-templates.html')]),
        item('Auto Scaling groups', p('An ASG keeps the right number of healthy instances running and replaces failed ones.', ul('Desired, minimum and maximum capacity', 'Target tracking policies', 'Health checks and cooldowns')), [link('Auto Scaling user guide', 'https://docs.aws.amazon.com/autoscaling/ec2/userguide/what-is-amazon-ec2-auto-scaling.html')]),
      ], [mc('What does an Auto Scaling group replace automatically?', ['Unhealthy instances', 'IAM roles', 'S3 buckets'], 0)]),
      unit('Pipelines & observability', 'How a change reaches production and how we know it is healthy.', [
        item('CI/CD with CodePipeline', p('Trace a deployment from a merged pull request to production: build, test, staging approval, release.'), [link('CodePipeline concepts', 'https://docs.aws.amazon.com/codepipeline/latest/userguide/concepts.html')]),
        item('CloudWatch dashboards & alarms', p('Find the dashboard for your team\'s service and learn which alarms page the on-call engineer.'), [link('CloudWatch alarms', 'https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html')]),
      ], [yn('Can production deployments skip the staging approval?', false)]),
    ],
    exam: { title: 'AWS DevOps Fundamentals — checkpoint', passing: 70, questions: [
      mc('Which service should hold application secrets?', ['S3', 'Secrets Manager', 'CloudTrail', 'EC2 user data'], 1),
      yn('Is it acceptable to share one IAM user between teammates?', false),
      mc('What keeps a fixed number of healthy instances running?', ['Auto Scaling group', 'Route 53', 'CloudFront'], 0),
      open('Describe what happens between a merged pull request and a production release in our pipeline.'),
    ] } },

  { key: 'angular', author: 'senior2@company.io', title: 'Angular & Express Full-Stack', tag: 'Angular / Express', targetDays: 30,
    description: 'Build and ship a feature end to end across our Angular frontend and Express API.',
    units: [
      unit('Angular foundations', 'Standalone components, services and RxJS in our codebase.', [
        item('Component architecture', p('Read through the shared component library and how inputs and outputs keep components reusable.'), [link('Angular components', 'https://angular.dev/guide/components')]),
        item('RxJS & state', p('Understand Observables versus Promises and when we reach for a BehaviorSubject.'), [link('RxJS overview', 'https://rxjs.dev/guide/overview')]),
      ], [mc('Which RxJS type keeps a current value you can read synchronously?', ['Subject', 'BehaviorSubject', 'ReplaySubject'], 1)]),
      unit('Express API', 'Routes, middleware and authentication.', [
        item('Routes & middleware', p('Add a GET route to the sample service and log requests with middleware.'), [link('Express routing', 'https://expressjs.com/en/guide/routing.html')]),
        item('Auth & JWT', p('Follow a request as it is authenticated: header, verification, user on the request.', ul('Never store tokens in plain logs', 'Short expiry, refresh through the auth service'))),
      ], [yn('Should JWTs be written to application logs for debugging?', false)]),
      unit('Shipping a feature', 'Wire the frontend to the API and get it reviewed.', [
        item('Connect Angular to the API', p('Replace a mocked call in a feature service with a real HTTP call and handle errors in the UI.')),
        item('Code review checklist', p('What reviewers look for before approving a merge request.'), [pdf('Review checklist (PDF)', 'Code review checklist', ['Tests cover the change', 'No secrets or tokens in code', 'Accessible markup and translations', 'Errors are handled and logged'])]),
      ]),
    ],
    exam: { title: 'Full-stack checkpoint', passing: 70, questions: [
      mc('Where should shared UI state that components subscribe to live?', ['In a service', 'In index.html', 'In a global variable'], 0),
      yn('Is it safe to store a JWT in localStorage without any other mitigation?', false),
      open('Explain how an Angular service call reaches an Express route and how the response gets back to the component.'),
    ] } },

  { key: 'splunk', author: 'senior1@company.io', title: 'Operations & Monitoring with Splunk', tag: 'Splunk & Reporting', targetDays: 14,
    description: 'Read production signals with confidence: searches, dashboards, alerting and the on-call runbook.',
    units: [
      unit('Searching logs', 'SPL essentials for day-to-day investigation.', [
        item('Splunk SPL basics', p('Write a search that filters the last 24 hours of logs for one service.', ul('index, sourcetype and time range', 'stats, table and timechart')), [link('Search tutorial', 'https://docs.splunk.com/Documentation/Splunk/latest/SearchTutorial/WelcometotheSearchTutorial')]),
        item('Building dashboards', p('Find the dashboards for your team\'s main service and add one panel.')),
      ], [mc('Which command summarises results by field?', ['stats', 'rename', 'head'], 0)]),
      unit('Alerting & on-call', 'From an alert to a resolved incident.', [
        item('Alerting & reporting', p('How alerts are routed, and which ones page the on-call engineer.')),
        item('On-call runbook walkthrough', p('Walk through a real past incident using the runbook.'), [pdf('On-call runbook (PDF)', 'On-call runbook', ['1. Acknowledge the page', '2. Check the service dashboard', '3. Correlate logs in Splunk', '4. Mitigate, then communicate', '5. Write the incident review'])]),
      ], [yn('Should an incident be closed before recovery is confirmed?', false)]),
    ] },

  { key: 'secure', author: 'khalid.shehri@company.io', title: 'Secure Coding Essentials', tag: 'Security', targetDays: 10,
    description: 'The habits that keep our code and customers safe: OWASP Top 10, secrets and dependency hygiene.',
    units: [
      unit('Common vulnerabilities', 'The OWASP Top 10 through examples from our stack.', [
        item('OWASP Top 10 overview', p('Injection, broken access control, misconfiguration — and how each shows up in web apps.'), [link('OWASP Top 10', 'https://owasp.org/www-project-top-ten/')]),
        item('Input validation & output encoding', p('Validate on the server, encode on output, never trust the client.')),
      ], [mc('Which is the best defence against SQL injection?', ['Parameterised queries', 'Hiding error messages', 'Client-side validation'], 0)]),
      unit('Secrets & dependencies', 'Keeping credentials out of code and packages up to date.', [
        item('Handling secrets', p('Secrets live in the secrets manager, never in the repository or build logs.')),
        item('Dependency hygiene', p('Read the dependency report and understand how we patch vulnerable packages.'), [link('OWASP dependency check', 'https://owasp.org/www-project-dependency-check/')]),
      ]),
    ],
    exam: { title: 'Secure coding checkpoint', passing: 80, questions: [
      yn('Can an API key be committed if the repository is private?', false),
      mc('Access control checks belong…', ['on the server', 'in the browser only', 'in CSS'], 0),
      open('Pick one OWASP Top 10 risk and describe how you would prevent it in a feature you build.'),
    ] } },

  // ── Sales ───────────────────────────────────────────────────────────────────────────
  { key: 'crm', author: 'majed.dossary@company.io', title: 'CRM & Pipeline Basics', tag: 'Sales & CRM', targetDays: 14,
    description: 'Keep the pipeline honest: accounts, contacts, opportunities and forecasting in our CRM.',
    units: [
      unit('Working in the CRM', 'Records every seller keeps up to date.', [
        item('Accounts, contacts & leads', p('How a lead becomes a contact on an account, and who owns each record.'), [link('Salesforce Trailhead: CRM basics', 'https://trailhead.salesforce.com/content/learn/modules/crm-basics')]),
        item('Logging activities', p('Log every call, meeting and email the same day so the team sees the full picture.')),
      ], [yn('Should customer calls be logged in the CRM the same day?', true)]),
      unit('Pipeline & forecasting', 'Stages, probabilities and the weekly forecast call.', [
        item('Opportunity stages', p('Our five stages and the exit criteria for each.', ul('Qualify', 'Discover', 'Propose', 'Negotiate', 'Closed won / lost')), [pdf('Stage definitions (PDF)', 'Opportunity stages', ['Qualify - budget, authority, need, timing confirmed', 'Discover - pain and success criteria documented', 'Propose - solution and price sent', 'Negotiate - terms under review', 'Closed - signed or lost with reason'])]),
        item('Weekly forecast', p('How commit, best case and pipeline roll up into the forecast reviewed every Sunday.')),
      ], [mc('Which stage requires a documented budget and decision maker?', ['Qualify', 'Propose', 'Closed won'], 0)]),
    ],
    exam: { title: 'CRM & pipeline checkpoint', passing: 70, questions: [
      mc('An opportunity with a sent proposal belongs in…', ['Qualify', 'Propose', 'Closed lost'], 1),
      yn('Can a deal be marked closed-won before the contract is signed?', false),
      mc('Forecast categories roll up…', ['weekly', 'yearly only', 'never'], 0),
      open('Describe how you would qualify a new inbound lead from a mid-size retailer.'),
    ] } },

  { key: 'product', author: 'faisal.harbi@company.io', title: 'Product Knowledge: Our Services', tag: 'Sales & CRM', targetDays: 14,
    description: 'What we sell, who it is for and the stories that win deals.',
    units: [
      unit('Our portfolio', 'The services, packages and pricing model.', [
        item('Service catalogue', p('Our three service lines and the problems each solves for customers.')),
        item('Pricing & packages', p('How we price: fixed-scope projects, retainers and managed services.'), [pdf('Pricing guide (PDF)', 'Pricing guide', ['Fixed scope - defined deliverables, milestone billing', 'Retainer - monthly capacity, rolling priorities', 'Managed service - SLA-backed, annual contract'])]),
      ], [mc('Which model suits ongoing support with an SLA?', ['Fixed scope', 'Managed service', 'One-off workshop'], 1)]),
      unit('Customers & competitors', 'Who buys, why, and how we compare.', [
        item('Ideal customer profile', p('Industries, company size and the signals that a prospect is a good fit.')),
        item('Competitive landscape', p('Where we win, where we lose, and how to position against each competitor.')),
      ]),
      unit('Winning stories', 'Case studies to use in conversations.', [
        item('Case study: retail replatforming', p('How a retailer cut checkout time by 40% after moving to our platform.')),
      ], [yn('Should case studies be shared without customer approval?', false)]),
    ] },

  { key: 'negotiation', author: 'hessa.mutairi@company.io', title: 'Consultative Selling & Negotiation', tag: 'Sales & CRM', targetDays: 21,
    description: 'Lead discovery conversations and negotiate terms that work for both sides.',
    units: [
      unit('Discovery', 'Ask better questions, listen for the real problem.', [
        item('Discovery questions', p('Open questions that surface pain, impact and decision process.'), [link('HubSpot: discovery questions', 'https://blog.hubspot.com/sales/discovery-call-questions')]),
        item('Mapping stakeholders', p('Identify the economic buyer, champion and blockers early.')),
      ], [mc('Who signs off the budget?', ['Economic buyer', 'Champion', 'End user'], 0)]),
      unit('Negotiation', 'Trade, don\'t give away.', [
        item('Preparing a negotiation', p('Know your walk-away point and the concessions you can trade.')),
        item('Handling objections', p('Acknowledge, clarify, respond, confirm.')),
      ]),
    ],
    exam: { title: 'Selling & negotiation checkpoint', passing: 70, questions: [
      yn('Should you offer a discount before the customer asks for one?', false),
      mc('A champion is someone who…', ['sells internally for you', 'signs the contract', 'blocks the deal'], 0),
      open('A customer says the price is too high. Walk through how you would respond.'),
    ] } },

  // ── HR (company-wide) ───────────────────────────────────────────────────────────────
  { key: 'policies', author: 'noura.otaibi@company.io', title: 'Company Policies & Code of Conduct', tag: 'People & Culture', targetDays: 7,
    description: 'The policies every employee agrees to in their first week.',
    units: [
      unit('Code of conduct', 'How we work with each other, customers and partners.', [
        item('Our code of conduct', p('Respect, integrity and speaking up when something is wrong.'), [pdf('Code of conduct (PDF)', 'Code of conduct', ['Treat everyone with respect', 'Avoid conflicts of interest', 'Protect company and customer information', 'Report concerns without fear of retaliation'])]),
        item('Speaking up', p('How to raise a concern confidentially and what happens next.')),
      ], [yn('Can concerns be raised confidentially?', true)]),
      unit('Information security & privacy', 'Protecting data in everyday work.', [
        item('Acceptable use & passwords', p('Use a password manager, enable MFA, lock your screen.')),
        item('Data privacy basics', p('What personal data is, and how we handle it lawfully.'), [link('SDAIA: Personal Data Protection Law', 'https://sdaia.gov.sa/en/SDAIA/about/Pages/RegulationsAndPolicies.aspx')]),
      ], [mc('What should protect your work accounts?', ['MFA', 'A shared password', 'Nothing'], 0)]),
    ],
    exam: { title: 'Policies acknowledgement', passing: 80, questions: [
      yn('Is it acceptable to share customer data with a friend outside the company?', false),
      mc('A phishing email should be…', ['reported to IT', 'forwarded to colleagues', 'answered'], 0),
      yn('Do all employees have to follow the code of conduct?', true),
    ] } },

  { key: 'benefits', author: 'noura.otaibi@company.io', title: 'Benefits, Payroll & Leave', tag: 'People & Culture', targetDays: 7,
    description: 'Everything about pay day, medical insurance and taking time off.',
    units: [
      unit('Pay & insurance', 'Salary, GOSI and medical cover.', [
        item('Payroll calendar', p('Salaries are paid on the 27th; payslips appear in the HR portal.')),
        item('Medical insurance', p('Your cover class, dependants and how to use the insurance app.')),
      ], [mc('Where do payslips appear?', ['HR portal', 'Email only', 'On request'], 0)]),
      unit('Leave', 'Annual, sick and public holidays.', [
        item('Annual & sick leave', p('How to request leave and the approval flow with your manager.'), [pdf('Leave policy (PDF)', 'Leave policy', ['Annual leave - request two weeks ahead', 'Sick leave - notify your manager the same day', 'Public holidays - published by HR each year'])]),
      ]),
    ] },

  // ── Delivery ────────────────────────────────────────────────────────────────────────
  { key: 'delivery', author: 'saud.malki@company.io', title: 'Project Delivery Fundamentals', tag: 'Project Delivery', targetDays: 21,
    description: 'Plan, run and close client projects the way we do it here.',
    units: [
      unit('Starting a project', 'Kick-off, scope and plan.', [
        item('Project kick-off', p('Run a kick-off that aligns goals, roles and communication.'), [pdf('Kick-off agenda (PDF)', 'Kick-off agenda', ['Goals and success criteria', 'Roles and RACI', 'Plan and milestones', 'Risks and assumptions', 'Communication cadence'])]),
        item('Scope & statement of work', p('Turn the SOW into a work breakdown and spot scope gaps early.'), [link('PMI: scope management', 'https://www.pmi.org/learning/library/project-scope-management-guidelines-7209')]),
      ], [yn('Should scope changes be agreed in writing?', true)]),
      unit('Running the project', 'Status, risks and change control.', [
        item('Status reporting', p('Weekly status: progress, next steps, risks, decisions needed.')),
        item('Risks & issues', p('Keep the RAID log current and escalate early.')),
      ], [mc('A risk that has happened becomes…', ['an issue', 'an assumption', 'a milestone'], 0)]),
      unit('Closing', 'Acceptance and lessons learned.', [
        item('Acceptance & close-out', p('Get formal acceptance, archive documents, release the team.')),
      ]),
    ],
    exam: { title: 'Project delivery checkpoint', passing: 70, questions: [
      mc('Which document defines what is in and out of scope?', ['Statement of work', 'Status report', 'Invoice'], 0),
      yn('Can a project close without client acceptance?', false),
      open('A key client stakeholder asks for extra features mid-sprint. What do you do?'),
    ] } },

  { key: 'jira', author: 'dana.rashid@company.io', title: 'Agile Delivery with Jira', tag: 'Project Delivery', targetDays: 14,
    description: 'Boards, sprints and the workflow our delivery teams use every day.',
    units: [
      unit('Scrum in practice', 'Ceremonies and roles.', [
        item('Sprint ceremonies', p('Planning, daily stand-up, review and retrospective — what each is for.'), [link('Atlassian: Scrum ceremonies', 'https://www.atlassian.com/agile/scrum/ceremonies')]),
        item('Writing good user stories', p('As a…, I want…, so that… — plus acceptance criteria.')),
      ], [mc('Which meeting inspects the increment with stakeholders?', ['Sprint review', 'Daily stand-up', 'Retrospective'], 0)]),
      unit('Jira workflow', 'Our board and how work moves through it.', [
        item('Boards & workflow', p('To do → In progress → Code review → QA → Done, and who moves each card.'), [link('Jira boards', 'https://support.atlassian.com/jira-software-cloud/docs/what-is-a-jira-software-board/')]),
        item('Estimation & velocity', p('Story points, velocity and why we do not compare teams on it.')),
      ], [yn('Should velocity be used to compare teams?', false)]),
    ] },

  { key: 'handover', author: 'reem.qahtani@company.io', title: 'Client Handover & Quality Assurance', tag: 'Project Delivery', targetDays: 14,
    description: 'Deliver work the client can run: QA gates, documentation and handover.',
    units: [
      unit('Quality gates', 'What must pass before anything reaches the client.', [
        item('QA checklist', p('Functional, regression, accessibility and performance checks.'), [pdf('QA gate checklist (PDF)', 'QA gate checklist', ['Test cases executed and passed', 'No open critical defects', 'Accessibility checked', 'Performance within agreed limits'])]),
        item('User acceptance testing', p('Plan UAT with the client and track sign-off.')),
      ], [yn('Can a release go out with open critical defects?', false)]),
      unit('Handover', 'Documentation and training for the client team.', [
        item('Handover pack', p('Runbooks, architecture overview, credentials transfer and support contacts.')),
        item('Hypercare', p('The two weeks after go-live: monitoring, quick fixes and daily check-ins.')),
      ]),
    ],
    exam: { title: 'Handover & QA checkpoint', passing: 75, questions: [
      mc('UAT is signed off by…', ['the client', 'the developer', 'nobody'], 0),
      yn('Is a handover pack needed for managed-service clients?', true),
      open('List what you would include in a handover pack for a web platform.'),
    ] } },
];

const PACKAGES = [
  { key: 'it-starter', author: 'manager@company.io', title: 'IT New Hire Starter', targetDays: 45,
    description: 'The first six weeks for new engineers: company policies, AWS and our full-stack.', journeys: ['policies', 'aws', 'angular'] },
  { key: 'sales-onboarding', author: 'faisal.harbi@company.io', title: 'Sales Onboarding', targetDays: 35,
    description: 'Everything a new seller needs before their first customer meeting.', journeys: ['policies', 'crm', 'product'] },
  { key: 'delivery-onboarding', author: 'reem.qahtani@company.io', title: 'Delivery Onboarding', targetDays: 40,
    description: 'Policies, project delivery and our Jira workflow for new delivery team members.', journeys: ['policies', 'delivery', 'jira'] },
];

// ════════════════════════════════════════ Scenarios ════════════════════════════════════════
// units: one state per unit — done (reviewed), review (waiting), sentback, partial, started, new.
// exam: passed:<score> | failed:<score> | submitted. last: days ago of the most recent activity.
// due: days from today (negative = overdue); defaults to assigned + target days.
const S = (units, extra = {}) => ({ units, ...extra });
const DONE3 = ['done', 'done', 'done'];
const SCENARIOS = [
  // IT
  { learner: 'learner1@company.io', package: 'it-starter', by: 'manager@company.io', assigned: 80, due: 6, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 74 }),
    aws: S(DONE3, { exam: 'passed:75', last: 41 }),
    angular: S(['done', 'review', 'new'], { last: 1, notes: true }) } },
  { learner: 'learner1@company.io', selfEnroll: 'secure', assigned: 9, progress: S(['partial', 'new'], { last: 2 }) },
  { learner: 'learner3@company.io', journey: 'aws', by: 'senior1@company.io', assigned: 60, due: -10, progress: S(['done', 'done', 'partial'], { last: 9 }) },
  { learner: 'learner3@company.io', journey: 'splunk', by: 'senior1@company.io', assigned: 45, progress: S(['done', 'done'], { last: 30 }) },
  { learner: 'learner2@company.io', package: 'it-starter', by: 'manager@company.io', assigned: 30, progress: {
    policies: S(['done', 'done'], { exam: 'submitted', last: 3 }),
    aws: S(['sentback', 'started', 'new'], { last: 2, notes: true }) } },
  { learner: 'rana.zahrani@company.io', package: 'it-starter', by: 'manager@company.io', assigned: 12, progress: {
    policies: S(['review', 'started'], { last: 0.5 }) } },
  { learner: 'a@a.com', journey: 'splunk', by: 'senior1@company.io', assigned: 2 },

  // Sales
  { learner: 'abdullah.qahtani@company.io', package: 'sales-onboarding', by: 'faisal.harbi@company.io', assigned: 70, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 66 }),
    crm: S(['done', 'done'], { exam: 'passed:75', last: 52 }),
    product: S(DONE3, { last: 38 }) } },
  { learner: 'abdullah.qahtani@company.io', journey: 'negotiation', by: 'majed.dossary@company.io', assigned: 20, progress: S(['done', 'partial'], { last: 1, notes: true }) },
  { learner: 'lama.ghamdi@company.io', package: 'sales-onboarding', by: 'faisal.harbi@company.io', assigned: 40, due: -4, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 35 }),
    crm: S(['done', 'done'], { exam: 'failed:50', last: 18 }),
    product: S(['done', 'partial', 'new'], { last: 12 }) } },
  { learner: 'turki.anazi@company.io', package: 'sales-onboarding', by: 'faisal.harbi@company.io', assigned: 25, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 20 }),
    crm: S(['done', 'review'], { last: 1 }) } },
  { learner: 'maha.shammari@company.io', package: 'sales-onboarding', by: 'faisal.harbi@company.io', assigned: 50, due: 5, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 45 }),
    crm: S(['done', 'done'], { exam: 'passed:100', last: 30 }),
    product: S(['done', 'done', 'review'], { last: 2 }) } },
  { learner: 'maha.shammari@company.io', journey: 'negotiation', by: 'hessa.mutairi@company.io', assigned: 15, cancel: 'faisal.harbi@company.io', progress: S(['started', 'new'], { last: 13 }) },
  { learner: 'nawaf.subaie@company.io', package: 'sales-onboarding', by: 'faisal.harbi@company.io', assigned: 4, progress: {
    policies: S(['started', 'new'], { last: 0.2 }) } },

  // Delivery
  { learner: 'fahad.juhani@company.io', package: 'delivery-onboarding', by: 'reem.qahtani@company.io', assigned: 85, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 80 }),
    delivery: S(DONE3, { exam: 'passed:100', last: 62 }),
    jira: S(['done', 'done'], { last: 50 }) } },
  { learner: 'fahad.juhani@company.io', journey: 'handover', by: 'saud.malki@company.io', assigned: 35, progress: S(['done', 'done'], { exam: 'passed:100', last: 21 }) },
  { learner: 'joud.harthi@company.io', package: 'delivery-onboarding', by: 'reem.qahtani@company.io', assigned: 55, due: -3, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 50 }),
    delivery: S(DONE3, { exam: 'submitted', last: 11 }),
    jira: S(['partial', 'new'], { last: 11 }) } },
  { learner: 'hamad.mansour@company.io', package: 'delivery-onboarding', by: 'reem.qahtani@company.io', assigned: 30, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 26 }),
    delivery: S(['done', 'sentback', 'new'], { last: 1, notes: true }) } },
  { learner: 'ibrahim.omari@company.io', package: 'delivery-onboarding', by: 'reem.qahtani@company.io', assigned: 45, progress: {
    policies: S(['done', 'done'], { exam: 'passed:100', last: 40 }),
    delivery: S(DONE3, { exam: 'failed:33', last: 14 }),
    jira: S(['done', 'done'], { last: 8 }) } },
  { learner: 'ghada.sulami@company.io', package: 'delivery-onboarding', by: 'reem.qahtani@company.io', assigned: 15, progress: {
    policies: S(['done', 'review'], { last: 1 }) } },
  { learner: 'ghada.sulami@company.io', selfEnroll: 'benefits', assigned: 10, progress: S(['done', 'done'], { last: 6 }) },
];

// Conversations for scenarios with notes: learner asks, reviewer answers.
const QUESTIONS = [
  ['Is there a sandbox I can practise this in?', 'Yes — request sandbox access through the service desk, it is approved the same day.'],
  ['The link in this item asks me to log in. Which account should I use?', 'Use your company SSO account; ping me if it still fails.'],
  ['Should I finish the whole unit before asking for a review?', 'Yes, the unit goes to review by itself once the items and quiz are done.'],
];
const SENT_BACK_NOTE = 'Good start — please redo the quiz and add a short summary of what you learned in the discussion before I complete this unit.';

// ════════════════════════════════════════ Run ════════════════════════════════════════
const log = (...a) => console.log(...a);

async function uploadPdf(token, name, title, lines) {
  // A minimal, valid one-page PDF with a title and a few lines of text.
  const text = [title, '', ...lines].map((l, i) => `BT /F1 ${i === 0 ? 16 : 11} Tf 60 ${760 - i * 22} Td (${l.replace(/[()\\]/g, '')}) Tj ET`).join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((o, i) => { offsets.push(body.length); body += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = body.length;
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  const form = new FormData();
  form.append('file', new Blob([body], { type: 'application/pdf' }), name);
  return call(token, 'POST', '/files', undefined, form);
}

async function main() {
  const admin = await as('admin@company.io');

  // ── 1. Clear learning data (journeys through the API so uploaded files are removed too) ──
  log('clearing old data…');
  for (const j of await call(admin, 'GET', '/journeys')) {
    await call(admin, 'DELETE', `/journeys/${j.id}`).catch(() => undefined);
  }
  sql(`SET FOREIGN_KEY_CHECKS=0;
       DELETE FROM notifications; DELETE FROM announcement_dismissals; DELETE FROM announcement_departments; DELETE FROM announcements; DELETE FROM certificates; DELETE FROM journey_notes; DELETE FROM exam_answers; DELETE FROM exam_attempts;
       DELETE FROM exam_questions; DELETE FROM exams;
       DELETE FROM package_assignment_journeys; DELETE FROM package_assignments; DELETE FROM package_journeys; DELETE FROM packages;
       DELETE FROM learner_journey_units; DELETE FROM learner_journey_items; DELETE FROM learner_journeys;
       DELETE FROM unit_quiz_questions; DELETE FROM journey_item_attachments; DELETE FROM journey_items; DELETE FROM journey_units; DELETE FROM journeys;
       SET FOREIGN_KEY_CHECKS=1;`);

  // ── 2. Departments ──
  const existing = await call(admin, 'GET', '/departments');
  for (const d of Object.values(DEPARTMENTS)) {
    const found = existing.find((x) => x.english.toLowerCase() === d.english.toLowerCase() || x.id === d.id);
    d.id = found ? found.id : (await call(admin, 'POST', '/departments', { english: d.english, arabic: d.arabic })).id;
    if (found && found.arabic !== d.arabic) await call(admin, 'PUT', `/departments/${found.id}`, { english: d.english, arabic: d.arabic });
  }
  log('departments:', Object.values(DEPARTMENTS).map((d) => `${d.english}=${d.id}`).join(', '));

  // ── 3. People ──
  let users = await call(admin, 'GET', '/users');
  const byEmail = () => Object.fromEntries(users.map((u) => [u.email, u]));
  for (const person of PEOPLE.filter((x) => x.role !== R.LEARNER)) {
    const u = byEmail()[person.email];
    if (!u) {
      await call(admin, 'POST', '/users', { name: person.name, email: person.email, password: PASSWORDS[person.email], role: person.role, departmentId: DEPARTMENTS[person.dept].id });
    } else if (u.department?.id !== DEPARTMENTS[person.dept].id) {
      await call(admin, 'PUT', `/users/${u.id}`, { departmentId: DEPARTMENTS[person.dept].id });
    }
  }
  users = await call(admin, 'GET', '/users');
  for (const person of PEOPLE.filter((x) => x.role === R.LEARNER)) {
    const u = byEmail()[person.email];
    const seniorId = byEmail()[person.senior].id;
    if (!u) {
      await call(admin, 'POST', '/users', { name: person.name, email: person.email, password: PASSWORDS[person.email], role: person.role, departmentId: DEPARTMENTS[person.dept].id, seniorId });
    } else if (u.department?.id !== DEPARTMENTS[person.dept].id || u.seniorId !== seniorId) {
      await call(admin, 'PUT', `/users/${u.id}`, { departmentId: DEPARTMENTS[person.dept].id, seniorId });
    }
  }
  users = await call(admin, 'GET', '/users');
  const U = byEmail();
  for (const person of PEOPLE.filter((x) => !x.existing)) {
    sql(`UPDATE users SET created_at=${q(dt(workHours(daysAgo(person.joinedDaysAgo))))} WHERE id=${q(U[person.email].id)}`);
  }
  // Departments no longer in use (e.g. test ones) go.
  for (const d of await call(admin, 'GET', '/departments')) {
    if (!Object.values(DEPARTMENTS).some((x) => x.id === d.id) && !d.memberCount) await call(admin, 'DELETE', `/departments/${d.id}`);
  }
  log(`people: ${users.length}`);

  // ── 4. Journeys, quizzes, attachments, exams ──
  const J = {};
  for (const spec of JOURNEYS) {
    const token = await as(spec.author);
    const units = [];
    for (const u of spec.units) {
      const items = [];
      for (const it of u.items) {
        const attachments = [];
        for (const a of it.attachments) {
          if (a.pdf) {
            const file = await uploadPdf(token, `${a.pdf.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.pdf`, a.pdf.title, a.pdf.lines);
            attachments.push({ kind: 1003, label: a.label, storageKey: file.storageKey, mimeType: file.mimeType, sizeBytes: file.sizeBytes, originalName: file.originalName });
          } else {
            attachments.push(a);
          }
        }
        items.push({ title: it.title, description: it.description, attachments });
      }
      units.push({ title: u.title, description: u.description, items, quiz: u.quiz });
    }
    const journey = await call(token, 'POST', '/journeys', { title: spec.title, description: spec.description, techTag: spec.tag, targetDays: spec.targetDays, units });
    if (spec.exam) {
      await call(token, 'POST', `/journeys/${journey.id}/exam`, { title: spec.exam.title, passingScorePercent: spec.exam.passing, questions: spec.exam.questions });
    }
    J[spec.key] = { ...spec, id: journey.id, units: await call(token, 'GET', `/journeys/${journey.id}/units`),
      examDto: spec.exam ? await call(token, 'GET', `/journeys/${journey.id}/exam`) : null };
    sql(`UPDATE journeys SET created_at=${q(dt(workHours(daysAgo(between(95, 120)))))}, updated_at=${q(dt(workHours(daysAgo(between(20, 90)))))} WHERE id=${q(journey.id)}`);
  }
  log(`journeys: ${Object.keys(J).length}`);

  // ── 5. Packages ──
  const P = {};
  for (const spec of PACKAGES) {
    const pkg = await call(await as(spec.author), 'POST', '/packages', { title: spec.title, description: spec.description, targetDays: spec.targetDays, journeyIds: spec.journeys.map((k) => J[k].id) });
    P[spec.key] = { ...spec, id: pkg.id };
    sql(`UPDATE packages SET created_at=${q(dt(daysAgo(100)))}, updated_at=${q(dt(daysAgo(between(40, 90))))} WHERE id=${q(pkg.id)}`);
  }
  log(`packages: ${Object.keys(P).length}`);

  // ── 6. Assignments and what each learner did ──
  const backdate = []; // SQL statements, run at the end
  const journeyWindows = []; // { ljId, from, to } for notification timestamps
  for (const sc of SCENARIOS) {
    const learner = U[sc.learner];
    const assignedAt = workHours(daysAgo(sc.assigned));
    const person = PEOPLE.find((x) => x.email === sc.learner);
    const reviewerEmail = person.senior;

    let targets; // [{ key, ljId, progress }]
    let packageDue = null; // journeys in a package share its due date
    if (sc.package) {
      const pa = await call(await as(sc.by), 'POST', '/package-assignments', { packageId: P[sc.package].id, learnerId: learner.id });
      targets = P[sc.package].journeys.map((key) => ({ key, ljId: pa.journeys.find((x) => x.journeyId === J[key].id).learnerJourneyId, progress: sc.progress?.[key] }));
      const due = sc.due != null ? new Date(NOW + sc.due * DAY) : new Date(assignedAt.getTime() + P[sc.package].targetDays * DAY);
      packageDue = due;
      backdate.push(`UPDATE package_assignments SET assigned_at=${q(dt(assignedAt))}, due_date=${q(dateOnly(due))} WHERE id=${q(pa.id)};`);
    } else if (sc.selfEnroll) {
      const res = await call(await as(sc.learner), 'POST', '/catalog/enroll', { type: 1001, id: J[sc.selfEnroll].id });
      targets = [{ key: sc.selfEnroll, ljId: res.learnerJourneyId, progress: sc.progress }];
    } else {
      const lj = await call(await as(sc.by), 'POST', '/learner-journeys', { journeyId: J[sc.journey].id, learnerId: learner.id });
      targets = [{ key: sc.journey, ljId: lj.id, progress: sc.progress }];
    }

    for (const t of targets) {
      const jspec = J[t.key];
      const due = packageDue ?? (sc.due != null ? new Date(NOW + sc.due * DAY) : new Date(assignedAt.getTime() + (jspec.targetDays ?? 21) * DAY));
      const progress = t.progress;
      const lastActivity = progress?.last != null ? new Date(NOW - progress.last * DAY) : assignedAt;
      // Learners who have not started are never logged in as (their password may be unknown).
      const learnerToken = progress ? await as(sc.learner) : null;
      const outline = progress ? await call(learnerToken, 'GET', `/learner-journeys/${t.ljId}/outline`) : null;
      const reviewer = progress ? await as(reviewerEmail) : null;

      // Every action the learner and reviewer take, in order, gets a moment between assignment and last activity.
      const events = [];
      const mark = (kind, row, extra = {}) => events.push({ kind, row, ...extra });

      if (progress) {
        for (let ui = 0; ui < outline.units.length; ui++) {
          const unitState = progress.units[ui] ?? 'new';
          const u = outline.units[ui];
          const authoring = jspec.units[ui];
          if (unitState === 'new') continue;
          const itemsToComplete = unitState === 'started' ? 0 : unitState === 'partial' ? Math.max(1, Math.floor(u.items.length / 2)) : u.items.length;
          for (let ii = 0; ii < u.items.length; ii++) {
            const it = u.items[ii];
            if (ii < itemsToComplete) {
              await call(learnerToken, 'POST', `/learner-journey-items/${it.progressId}/open`);
              await call(learnerToken, 'POST', `/learner-journey-items/${it.progressId}/complete`);
              mark('item-done', it.progressId);
            } else if (ii === itemsToComplete) {
              await call(learnerToken, 'POST', `/learner-journey-items/${it.progressId}/open`);
              mark('item-open', it.progressId);
              break;
            }
          }
          const unitFinished = ['done', 'review', 'sentback'].includes(unitState);
          if (unitFinished && authoring.quiz.length) {
            // Mostly right; the odd miss makes scores look real.
            const answers = authoring.quiz.map((qq) => {
              const right = rand() > 0.2;
              return qq.type.code === 1001
                ? { questionId: qq.id, selectedOptionIndex: right ? qq.correctOptionIndex : (qq.correctOptionIndex === 1001 ? 1002 : 1001) }
                : { questionId: qq.id, boolAnswer: right ? qq.correctBoolAnswer : !qq.correctBoolAnswer };
            });
            await call(learnerToken, 'POST', `/learner-journey-units/${u.learnerUnitId}/quiz`, { answers });
            mark('quiz', u.learnerUnitId);
          }
          if (unitState === 'done') {
            await call(reviewer, 'PATCH', `/learner-journey-units/${u.learnerUnitId}/status`, { status: 1004 });
            mark('unit-reviewed', u.learnerUnitId);
          } else if (unitState === 'sentback') {
            await call(reviewer, 'PATCH', `/learner-journey-units/${u.learnerUnitId}/status`, { status: 1002 });
            await call(reviewer, 'POST', `/learner-journey-units/${u.learnerUnitId}/notes`, { message: SENT_BACK_NOTE });
            mark('unit-reviewed', u.learnerUnitId, { sentBack: true });
          } else if (unitFinished) {
            mark('unit-submitted', u.learnerUnitId);
          }
        }

        if (progress.notes) {
          const [question, answer] = QUESTIONS[Math.floor(rand() * QUESTIONS.length)];
          const firstItem = outline.units[0].items[0].progressId;
          await call(learnerToken, 'POST', `/learner-journey-items/${firstItem}/notes`, { message: question });
          await call(reviewer, 'POST', `/learner-journey-items/${firstItem}/notes`, { message: answer });
          mark('conversation', firstItem);
        }

        if (progress.exam && jspec.examDto) {
          const [state, scoreText] = progress.exam.split(':');
          const target = Number(scoreText ?? 100) / 100;
          const qs = jspec.examDto.questions;
          const rightCount = Math.round(target * qs.length);
          const answers = qs.map((qq, i) => {
            const right = i < rightCount;
            if (qq.type.code === 1001) return { questionId: qq.id, selectedOptionIndex: right ? qq.correctOptionIndex : (qq.correctOptionIndex === 1001 ? 1002 : 1001) };
            if (qq.type.code === 1002) return { questionId: qq.id, boolAnswer: right ? qq.correctBoolAnswer : !qq.correctBoolAnswer };
            return { questionId: qq.id, openText: right
              ? 'I would follow our documented process step by step, check with my senior where unsure, and confirm the outcome with the stakeholder.'
              : 'Not sure — I would ask someone.' };
          });
          const attempt = await call(learnerToken, 'POST', `/learner-journeys/${t.ljId}/exam-attempt`, { examId: jspec.examDto.id, answers });
          mark('exam-submitted', attempt.id);
          if (state !== 'submitted') {
            const marks = qs.map((qq, i) => ({ questionId: qq.id, markedCorrect: i < rightCount }));
            await call(reviewer, 'PATCH', `/exam-attempts/${attempt.id}/grade`, { marks, passed: state === 'passed' });
            mark('exam-graded', attempt.id);
          }
        }
      }
      if (sc.cancel) await call(await as(sc.cancel), 'PATCH', `/learner-journeys/${t.ljId}/status`, { status: 1005 });

      // ── Timeline: spread the events from assignment to last activity ──
      const start = assignedAt.getTime() + 0.2 * DAY;
      const end = Math.max(lastActivity.getTime(), start + 0.1 * DAY);
      let firstMoment = null;
      let lastMoment = null;
      events.forEach((e, i) => {
        const at = events.length === 1 ? new Date(end) : new Date(start + ((end - start) * i) / (events.length - 1));
        const when = (e.kind === 'item-done' || e.kind === 'item-open') && end - start > DAY ? workHours(at) : at;
        const moment = new Date(Math.min(when.getTime(), NOW - 3_600_000));
        firstMoment ??= moment;
        lastMoment = moment;
        const m = q(dt(moment));
        switch (e.kind) {
          case 'item-done':
            backdate.push(`UPDATE learner_journey_items SET updated_at=${m}, time_spent_hours=${between(0.5, 3.5).toFixed(2)} WHERE id=${q(e.row)};`);
            break;
          case 'item-open':
            backdate.push(`UPDATE learner_journey_items SET updated_at=${m}, time_spent_hours=${between(0.1, 1.2).toFixed(2)} WHERE id=${q(e.row)};`);
            break;
          case 'quiz':
            backdate.push(`UPDATE learner_journey_units SET quiz_submitted_at=${m}, updated_at=${m} WHERE id=${q(e.row)};`);
            break;
          case 'unit-submitted':
          case 'unit-reviewed':
            backdate.push(`UPDATE learner_journey_units SET updated_at=${m} WHERE id=${q(e.row)};`);
            backdate.push(`UPDATE journey_notes SET created_at=${m} WHERE learner_journey_unit_id=${q(e.row)};`);
            break;
          case 'conversation': {
            const later = q(dt(new Date(moment.getTime() + 3 * 3_600_000 > NOW ? NOW - 60_000 : moment.getTime() + 3 * 3_600_000)));
            backdate.push(`UPDATE journey_notes n JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) rn FROM journey_notes WHERE learner_journey_item_id=${q(e.row)}) x ON x.id=n.id SET n.created_at = IF(x.rn=1, ${m}, ${later});`);
            break;
          }
          case 'exam-submitted':
            backdate.push(`UPDATE exam_attempts SET submitted_at=${m} WHERE id=${q(e.row)};`);
            break;
          case 'exam-graded':
            backdate.push(`UPDATE exam_attempts SET graded_at=${m} WHERE id=${q(e.row)};`);
            break;
        }
      });
      const unitsUntouched = `UPDATE learner_journey_units SET updated_at=${q(dt(assignedAt))} WHERE learner_journey_id=${q(t.ljId)} AND status=1001;`;
      backdate.push(`UPDATE learner_journeys SET assigned_at=${q(dt(assignedAt))}, due_date=${q(dateOnly(due))},
        started_at=IF(status=1001, NULL, ${q(dt(firstMoment ?? assignedAt))}),
        completed_at=IF(status=1004, ${q(dt(lastMoment ?? assignedAt))}, NULL),
        due_soon_notified_at=IF(due_date IS NOT NULL AND ${q(dateOnly(due))} <= CURDATE() + INTERVAL 2 DAY, ${q(dt(new Date(Math.min(due.getTime() - 2 * DAY, NOW - DAY))))}, NULL),
        overdue_notified_at=IF(${q(dateOnly(due))} < CURDATE(), ${q(dt(new Date(Math.min(due.getTime() + DAY, NOW - 3_600_000))))}, NULL)
        WHERE id=${q(t.ljId)};`);
      backdate.push(`UPDATE learner_journey_items SET updated_at=${q(dt(assignedAt))} WHERE learner_journey_id=${q(t.ljId)} AND status=1001;`);
      backdate.push(unitsUntouched);
      journeyWindows.push({ ljId: t.ljId, from: assignedAt, to: new Date(lastMoment ?? assignedAt) });
    }
    log(`  ${sc.learner} ← ${sc.package ?? sc.journey ?? sc.selfEnroll}`);
  }

  // ── 7. Put it all in the past ──
  log('backdating…');
  for (let i = 0; i < backdate.length; i += 60) sql(backdate.slice(i, i + 60).join('\n'));

  // Notifications: timed within their journey's window; older ones read.
  const rows = sql(`SELECT id, IFNULL(JSON_UNQUOTE(JSON_EXTRACT(variables_json, '$.learnerJourneyId')), IFNULL(SUBSTRING_INDEX(JSON_UNQUOTE(JSON_EXTRACT(variables_json, '$.targetPath')), '/', -1), '')), IFNULL(JSON_UNQUOTE(JSON_EXTRACT(variables_json, '$.packageAssignmentId')), '') FROM notifications`)
    .split('\n').filter(Boolean).map((l) => l.split('\t'));
  const paDates = Object.fromEntries(sql(`SELECT id, assigned_at FROM package_assignments`).split('\n').filter(Boolean).map((l) => l.split('\t')));
  const notif = [];
  for (const [id, ljId, paId] of rows) {
    const w = journeyWindows.find((x) => x.ljId === ljId);
    let at = w ? new Date(between(w.from.getTime(), w.to.getTime())) : paId && paDates[paId] ? new Date(paDates[paId].replace(' ', 'T')) : new Date(NOW - between(0.1, 3) * DAY);
    at = new Date(Math.min(at.getTime(), NOW - 600_000));
    const read = at.getTime() < NOW - 2 * DAY ? q(dt(new Date(at.getTime() + between(0.5, 20) * 3_600_000))) : 'NULL';
    notif.push(`UPDATE notifications SET created_at=${q(dt(at))}, read_at=${read} WHERE id=${q(id)};`);
  }
  for (let i = 0; i < notif.length; i += 80) sql(notif.slice(i, i + 80).join('\n'));

  // Certificates were issued as journeys completed; date them to the completion, and their notifications too.
  sql(`UPDATE certificates c JOIN learner_journeys lj ON lj.id = c.learner_journey_id SET c.completed_at = lj.completed_at, c.issued_at = lj.completed_at WHERE lj.completed_at IS NOT NULL;
       UPDATE certificates c SET c.completed_at = (SELECT MAX(lj.completed_at) FROM package_assignment_journeys paj JOIN learner_journeys lj ON lj.id = paj.learner_journey_id WHERE paj.package_assignment_id = c.package_assignment_id AND lj.status = 1004), c.issued_at = c.completed_at WHERE c.package_assignment_id IS NOT NULL;
       UPDATE certificates SET issued_at = completed_at WHERE package_assignment_id IS NOT NULL;
       UPDATE notifications n JOIN certificates c ON n.link = CONCAT('/certificates/', c.id) SET n.created_at = c.issued_at + INTERVAL 2 MINUTE, n.read_at = IF(c.issued_at < NOW() - INTERVAL 2 DAY, c.issued_at + INTERVAL 5 HOUR, NULL);`);

  // ── 8. Announcements: posted by the people who would post them, some long expired, some current ──
  const D = Object.fromEntries(Object.entries(DEPARTMENTS).map(([k, d]) => [k, d.id]));
  const journeyLink = (key, label) => `<a href="/catalog/journeys/${J[key].id}">${label}</a>`;
  const ANNOUNCEMENTS = [
    { by: 'noura.otaibi@company.io', daysAgo: 78, showDays: 21, orgWide: true,
      title: 'Welcome to Journey, our new onboarding platform',
      body: p('Starting today, every new joiner follows their onboarding in Journey instead of spreadsheets and email threads.',
        ul('Your assigned journeys are on your dashboard', 'Browse and enroll in more from <a href="/catalog">the catalog</a>', 'Your senior reviews each unit and answers your questions in the notes'),
        'Questions about the platform? Reply to this announcement through your manager or contact HR.') },
    { by: 'noura.otaibi@company.io', daysAgo: 44, showDays: 14, orgWide: true,
      title: 'Medical insurance renewal: update your dependants by 31 August',
      body: p('Our medical insurance renews on 1 September. If you want to add or remove dependants, send the updated details to HR before <strong>31 August</strong>.',
        `The ${journeyLink('benefits', 'Benefits, Payroll &amp; Leave')} journey explains the cover classes.`) },
    { by: 'faisal.harbi@company.io', daysAgo: 26, showDays: 10, departments: ['sales'],
      title: 'CRM stages updated',
      body: p('We simplified our opportunity stages to five. Please move your open deals to the new stages before the next forecast call.',
        `The ${journeyLink('crm', 'CRM &amp; Pipeline Basics')} journey has the new definitions.`) },
    { by: 'noura.otaibi@company.io', daysAgo: 9, showDays: 30, departments: ['sales', 'delivery'],
      title: 'Client-facing dress code reminder',
      body: p('For client meetings and site visits, please follow the business dress code in the policy.',
        `See the ${journeyLink('policies', 'Company Policies &amp; Code of Conduct')} journey, unit 1.`) },
    { by: 'reem.qahtani@company.io', daysAgo: 6, showDays: 21, departments: ['delivery'],
      title: 'New Jira workflow from 1 October',
      body: p('From 1 October every delivery board adds a <strong>QA</strong> column between Code review and Done.',
        ul(`Refresh the workflow in ${journeyLink('jira', 'Agile Delivery with Jira')}`, 'Move cards waiting for testing into QA before the sprint ends')) },
    { by: 'faisal.harbi@company.io', daysAgo: 4, showDays: 14, departments: ['sales'],
      title: 'Q4 sales kickoff on Sunday at 10:00',
      body: p('Join us in the main meeting room on Sunday at 10:00 for the Q4 kickoff: targets, territories and the new pricing guide.',
        `New sellers: please finish ${journeyLink('product', 'Product Knowledge: Our Services')} before the session.`) },
    { by: 'admin@company.io', daysAgo: 3, showDays: 10, orgWide: true,
      title: 'Office closed for Saudi National Day',
      body: p('The office is closed on <strong>Tuesday 23 September</strong> for Saudi National Day. Due dates that fall on the holiday are not counted as overdue.',
        'Happy National Day! 🇸🇦') },
    { by: 'manager@company.io', daysAgo: 1, showDays: 14, departments: ['it'],
      title: 'AWS sandbox access is now self-service',
      body: p('You no longer need a ticket for sandbox access: request it from the service desk portal and it is approved automatically.',
        `New to AWS here? Start with ${journeyLink('aws', 'AWS DevOps Fundamentals')}.`) },
  ];

  const annIds = [];
  const annTimes = {};
  for (const a of ANNOUNCEMENTS) {
    const created = await call(await as(a.by), 'POST', '/announcements', {
      title: a.title, body: a.body, orgWide: !!a.orgWide,
      departmentIds: (a.departments ?? []).map((k) => D[k]),
    });
    const at = workHours(daysAgo(a.daysAgo));
    annIds.push(created.id);
    annTimes[created.id] = at;
    sql(`UPDATE announcements SET created_at=${q(dt(at))}, updated_at=${q(dt(at))}, show_until=${q(dateOnly(new Date(at.getTime() + a.showDays * DAY)))} WHERE id=${q(created.id)}`);
  }

  // Their notifications are written in the background; wait for the count to settle before dating them.
  let last = -1;
  for (let i = 0; i < 40; i++) {
    const count = Number(sql(`SELECT COUNT(*) FROM notifications WHERE template_id='announcement-published'`));
    if (count === last) break;
    last = count;
    await new Promise((done) => setTimeout(done, 750));
  }
  const annNotif = [];
  for (const line of sql(`SELECT id, link FROM notifications WHERE template_id='announcement-published'`).split('\n').filter(Boolean)) {
    const [id, linkTo] = line.split('\t');
    const at = annTimes[linkTo.split('=')[1]];
    if (!at) continue;
    const readAt = at.getTime() < NOW - 2 * DAY && rand() < 0.85 ? q(dt(new Date(at.getTime() + between(0.2, 30) * 3_600_000))) : 'NULL';
    annNotif.push(`UPDATE notifications SET created_at=${q(dt(new Date(at.getTime() + 60_000)))}, read_at=${readAt} WHERE id=${q(id)};`);
  }
  for (let i = 0; i < annNotif.length; i += 80) sql(annNotif.slice(i, i + 80).join('\n'));

  // A few people already closed the current ones on their dashboards.
  const dismissedBy = ['abdullah.qahtani@company.io', 'maha.shammari@company.io', 'fahad.juhani@company.io', 'learner3@company.io'];
  for (const email of dismissedBy) {
    const active = await call(await as(email), 'GET', '/announcements/active');
    if (active.length) await call(await as(email), 'POST', `/announcements/${active[active.length - 1].id}/dismiss`);
  }
  log(`announcements: ${annIds.length}`);

  const summary = sql(`SELECT
    (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM departments), (SELECT COUNT(*) FROM journeys), (SELECT COUNT(*) FROM journey_units),
    (SELECT COUNT(*) FROM unit_quiz_questions), (SELECT COUNT(*) FROM exams), (SELECT COUNT(*) FROM journey_item_attachments),
    (SELECT COUNT(*) FROM packages), (SELECT COUNT(*) FROM learner_journeys), (SELECT COUNT(*) FROM exam_attempts), (SELECT COUNT(*) FROM announcements), (SELECT COUNT(*) FROM certificates), (SELECT COUNT(*) FROM notifications)`).split('\t');
  const labels = ['users', 'departments', 'journeys', 'units', 'quiz questions', 'exams', 'attachments', 'packages', 'assignments', 'exam attempts', 'announcements', 'certificates', 'notifications'];
  log('\nseeded: ' + labels.map((l, i) => `${summary[i]} ${l}`).join(', '));
}

main().catch((e) => { console.error('seed failed:', e.message); process.exit(1); });
