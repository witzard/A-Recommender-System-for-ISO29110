import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { ratingLabel } from './ratings';
import './App.css';

// Knowledge-search parameters (08_Revisions/2026-11-08_QueryParameterGrouping). The backend chooses the queries.
const PARAMS = [
  ['role', 'Role'], ['activity', 'Activity'], ['process', 'Process'], ['basePractice', 'Base Practice'],
  ['outcome', 'Process outcome'], ['workProduct', 'Work product'], ['state', 'Work product state'],
];
const PARAM_LABELS = { ...Object.fromEntries(PARAMS), topic: 'Topic' };
const EMPTY_FORM = { ...Object.fromEntries(PARAMS.map(([k]) => [k, ''])), kwAll: '', kwAny: '' };

const local = (iri) => (iri ? iri.slice(iri.lastIndexOf('#') + 1) : '');
const phrases = (text) => text.split(',').map((s) => s.trim()).filter(Boolean);
const score = (v) => (v === undefined || v === null || v === '' ? '' : Number(v).toFixed(2));

/* ------------------------------------------------------------------ knowledge search */

const short = (text, n = 100) => (text.length > n ? `${text.slice(0, n - 1)}…` : text);

function Select({ id, label, value, values, onChange }) {
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Not selected</option>
        {values.map((v) => <option key={v.value} value={v.value} title={v.label}>{short(v.label)}</option>)}
      </select>
    </>
  );
}

function Text({ id, label, value, onChange, hint }) {
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <input id={id} type="text" value={value} maxLength={300} onChange={(e) => onChange(e.target.value)} />
      {hint && <span className="hint">{hint}</span>}
    </>
  );
}

/** Base Practices and outcomes are limited to the chosen process (their IDs start with it). */
function valuesFor(name, options, form) {
  const values = options[name] || [];
  if ((name === 'basePractice' || name === 'outcome') && form.process) return values.filter((v) => v.value.startsWith(`${form.process}.`));
  return values;
}

function requestFor(form) {
  return { ...Object.fromEntries(PARAMS.map(([k]) => [k, form[k]])), kwAll: phrases(form.kwAll), kwAny: phrases(form.kwAny) };
}

const canRun = (form) => PARAMS.some(([k]) => form[k]) || phrases(form.kwAll).length > 0 || phrases(form.kwAny).length > 0;

function ResultTable({ query, rows }) {
  if (query === 'tasks') {
    return (
      <table>
        <thead><tr><th>Task ID</th><th>Task</th><th>Activity</th><th>Roles</th><th>Output work products</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.task}><td className="id">{r.taskID}</td><td><span className="multiline">{r.taskDesc}</span></td>
            <td>{r.activityName}</td><td>{r.roles}</td><td>{r.outputs}</td></tr>))}</tbody>
      </table>
    );
  }
  if (query === 'workProductStatus') {
    return (
      <table>
        <thead><tr><th>Task / BP</th><th>Description</th><th>Roles</th><th>Work product</th><th>Final state</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.producer + r.wpName + r.stateName}><td className="id">{r.producerID}</td><td><span className="multiline">{r.producerDesc}</span></td>
            <td>{r.roles}</td><td>{r.wpName}</td><td>{r.stateName}</td></tr>))}</tbody>
      </table>
    );
  }
  if (query === 'basePracticeOutcome') {
    return (
      <table>
        <thead><tr><th>BP ID</th><th>Base Practice</th><th>Process outcome</th><th>Outcome</th><th>Work products (advisor table)</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.bp + r.po}><td className="id">{r.bpID}</td><td><span className="multiline">{r.bpDesc}</span></td>
            <td className="id">{r.process} {r.poID})</td><td>{r.poDesc}</td><td>{r.outputs}</td></tr>))}</tbody>
      </table>
    );
  }
  return (
    <table>
      <thead><tr><th>WP ID</th><th>Work product</th><th>Characteristic</th><th>Criterion</th></tr></thead>
      <tbody>{rows.map((r) => (
        <tr key={r.wp + r.wpc}><td className="id">{r.wpID}</td><td>{r.wpName}</td><td className="id">{local(r.wpc)}</td>
          <td><span className="multiline">{r.criterion}</span></td></tr>))}</tbody>
    </table>
  );
}

