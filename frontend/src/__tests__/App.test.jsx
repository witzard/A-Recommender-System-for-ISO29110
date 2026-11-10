// UI tests: the real App component with the API answered from fixtures.json (responses of the backend services
// computed on ontology/ISO29110V2_rev09.ttl and kb/inferred.nt; see fixtures._note).
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import fixtures from './fixtures.json';

const calls = [];
vi.mock('../api', () => ({
  API_BASE_URL: 'test',
  api: {
    options: () => Promise.resolve(fixtures.options),
    search: (params) => { calls.push(['search', params]); return Promise.resolve(fixtures.search); },
    projects: () => Promise.resolve(fixtures.projects),
    assessment: (id) => { calls.push(['assessment', id]); return Promise.resolve(fixtures.assessments[id]); },
    reasoningReport: () => Promise.resolve(fixtures.reasoningReport),
  },
}));
import App from '../App';

beforeEach(() => { calls.length = 0; });
afterEach(cleanup);

describe('knowledge search by parameters', () => {
  it('offers the parameter values read from the ontology and no example questions', async () => {
    render(<App />);
    const role = await screen.findByLabelText('Role');
    expect(within(role).getAllByRole('option')).toHaveLength(1 + 7);
    expect(within(screen.getByLabelText('Activity')).getAllByRole('option')).toHaveLength(1 + 10);
    expect(within(screen.getByLabelText('Process')).getAllByRole('option')).toHaveLength(1 + 2);
    expect(within(screen.getByLabelText('Base Practice')).getAllByRole('option')).toHaveLength(1 + 50);
    expect(within(screen.getByLabelText('Process outcome')).getAllByRole('option')).toHaveLength(1 + 28);
    expect(within(screen.getByLabelText('Work product')).getAllByRole('option')).toHaveLength(1 + 23);
    expect(within(screen.getByLabelText('Work product state')).getAllByRole('option')).toHaveLength(1 + 24);
    expect(screen.queryByText('Example questions')).toBeNull();
    expect(screen.queryByRole('tab', { name: /T1/ })).toBeNull();
  });

  it('limits Base Practices and outcomes to the chosen process', async () => {
    render(<App />);
    fireEvent.change(await screen.findByLabelText('Process'), { target: { value: 'SI' } });
    expect(within(screen.getByLabelText('Base Practice')).getAllByRole('option')).toHaveLength(1 + 23);
    expect(within(screen.getByLabelText('Process outcome')).getAllByRole('option')).toHaveLength(1 + 13);
  });

  it('sends only parameters and shows the results of every matched query together', async () => {
    render(<App />);
    fireEvent.change(await screen.findByLabelText('Role'), { target: { value: 'Technical leader' } });
    fireEvent.change(screen.getByLabelText('Work product'), { target: { value: 'Project Plan' } });
    fireEvent.change(screen.getByLabelText('Topic: contains all of'), { target: { value: 'verify' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await screen.findByText('3 queries matched the selected parameters; 9 rows in total.');
    expect(calls[0]).toEqual(['search', { role: 'Technical leader', activity: '', process: '', basePractice: '', outcome: '',
      workProduct: 'Project Plan', state: '', kwAll: ['verify'], kwAny: [] }]);
    expect(screen.getByRole('heading', { name: 'G1 Tasks (5 rows)' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'G2 Tasks and Base Practices that output the work product (3 rows)' })).toBeTruthy();
    const g3 = screen.getByRole('heading', { name: 'G3 Base Practices, process outcomes and work products (advisor table) (1 row)' }).parentElement;
    expect(within(g3).getByText('PM.BP13')).toBeTruthy();
    expect(within(g3).getByText('PM c)')).toBeTruthy();
    expect(screen.queryByText('SPARQL query')).toBeNull();
  });
});

const processScores = () => {
  const table = screen.getByRole('columnheader', { name: 'Base Practices' }).closest('table');
  return within(table).getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell').map((c) => c.textContent));
};

describe('assessment results and recommendations', () => {
  const open = async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: 'Assessment results and recommendations' }));
    return screen.findByRole('complementary', { name: 'Companies' });
  };

  it('lists the three companies in the side bar and loads the first project', async () => {
    const side = await open();
    expect(within(side).getAllByRole('button')).toHaveLength(3);
    await screen.findByText('Recommendations (1 work product)');
    expect(calls).toContainEqual(['assessment', 'PJ.01']);
    expect(within(screen.getByLabelText('Project')).getAllByRole('option')).toHaveLength(1);
    expect(processScores()).toEqual([['PM', '27', '0.40'], ['SI', '23', '0.02']]);
  });

  it('marks mock data and shows the work products not delivered', async () => {
    const side = await open();
    await screen.findByText('Recommendations (1 work product)');
    fireEvent.click(within(side).getByText('Mock Company B (start-up, mock data)'));
    await screen.findByText('Recommendations (3 work products)');
    expect(screen.getByText(/It is not data of a real company/)).toBeTruthy();
    expect(screen.getByText('WP.08 Product Operation Guide; WP.18 Software User Documentation')).toBeTruthy();
    expect(processScores()).toEqual([['PM', '27', '0.56'], ['SI', '23', '0.52']]);
  });

  it('shows for WP.02 of PJ.01 the 7 tasks inferred by the reasoner and the reason', async () => {
    await open();
    await screen.findByText('Recommendations (1 work product)');
    fireEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    const rec = screen.getByRole('heading', { name: /WP\.02 Agreement/ }).closest('details');
    const tasks = within(rec).getAllByRole('table')[0];
    expect(within(tasks).getAllByRole('row')).toHaveLength(1 + 7);
    expect(within(rec).getByText(/0%, Not achieved/)).toBeTruthy();
    expect(within(tasks).getAllByText('Uses WP.02 as input')).toHaveLength(7);
    expect(within(rec).queryByText('Recommended role')).toBeNull();
  });

  it('shows the process outcomes linked to WP.02 of PJ.01 (RecommendOutcomeRule)', async () => {
    await open();
    await screen.findByText('Recommendations (1 work product)');
    fireEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    const rec = screen.getByRole('heading', { name: /WP\.02 Agreement/ }).closest('details');
    const outcomes = within(rec).getByRole('columnheader', { name: 'Process outcome' }).closest('table');
    const rows = within(outcomes).getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell').map((c) => c.textContent));
    expect(rows.map((r) => [r[0], r[2]])).toEqual([['PM a)', 'PM.BP1'], ['PM b)', 'PM.BP3, PM.BP4, PM.BP5, PM.BP6, PM.BP7, PM.BP8']]);
  });

  it('separates gaps rated Not achieved and Partially achieved', async () => {
    const side = await open();
    await screen.findByText('Recommendations (1 work product)');
    fireEvent.click(within(side).getByText('Mock Company B (start-up, mock data)'));
    await screen.findByText('Recommendations (3 work products)');
    expect(screen.getByText('Characteristics not achieved (18)')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Show gaps rated'), { target: { value: 'Partially_achieved' } });
    expect(screen.getByText('Characteristics partially achieved (63)')).toBeTruthy();
    expect(screen.getByText('Recommendations (11 work products)')).toBeTruthy();
    const sections = () => [...document.querySelectorAll('details.gaps, details.rec')];
    expect(sections()).toHaveLength(1 + 11);
    expect(sections().filter((d) => d.open)).toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(sections().filter((d) => d.open)).toHaveLength(1 + 11);
    fireEvent.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(sections().filter((d) => d.open)).toHaveLength(0);
    fireEvent.change(screen.getByLabelText('Show gaps rated'), { target: { value: '' } });
    expect(screen.getByText('Recommendations (12 work products)')).toBeTruthy();
  });
});
