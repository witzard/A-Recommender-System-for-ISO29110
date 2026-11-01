import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Button, CircularProgress,
  FormControl, InputLabel, List, ListItemButton, ListItemText, MenuItem, Select, TextField, Tooltip, Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { api, describeError } from './api';
import { RATINGS, ratingInfo } from './ratings';
import './App.css';

/* ------------------------------------------------------------------ small pieces */

function RatingChip({ rating }) {
  const r = ratingInfo(rating);
  return <span className="rating-chip" style={{ color: r.color, background: r.tint }}>{r.label}</span>;
}

/** The 0–100 scale with the four rating bands and the company's score. */
function ScoreStrip({ percentage, rating }) {
  const bands = ['Not_achieved', 'Partially_achieved', 'Largely_achieved', 'Completely_achieved'];
  const value = percentage === undefined || percentage === null ? null : Number(percentage);
  return (
    <div className="strip" aria-label={value === null ? 'No percentage recorded' : `${value} percent`}>
      {bands.map((b) => {
        const r = RATINGS[b];
        const width = r.max - r.min + (b === 'Not_achieved' ? 1 : 0);
        return <span key={b} className={`band ${b === rating ? 'band-on' : ''}`} style={{ flexGrow: width, '--c': r.color, '--t': r.tint }} />;
      })}
      {value !== null && <span className="marker" style={{ left: `${value}%`, '--c': ratingInfo(rating).color }} />}
    </div>
  );
}

function Section({ id, title, intro, children, aside }) {
  return (
    <section className="section" id={id} aria-labelledby={`${id}-title`}>
      <div className="section-head">
        <div>
          <h2 id={`${id}-title`}>{title}</h2>
          {intro && <p className="intro">{intro}</p>}
        </div>
        {aside}
      </div>
      {children}
    </section>
  );
}

/* ------------------------------------------------------------------ company assessment */

function CompanyPanel({ data }) {
  const { company, project, assessment, ratingCounts, results } = data;
  return (
    <>
      <header className="company-head">
        <h1>{company.name}</h1>
        <p className="profile">{company.profile}</p>
        <dl className="facts">
          <div><dt>Project</dt><dd>{project.projectName}</dd></div>
          <div><dt>Period</dt><dd>{project.start} to {project.end}</dd></div>
          <div><dt>Team size</dt><dd>{project.staff}</dd></div>
          <div><dt>Assessed on</dt><dd>{assessment.assessmentDate}</dd></div>
          <div><dt>Assessor</dt><dd>{assessment.consultantName}</dd></div>
        </dl>
        {project.projectDescription && <p className="project-desc">{project.projectDescription}</p>}
      </header>

      <Section id="results" title="Assessment results"
        intro="Each Base Practice was scored as a percentage. The rating was not entered by hand: the SWRL rating rules assigned it when the reasoner ran."
        aside={<div className="counts">{Object.entries(ratingCounts).map(([k, n]) => (
          <span key={k} className="count" style={{ color: ratingInfo(k).color }}><b>{n}</b> {ratingInfo(k).label}</span>))}</div>}>
        <div className="results" role="table" aria-label="Assessment results">
          <div className="row row-head" role="row">
            <span role="columnheader">Base Practice</span><span role="columnheader">Score</span>
            <span role="columnheader">Rating</span><span role="columnheader">Evidence</span>
          </div>
          {results.map((r) => (
            <div className="row" role="row" key={r.result}>
              <span role="cell" className="bp"><b>{r.bpID}</b><small>{r.bpDescription}</small></span>
              <span role="cell" className="score">
                <span className="pct">{r.percentage ?? '—'}{r.percentage !== undefined && '%'}</span>
                <ScoreStrip percentage={r.percentage} rating={r.rating} />
              </span>
              <span role="cell">
                <RatingChip rating={r.rating} />
                <small className="source">
                  {r.ratingSource === 'inferred' ? `Inferred by ${r.ratingRule}` : r.ratingSource === 'asserted' ? 'Entered as data' : 'No score, so no rule could fire'}
                </small>
              </span>
              <span role="cell" className="evidence">{r.evidence}{r.evidenceWp && <small>About: {r.evidenceWp}</small>}</span>
            </div>
          ))}
        </div>
      </Section>
    </>
  );
}

