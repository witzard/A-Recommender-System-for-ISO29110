import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { RATING_ORDER, ratingLabel } from './ratings';
import examples from './examples.json';
import './App.css';

const TEMPLATES = [
  ['T1', 'T1 Task finder'],
  ['T2', 'T2 Work product status'],
  ['T3', 'T3 Work product criteria'],
];
const EMPTY_FORMS = {
  T1: { role: '', activity: '', workProduct: '', kwAll: '', kwAny: '' },
  T2: { workProduct: '', state: '', kwProducer: '' },
  T3: { workProduct: '' },
};

const local = (iri) => (iri ? iri.slice(iri.lastIndexOf('#') + 1) : '');
const phrases = (text) => text.split(',').map((s) => s.trim()).filter(Boolean);
const score = (v) => (v === undefined || v === null || v === '' ? '' : Number(v).toFixed(4));

/* ------------------------------------------------------------------ knowledge search */

function Select({ id, label, value, values, onChange, anyLabel = 'Any' }) {
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{anyLabel}</option>
        {values.map((v) => <option key={v} value={v}>{v}</option>)}
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

function SearchForm({ template, form, options, set }) {
  if (template === 'T1') {
    return (
      <>
        <Select id="t1-role" label="Role" value={form.role} values={options.role} onChange={(v) => set('role', v)} />
        <Select id="t1-activity" label="Activity" value={form.activity} values={options.activity} onChange={(v) => set('activity', v)} />
        <Select id="t1-wp" label="Work product (input or output)" value={form.workProduct} values={options.workProduct} onChange={(v) => set('workProduct', v)} />
        <Text id="t1-all" label="Task contains all of" value={form.kwAll} onChange={(v) => set('kwAll', v)} hint="Phrases separated by commas, e.g. project plan, verify" />
        <Text id="t1-any" label="Task contains any of" value={form.kwAny} onChange={(v) => set('kwAny', v)} hint="Phrases separated by commas" />
      </>
    );
  }
  if (template === 'T2') {
    return (
      <>
        <Select id="t2-wp" label="Work product" value={form.workProduct} values={options.workProduct} onChange={(v) => set('workProduct', v)} anyLabel="Select" />
        <Select id="t2-state" label="Final state" value={form.state} values={options.state} onChange={(v) => set('state', v)} anyLabel="Select" />
        <Text id="t2-kw" label="Producer contains" value={form.kwProducer} onChange={(v) => set('kwProducer', v)} hint="Optional phrase in the task or Base Practice description, e.g. verify" />
      </>
    );
  }
  return <Select id="t3-wp" label="Work product" value={form.workProduct} values={options.workProduct} onChange={(v) => set('workProduct', v)} anyLabel="All work products" />;
}

function requestFor(template, form) {
  if (template === 'T1') return { role: form.role, activity: form.activity, workProduct: form.workProduct, kwAll: phrases(form.kwAll), kwAny: phrases(form.kwAny) };
  if (template === 'T2') return { workProduct: form.workProduct, state: form.state, kwProducer: form.kwProducer.trim() };
  return { workProduct: form.workProduct };
}

function canRun(template, form) {
  if (template === 'T1') return Boolean(form.role || form.activity || form.workProduct || phrases(form.kwAll).length || phrases(form.kwAny).length);
  if (template === 'T2') return Boolean(form.workProduct && form.state);
  return true;
}

function formFromExample(example) {
  const p = example.params;
  if (example.template === 'T1') {
    return { ...EMPTY_FORMS.T1, role: p.role || '', activity: p.activity || '', workProduct: p.workProduct || '',
      kwAll: (p.kwAll || []).join(', '), kwAny: (p.kwAny || []).join(', ') };
  }
  if (example.template === 'T2') return { workProduct: p.workProduct || '', state: p.state || '', kwProducer: p.kwProducer || '' };
  return { workProduct: p.workProduct || '' };
}

function ResultTable({ template, rows }) {
  if (template === 'T1') {
    return (
      <table>
        <thead><tr><th>Task ID</th><th>Task</th><th>Activity</th><th>Roles</th><th>Output work products</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.task}><td className="id">{r.taskID}</td><td><span className="multiline">{r.taskDesc}</span></td>
            <td>{r.activityName}</td><td>{r.roles}</td><td>{r.outputs}</td></tr>))}</tbody>
      </table>
    );
  }
  if (template === 'T2') {
    return (
      <table>
        <thead><tr><th>Task / BP</th><th>Description</th><th>Roles</th><th>Work product</th><th>Final state</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.producer + r.wpName + r.stateName}><td className="id">{r.producerID}</td><td><span className="multiline">{r.producerDesc}</span></td>
            <td>{r.roles}</td><td>{r.wpName}</td><td>{r.stateName}</td></tr>))}</tbody>
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
  const [template, setTemplate] = useState('T1');
  const [forms, setForms] = useState(EMPTY_FORMS);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { api.options().then(setOptions).catch((e) => setError(e.message)); }, []);

  const form = forms[template];
  const set = (name, value) => setForms((f) => ({ ...f, [template]: { ...f[template], [name]: value } }));

  const run = async (t, f, label) => {
    setBusy(true); setError('');
    try {
      const response = await api.search(t, requestFor(t, f));
      setResult({ ...response, label });
    } catch (e) {
      setError(e.message); setResult(null);
    } finally {
      setBusy(false);
    }
  };

  const applyExample = (example) => {
    const f = formFromExample(example);
    setTemplate(example.template);
    setForms((all) => ({ ...all, [example.template]: f }));
    run(example.template, f, `${example.id}: ${example.question}`);
  };

  return (
    <div className="panel">
      <p className="note">
        Choose values and phrases; the page builds a SPARQL query from the selected template and runs it on the ontology.
        The values in each list are read from the ontology.
      </p>
      <div className="tabs" role="tablist" aria-label="Query templates">
        {TEMPLATES.map(([id, label]) => (
          <button key={id} type="button" role="tab" aria-selected={template === id}
            onClick={() => { setTemplate(id); setResult(null); setError(''); }}>{label}</button>
        ))}
      </div>
      {!options && !error && <p>Loading values from the ontology...</p>}
      {options && (
        <form className="form" onSubmit={(e) => { e.preventDefault(); if (canRun(template, form)) run(template, form, ''); }}>
          <SearchForm template={template} form={form} options={options} set={set} />
          <div className="actions">
            <button type="submit" disabled={busy || !canRun(template, form)}>{busy ? 'Searching...' : 'Search'}</button>
            <button type="button" onClick={() => { setForms((f) => ({ ...f, [template]: EMPTY_FORMS[template] })); setResult(null); }}>Clear</button>
          </div>
        </form>
      )}

      <details>
        <summary>Example questions (the 34 competency questions used to evaluate the ontology)</summary>
        <p className="note">Selecting a question fills in the template parameters used for it in the evaluation and runs the search.</p>
        <ul className="examples">
          {examples.map((x) => (
            <li key={x.id}><button type="button" onClick={() => applyExample(x)}>
              <span className="qid">{x.id}</span>{x.question} <span className="note">({x.template})</span>
            </button></li>
          ))}
        </ul>
      </details>

      {error && <p className="error" role="alert">{error}</p>}
      {result && (
        <div aria-live="polite">
          <h2>Results</h2>
          {result.label && <p>{result.label}</p>}
          <p>{result.rows.length} row{result.rows.length === 1 ? '' : 's'}.</p>
          {result.rows.length > 0 && <div className="table-wrap"><ResultTable template={result.template} rows={result.rows} /></div>}
          <details>
            <summary>SPARQL query</summary>
            <pre>{result.sparql}</pre>
          </details>
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
    ['Work products not delivered (excludesWorkProduct)',
      project.excludedWorkProducts.length ? project.excludedWorkProducts.map((w) => `${w.wpID} ${w.wpName}`).join('; ') : 'None'],
  ];
  return (
    <table className="profile">
      <tbody>{rows.map(([k, v]) => <tr key={k}><th>{k}</th><td>{v ?? ''}</td></tr>)}</tbody>
    </table>
  );
}

function ratingSummary(ratings) {
  return RATING_ORDER.filter((k) => ratings[k]).map((k) => `${ratingLabel(k)} ${ratings[k]}`).join('; ');
}

function Scores({ data }) {
  return (
    <>
      <h3>Process</h3>
      <table style={{ width: 'auto', minWidth: 360 }}>
        <thead><tr><th>Process</th><th className="num">Base Practices</th><th className="num">Score</th></tr></thead>
        <tbody>{data.processScores.map((p) => (
          <tr key={p.process}><td>{p.process}</td><td className="num">{p.nBP}</td><td className="num">{score(p.score)}</td></tr>))}</tbody>
      </table>
      <h3>Work products</h3>
      <div className="table-wrap">
        <table>
          <thead><tr><th>WP ID</th><th>Work product</th><th>Characteristics by rating</th><th className="num">Score</th></tr></thead>
          <tbody>{data.workProducts.map((w) => (
            <tr key={w.wpID}><td className="id">{w.wpID}</td><td>{w.wpName}</td><td>{ratingSummary(w.ratings)}</td>
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
  if (gaps.length === 0) return <p>No characteristic is rated Not achieved or Partially achieved.</p>;
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

function Recommendation({ rec }) {
  const link = (l) => (l === 'produces' ? `Produces ${rec.wpID}` : l === 'uses' ? `Uses ${rec.wpID} as input` : '');
  return (
    <div className="rec">
      <h3>{rec.wpID} {rec.wpName} <span className="note">({rec.ae})</span></h3>
      <p>Reason: {rec.gaps.length} characteristic{rec.gaps.length === 1 ? ' is' : 's are'} not achieved or partially achieved:</p>
      <ul>{rec.gaps.map((g, i) => (
        <li key={`${g.characteristic}-${i}`}>{g.characteristic}: <span className="multiline">{g.item}</span> ({g.percentage}%, {ratingLabel(g.rating)})</li>))}</ul>
      <table>
        <thead><tr><th>Recommended task</th><th>Task</th><th>Roles</th><th>Link to the work product</th></tr></thead>
        <tbody>{rec.tasks.map((t) => (
          <tr key={t.taskID}><td className="id">{t.taskID}</td><td><span className="multiline">{t.description}</span></td>
            <td>{t.roles}</td><td>{link(t.link)}</td></tr>))}</tbody>
      </table>
      <table>
        <thead><tr><th>Recommended role</th><th>Recommended tasks performed by the role</th></tr></thead>
        <tbody>{rec.roles.map((r) => (
          <tr key={r.roleName}><td>{r.roleName} ({r.abbreviation})</td><td>{r.tasks}</td></tr>))}</tbody>
      </table>
      {rec.basePractices.length > 0 ? (
        <table>
          <thead><tr><th>Recommended BP</th><th>Base Practice (has {rec.wpID} as output)</th></tr></thead>
          <tbody>{rec.basePractices.map((b) => (
            <tr key={b.bpID}><td className="id">{b.bpID}</td><td><span className="multiline">{b.description}</span></td></tr>))}</tbody>
        </table>
      ) : <p className="note">No Base Practice has {rec.wpID} as output in the ontology, so no Base Practice is recommended.</p>}
    </div>
  );
}

function Assessment() {
  const [projects, setProjects] = useState([]);
  const [selected, setSelected] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback((id) => {
    setSelected(id); setData(null); setError('');
    if (!id) return;
    setLoading(true);
    api.assessment(id).then(setData).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    api.projects().then((list) => {
      setProjects(list);
      if (list[0]) load(list[0].id);
    }).catch((e) => setError(e.message));
  }, [load]);

  return (
    <div className="panel">
      <form className="form" onSubmit={(e) => e.preventDefault()}>
        <label htmlFor="project">Enterprise / project</label>
        <select id="project" value={selected} onChange={(e) => load(e.target.value)}>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>{p.enterprise} {p.enterpriseName} / {p.id} {p.projectName}{p.mock ? ' [MOCK DATA]' : ''}</option>
          ))}
        </select>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {loading && <p>Loading...</p>}
      {data && !loading && (
        <>
          {data.project.mock && <p className="mock">Mock data created to demonstrate the recommender. It is not data of a real company.</p>}
          <h2>Profile</h2>
          <Profile project={data.project} />
          <h2>Scores</h2>
          <p className="note">
            Computed with the SPARQL queries Q1-Q3 v3 from the recorded percentages (score of each rating: N 0, P 0.3, L 0.7, F 1;
            an item without a percentage counts as 0). Work product score = mean of its characteristics; Base Practice score = mean of
            its work products; process score = mean of its Base Practices.
          </p>
          <Scores data={data} />
          <h2>Characteristics not achieved ({data.gaps.length})</h2>
          <Gaps gaps={data.gaps} />
          <h2>Recommendations ({data.recommendations.length} work product{data.recommendations.length === 1 ? '' : 's'})</h2>
          <p className="note">
            Tasks, roles and Base Practices below are the facts recommendTask, recommendRole and recommendBasePractice inferred by the
            Pellet reasoner from the SWRL rules of the ontology. The page only displays them.
          </p>
          {data.recommendations.length === 0 && <p>The reasoner inferred no recommendation for this project.</p>}
          {data.recommendations.map((r) => <Recommendation key={r.wpID} rec={r} />)}
        </>
      )}
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