function KnowledgeSearch() {
  const [options, setOptions] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { api.options().then(setOptions).catch((e) => setError(e.message)); }, []);

  const set = (name, value) => setForm((f) => {
    const next = { ...f, [name]: value };
    if (name === 'process' && value) {
      if (next.basePractice && !next.basePractice.startsWith(`${value}.`)) next.basePractice = '';
      if (next.outcome && !next.outcome.startsWith(`${value}.`)) next.outcome = '';
    }
    return next;
  });

  const run = async () => {
    setBusy(true); setError('');
    try {
      setResult(await api.search(requestFor(form)));
    } catch (e) {
      setError(e.message); setResult(null);
    } finally {
      setBusy(false);
    }
  };

  const total = result ? result.results.reduce((n, r) => n + r.rows.length, 0) : 0;
  return (
    <div className="panel">
      <p className="note">
        Select any of the parameters (values come from the ISO/IEC 29110 knowledge base) and, if needed, a topic. The system runs
        every query that uses the selected parameters, choosing the most specific ones, and shows their results together.
      </p>
      {!options && !error && <p>Loading values from the ontology...</p>}
      {options && (
        <form className="form" onSubmit={(e) => { e.preventDefault(); if (canRun(form)) run(); }}>
          {PARAMS.map(([k, label]) => (
            <Select key={k} id={`p-${k}`} label={label} value={form[k]} values={valuesFor(k, options, form)} onChange={(v) => set(k, v)} />
          ))}
          <Text id="p-all" label="Topic: contains all of" value={form.kwAll} onChange={(v) => set('kwAll', v)} hint="Phrases separated by commas, e.g. project plan, verify" />
          <Text id="p-any" label="Topic: contains any of" value={form.kwAny} onChange={(v) => set('kwAny', v)} hint="Phrases separated by commas" />
          <div className="actions">
            <button type="submit" disabled={busy || !canRun(form)}>{busy ? 'Searching...' : 'Search'}</button>
            <button type="button" onClick={() => { setForm(EMPTY_FORM); setResult(null); setError(''); }}>Clear</button>
          </div>
        </form>
      )}

      {error && <p className="error" role="alert">{error}</p>}
      {result && (
        <div aria-live="polite">
          <h2>Results</h2>
          <p>{result.results.length} quer{result.results.length === 1 ? 'y' : 'ies'} matched the selected parameters; {total} row{total === 1 ? '' : 's'} in total.</p>
          {result.results.map((r) => (
            <section key={r.query}>
              <h3>{r.group} {r.title} ({r.rows.length} row{r.rows.length === 1 ? '' : 's'})</h3>
              <p className="note">Parameters used: {r.parameters.map((p) => PARAM_LABELS[p] || p).join(', ')}</p>
              {r.rows.length > 0 && <div className="table-wrap"><ResultTable query={r.query} rows={r.rows} /></div>}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ assessment */

function Profile({ project }) {
  const rows = [
    ['Enterprise', `${project.enterprise} - ${project.enterpriseName}`],
    ['Project', `${project.id} - ${project.projectName}`],
    ['Description', project.hasProjectDescription],
    ['Number of staff', project.hasNumberofStaff],
    ['Duration', project.hasTotalDuration],
    ['Start date', project.hasStartingDate],
    ['End date', project.hasEndingDate],
    ['Reason', project.hasReason],
    ['Work products not delivered',
      project.excludedWorkProducts.length ? project.excludedWorkProducts.map((w) => `${w.wpID} ${w.wpName}`).join('; ') : 'None'],
  ];
  return (
    <table className="profile">
      <tbody>{rows.map(([k, v]) => <tr key={k}><th>{k}</th><td>{v ?? ''}</td></tr>)}</tbody>
    </table>
  );
}

const RATING_COLUMNS = [
  ['Not_achieved', 'N'], ['Partially_achieved', 'P'], ['Largely_achieved', 'L'], ['Completely_achieved', 'F'],
  ['Not_applicable', 'N/A'], ['Not_rated', 'No %'],
];

function Scores({ data }) {
  return (
    <>
      <h3>Process</h3>
      <table style={{ width: 'auto', minWidth: 360 }}>
        <thead><tr><th>Process</th><th className="num">Score</th></tr></thead>
        <tbody>{data.processScores.map((p) => (
          <tr key={p.process}><td>{p.process}</td><td className="num">{score(p.score)}</td></tr>))}</tbody>
      </table>
      <h3>Work products</h3>
      <p className="note">
        N = Not achieved (0-15%), P = Partially achieved (16-50%), L = Largely achieved (51-90%), F = Completely achieved (91-100%),
        N/A = Not applicable (work product not delivered in this project), No % = no percentage recorded (counted as 0).
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th rowSpan={2}>WP ID</th><th rowSpan={2}>Work product</th><th colSpan={RATING_COLUMNS.length}>Number of characteristics by rating</th>
              <th rowSpan={2} className="num">Score</th></tr>
            <tr>{RATING_COLUMNS.map(([k, label]) => <th key={k} className="num" title={ratingLabel(k)}>{label}</th>)}</tr>
          </thead>
          <tbody>{data.workProducts.map((w) => (
            <tr key={w.wpID}><td className="id">{w.wpID}</td><td>{w.wpName}</td>
              {RATING_COLUMNS.map(([k]) => <td key={k} className="num">{w.ratings[k] || ''}</td>)}
              <td className="num">{score(w.score)}</td></tr>))}</tbody>
        </table>
      </div>
      <details>
        <summary>Base Practices ({data.basePracticeScores.length})</summary>
        <div className="table-wrap">
          <table>
            <thead><tr><th>BP ID</th><th>Base Practice</th><th className="num">Work products</th><th className="num">Score</th></tr></thead>
            <tbody>{data.basePracticeScores.map((b) => (
              <tr key={b.bpID}><td className="id">{b.bpID}</td><td><span className="multiline">{b.description}</span></td>
                <td className="num">{b.nWP}</td><td className="num">{score(b.score)}</td></tr>))}</tbody>
          </table>
        </div>
      </details>
    </>
  );
}

function Gaps({ gaps }) {
  if (gaps.length === 0) return <p>No characteristic has this rating.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>WP ID</th><th>Work product</th><th>Characteristic</th><th>Item</th><th className="num">%</th><th>Rating</th></tr></thead>
        <tbody>{gaps.map((g, i) => (
          <tr key={`${g.characteristic}-${i}`}><td className="id">{g.wpID}</td><td>{g.wpName}</td><td className="id">{g.characteristic}</td>
            <td><span className="multiline">{g.item}</span></td><td className="num">{g.percentage}</td><td>{ratingLabel(g.rating)}</td></tr>))}</tbody>
      </table>
    </div>
  );
}

// Collapsible section whose open state is kept by the parent, so "Expand all" / "Collapse all" can drive it.
function Section({ id, open, setOpen, className, summary, children }) {
  return (
    <details className={className} open={open.has(id)}
      onToggle={(e) => { const o = e.currentTarget.open; setOpen((prev) => {
        if (prev.has(id) === o) return prev;
        const next = new Set(prev); if (o) next.add(id); else next.delete(id); return next;
      }); }}>
      <summary>{summary}</summary>
      {children}
    </details>
  );
}

function Recommendation({ rec, gaps, open, setOpen }) {
  const link = (l) => (l === 'produces' ? `Produces ${rec.wpID}` : l === 'uses' ? `Uses ${rec.wpID} as input` : '');
  const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;
  return (
    <Section id={rec.wpID} open={open} setOpen={setOpen} className="rec"
      summary={<>
        <h3>{rec.wpID} {rec.wpName} <span className="note">({rec.ae})</span></h3>
        <span className="note"> {plural(gaps.length, 'characteristic')}, {plural(rec.tasks.length, 'task')},{' '}
          {plural(rec.outcomes.length, 'outcome')}, {plural(rec.basePractices.length, 'Base Practice')}</span>
      </>}>
      <p>Reason: {gaps.length} characteristic{gaps.length === 1 ? ' is' : 's are'} rated {gaps.length ? [...new Set(gaps.map((g) => ratingLabel(g.rating)))].join(' / ') : ''}:</p>
      <ul>{gaps.map((g, i) => (
        <li key={`${g.characteristic}-${i}`}>{g.characteristic}: <span className="multiline">{g.item}</span> ({g.percentage}%, {ratingLabel(g.rating)})</li>))}</ul>
      <table>
        <thead><tr><th>Recommended task</th><th>Task</th><th>Roles (performing the task)</th><th>Link to the work product</th></tr></thead>
        <tbody>{rec.tasks.map((t) => (
          <tr key={t.taskID}><td className="id">{t.taskID}</td><td><span className="multiline">{t.description}</span></td>
            <td>{t.roles}</td><td>{link(t.link)}</td></tr>))}</tbody>
      </table>
      {rec.outcomes.length > 0 ? (
        <table>
          <thead><tr><th>Process outcome</th><th>Outcome achieved when the work product meets its criteria</th><th>Through Base Practices</th></tr></thead>
          <tbody>{rec.outcomes.map((o) => (
            <tr key={o.outcome}><td className="id">{o.process} {o.outcomeID})</td><td><span className="multiline">{o.description}</span></td>
              <td>{o.basePractices}</td></tr>))}</tbody>
        </table>
      ) : <p className="note">No pre-assessed Base Practice uses {rec.wpID}, so no process outcome is linked.</p>}
      {rec.basePractices.length > 0 ? (
        <table>
          <thead><tr><th>Recommended BP</th><th>Base Practice (has {rec.wpID} as output)</th></tr></thead>
          <tbody>{rec.basePractices.map((b) => (
            <tr key={b.bpID}><td className="id">{b.bpID}</td><td><span className="multiline">{b.description}</span></td></tr>))}</tbody>
        </table>
      ) : <p className="note">No Base Practice has {rec.wpID} as output in the ontology, so no Base Practice is recommended.</p>}
    </Section>
  );
}

const GAP_FILTERS = [['Not_achieved', 'Not achieved'], ['Partially_achieved', 'Partially achieved'], ['', 'Not achieved and partially achieved']];

function GapsAndRecommendations({ data, rating, setRating }) {
  const keep = (g) => !rating || g.rating === rating;
  const gaps = data.gaps.filter(keep);
  const recs = data.recommendations.map((r) => ({ rec: r, gaps: r.gaps.filter(keep) })).filter((x) => x.gaps.length > 0);
  const count = (k) => data.gaps.filter((g) => !k || g.rating === k).length;
  // Sections are collapsed by default so the page stays short; the open state survives a change of the rating filter.
  const [open, setOpen] = useState(() => new Set());
  const allIds = ['gaps', ...recs.map((x) => x.rec.wpID)];
  return (
    <>
      <form className="form" onSubmit={(e) => e.preventDefault()}>
        <label htmlFor="gap-rating">Show gaps rated</label>
        <select id="gap-rating" value={rating} onChange={(e) => setRating(e.target.value)}>
          {GAP_FILTERS.map(([k, label]) => <option key={k || 'all'} value={k}>{label} ({count(k)})</option>)}
        </select>
      </form>
      <div className="section-actions">
        <button type="button" className="plain-button" onClick={() => setOpen(new Set(allIds))}>Expand all</button>
        <button type="button" className="plain-button" onClick={() => setOpen(new Set())}>Collapse all</button>
      </div>
      <Section id="gaps" open={open} setOpen={setOpen} className="gaps"
        summary={<h2>Characteristics {rating ? ratingLabel(rating).toLowerCase() : 'not achieved or partially achieved'} ({gaps.length})</h2>}>
        <Gaps gaps={gaps} />
      </Section>
      <h2>Recommendations ({recs.length} work product{recs.length === 1 ? '' : 's'})</h2>
      <p className="note">
        The tasks, Base Practices and process outcomes below are inferred by the reasoner from the rules of the ISO/IEC 29110
        knowledge base. The roles are those that perform each recommended task.
      </p>
      {recs.length === 0 && <p>No recommendation for the gaps shown.</p>}
      {recs.map((x) => <Recommendation key={x.rec.wpID} rec={x.rec} gaps={x.gaps} open={open} setOpen={setOpen} />)}
    </>
  );
}

function Assessment() {
  const [projects, setProjects] = useState([]);
  const [enterprise, setEnterprise] = useState('');
  const [selected, setSelected] = useState('');
  const [data, setData] = useState(null);
  const [gapRating, setGapRating] = useState('Not_achieved');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback((id) => {
    setSelected(id); setData(null); setError('');
    if (!id) return;
    setLoading(true);
    api.assessment(id).then(setData).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  const chooseEnterprise = useCallback((list, ent) => {
    setEnterprise(ent);
    const first = list.find((p) => p.enterprise === ent);
    load(first ? first.id : '');
  }, [load]);

  useEffect(() => {
    api.projects().then((list) => {
      setProjects(list);
      if (list[0]) chooseEnterprise(list, list[0].enterprise);
    }).catch((e) => setError(e.message));
  }, [chooseEnterprise]);

  const enterprises = [];
  for (const p of projects) {
    if (!enterprises.some((e) => e.id === p.enterprise)) enterprises.push({ id: p.enterprise, name: p.enterpriseName, mock: p.mock });
  }
  const own = projects.filter((p) => p.enterprise === enterprise);

  return (
    <div className="with-side">
      <aside className="side" aria-label="Companies">
        <h2>Companies</h2>
        <ul>
          {enterprises.map((e) => (
            <li key={e.id}>
              <button type="button" aria-current={e.id === enterprise} onClick={() => chooseEnterprise(projects, e.id)}>
                {e.name}<br /><span className="note">{e.id}{e.mock ? ' - mock data' : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>
      <div className="panel main">
        {own.length > 0 && (
          <form className="form" onSubmit={(e) => e.preventDefault()}>
            <label htmlFor="project">Project</label>
            <select id="project" value={selected} onChange={(e) => load(e.target.value)}>
              {own.map((p) => <option key={p.id} value={p.id}>{p.id} {p.projectName}</option>)}
            </select>
          </form>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        {loading && <p>Loading...</p>}
        {data && !loading && (
          <>
            {data.project.mock && <p className="mock">Mock data created to demonstrate the recommender. It is not data of a real company.</p>}
            <h2>Profile</h2>
            <Profile project={data.project} />
            <h2>Scores</h2>
            <p className="note">
              Computed from the recorded percentages (score of each rating: N 0, P 0.3, L 0.7, F 1; an item without a percentage
              counts as 0). Work product score = mean of its characteristics; Base Practice score = mean of its work products;
              process score = mean of its Base Practices.
            </p>
            <Scores data={data} />
            <GapsAndRecommendations data={data} rating={gapRating} setRating={setGapRating} />
          </>
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ page */

export default function App() {
  const [mode, setMode] = useState('search');
  const [report, setReport] = useState(null);

  useEffect(() => { api.reasoningReport().then(setReport).catch(() => setReport(null)); }, []);

  return (
    <div className="page">
      <header className="top">
        <h1>Recommender Framework for ISO/IEC 29110 QA Compliance</h1>
        <span className="sub">Research prototype</span>
      </header>
      <nav className="modes" role="tablist" aria-label="Mode">
        <button type="button" role="tab" aria-selected={mode === 'search'} onClick={() => setMode('search')}>ISO/IEC 29110 knowledge search</button>
        <button type="button" role="tab" aria-selected={mode === 'assessment'} onClick={() => setMode('assessment')}>Assessment results and recommendations</button>
      </nav>
      <main>
        {mode === 'search' ? <KnowledgeSearch /> : <Assessment />}
      </main>
      <footer className="footer">
        {report ? (
          <>Reasoning: {report.reasoner}; run {report.runAt}; {report.consistent ? 'consistent' : 'INCONSISTENT'};{' '}
            {report.rules.filter((r) => r.enabled).length} SWRL rules; inferred {report.inferred.types} class assertions and{' '}
            {report.inferred.objectPropertyValues} object property values.</>
        ) : 'Reasoning report not available.'}
      </footer>
    </div>
  );
}