function Recommendations({ data, status }) {
  const recs = data.recommendations;
  return (
    <Section id="recommendations" title={`QA practice recommendations (${recs.length})`}
      intro="A recommendation appears only when the reasoner inferred recommend(assessed entity, practice). Open one to see how it was derived.">
      {recs.length === 0 && (
        <Alert severity="success" variant="outlined">
          The reasoner inferred no recommendation for {data.company.name}: no assessed Base Practice is rated Not achieved or Partially achieved.
        </Alert>
      )}
      {recs.map((r) => (
        <Accordion key={r.practice} disableGutters elevation={0} className="rec">
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <span className="rec-sum">
              <RatingChip rating={r.rating} />
              <span className="rec-title">{r.practiceName}</span>
            </span>
          </AccordionSummary>
          <AccordionDetails>
            <ol className="chain">
              <li><b>Assessment finding.</b> {r.bpID} scored {r.percentage}% for this project.
                {r.evidence && <> Evidence: “{r.evidence}”</>}</li>
              <li><b>Rule {r.ratingRule}</b> fired because the score falls in the {ratingInfo(r.rating).label} band
                ({ratingInfo(r.rating).min}–{ratingInfo(r.rating).max}%). Inferred: <code>{r.bpID} result → {r.rating}</code>.</li>
              <li><b>Rule {r.recommendationRule}</b> fired because the assessed entity has this result, the result assesses {r.bpID},
                and this practice addresses {r.bpID}. Inferred: <code>recommend(assessed entity, practice)</code>.</li>
              <li><b>What to improve.</b> {r.practiceDescription}</li>
            </ol>
            <p className="proof">
              Rules this recommendation depends on (each was switched off in a separate reasoner run and the recommendation disappeared):{' '}
              {r.necessaryRules.join(', ')}. Reasoner: {r.reasoner}, run {new Date(r.runAt).toLocaleString()}.
            </p>
          </AccordionDetails>
        </Accordion>
      ))}
      {status?.runAt && <p className="run-note">Knowledge base reasoned by {status.reasoner}; {status.swrlInferredCount} facts were inferred by SWRL rules.</p>}
    </Section>
  );
}

/* ------------------------------------------------------------------ knowledge search */

const FIELDS = [
  ['process', 'Process'], ['activity', 'Activity'], ['role', 'Role'], ['workProduct', 'Work product'], ['state', 'Work product ends as'],
];

function TaskResult({ task }) {
  const [details, setDetails] = useState(null);
  const [error, setError] = useState('');
  const load = (_, expanded) => {
    if (expanded && !details) api.taskDetails(task.task).then(setDetails).catch((e) => setError(describeError(e)));
  };
  const wpList = (items) => [...new Map(items.map((w) => [w.genericWp || w.wpName, w])).values()];
  return (
    <Accordion disableGutters elevation={0} className="task" onChange={load}>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <span className="task-sum">
          <b>{task.taskID}</b>
          <span>{task.taskDescription.split(':')[0]}</span>
          <small>{task.activityID} {task.activityName}</small>
          <small>Roles: {task.roles || 'none recorded'}</small>
        </span>
      </AccordionSummary>
      <AccordionDetails>
        <p className="task-full">{task.taskDescription}</p>
        {error && <Alert severity="error">{error}</Alert>}
        {!details && !error && <CircularProgress size={18} />}
        {details && (
          <div className="task-grid">
            <div><h4>Uses</h4><ul>{wpList(details.inputs).map((w) => <li key={w.wpName}>{w.genericWp || w.wpName}</li>)}</ul></div>
            <div><h4>Produces</h4><ul>{wpList(details.outputs).map((w) => (
              <li key={w.wpName}>{w.genericWp || w.wpName}{w.stateName && <small> (ends {w.stateName.toLowerCase()})</small>}</li>))}</ul></div>
            <div><h4>Related Base Practices</h4><ul>{details.relatedBasePractices.map((b) => (
              <li key={b.bpID}><Tooltip title={b.bpDescription}><span>{b.bpID.trim()}</span></Tooltip></li>))}</ul></div>
          </div>
        )}
      </AccordionDetails>
    </Accordion>
  );
}

function KnowledgeSearch() {
  const [options, setOptions] = useState(null);
  const [form, setForm] = useState({ process: '', activity: '', role: '', workProduct: '', state: '', keyword: '' });
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { api.searchOptions().then(setOptions).catch((e) => setError(describeError(e))); }, []);

  const processCode = useMemo(() => options?.process.find((p) => p.value === form.process)?.label.match(/\((\w+)\)$/)?.[1], [options, form.process]);
  const choices = (name) => (name === 'activity' && processCode ? options.activity.filter((a) => a.process === processCode) : options?.[name] || []);
  const set = (name, value) => setForm((f) => ({ ...f, [name]: value, ...(name === 'process' ? { activity: '' } : {}) }));
  const anySet = Object.values(form).some((v) => v.trim());

  const run = async () => {
    setBusy(true); setError('');
    try { setResult(await api.search(form)); } catch (e) { setError(describeError(e)); setResult(null); } finally { setBusy(false); }
  };

  return (
    <Section id="search" title="Search ISO/IEC 29110 knowledge"
      intro="Choose any combination of parameters. Every option comes from the ontology; fields you leave empty are not used.">
      {!options && !error && <CircularProgress size={22} />}
      {options && (
        <form className="search-form" onSubmit={(e) => { e.preventDefault(); if (anySet) run(); }}>
          {FIELDS.map(([name, label]) => (
            <FormControl key={name} size="small">
              <InputLabel id={`${name}-l`}>{label}</InputLabel>
              <Select labelId={`${name}-l`} label={label} value={form[name]} onChange={(e) => set(name, e.target.value)}>
                <MenuItem value=""><em>Any</em></MenuItem>
                {choices(name).map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
              </Select>
            </FormControl>
          ))}
          <TextField size="small" label="Word in task description" value={form.keyword} slotProps={{ htmlInput: { maxLength: 60 } }}
            onChange={(e) => set('keyword', e.target.value)} />
          <Button type="submit" variant="contained" disableElevation disabled={!anySet || busy}>{busy ? 'Searching…' : 'Search'}</Button>
          <Button type="button" onClick={() => { setForm({ process: '', activity: '', role: '', workProduct: '', state: '', keyword: '' }); setResult(null); }}>Clear</Button>
        </form>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {result && (
        <div className="search-results" aria-live="polite">
          <p className="intro">{result.results.length === 0
            ? 'No task matches all selected parameters. Remove one parameter to widen the search.'
            : `${result.results.length} task${result.results.length > 1 ? 's' : ''} match all selected parameters.`}</p>
          {result.results.map((t) => <TaskResult key={t.task} task={t} />)}
        </div>
      )}
    </Section>
  );
}

/* ------------------------------------------------------------------ page */

export default function App() {
  const [companies, setCompanies] = useState([]);
  const [selected, setSelected] = useState('');
  const [data, setData] = useState(null);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const select = useCallback((id) => {
    setSelected(id); setLoading(true); setError('');
    api.assessment(id).then(setData).catch((e) => { setError(describeError(e)); setData(null); }).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    api.companies().then((list) => { setCompanies(list); if (list[0]) select(list[0].id); }).catch((e) => setError(describeError(e)));
    api.reasoningStatus().then(setStatus).catch(() => {});
  }, [select]);

  return (
    <div className="shell">
      <aside className="sidebar" aria-label="Companies">
        <p className="brand">ISO/IEC 29110<br /><span>QA recommendation</span></p>
        <Typography component="h2" className="side-title">Assessed companies</Typography>
        <List dense disablePadding>
          {companies.map((c) => (
            <ListItemButton key={c.id} selected={c.id === selected} onClick={() => select(c.id)} className="company-btn">
              <ListItemText primary={c.name} secondary={c.id} />
            </ListItemButton>
          ))}
        </List>
        <nav className="jump" aria-label="On this page">
          <a href="#results">Results</a><a href="#recommendations">Recommendations</a><a href="#search">Knowledge search</a>
        </nav>
        <p className="side-note">Company data is maintained in Protégé and loaded into the knowledge base. It cannot be edited here.</p>
      </aside>
      <main className="main">
        {error && <Alert severity="error" className="top-error">{error}</Alert>}
        {loading && <div className="loading"><CircularProgress size={24} /> Loading assessment…</div>}
        {data && !loading && <><CompanyPanel data={data} /><Recommendations data={data} status={status} /></>}
        <KnowledgeSearch />
      </main>
    </div>
  );
}
