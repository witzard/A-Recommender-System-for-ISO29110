// UI tests: the real App component with the API answered from fixtures.json (responses of the backend services
// computed on ontology/ISO29110V2_rev06b.ttl and kb/inferred.nt).
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import fixtures from './fixtures.json';

const calls = [];
vi.mock('../api', () => ({
  API_BASE_URL: 'test',
  api: {
    options: () => Promise.resolve(fixtures.options),
    search: (template, params) => { calls.push([template, params]); return Promise.resolve(fixtures.searchT1); },
    projects: () => Promise.resolve(fixtures.projects),
    assessment: (id) => { calls.push(['assessment', id]); return Promise.resolve(fixtures.assessments[id]); },
    reasoningReport: () => Promise.resolve(fixtures.reasoningReport),
  },
}));
import App from '../App';

beforeEach(() => { calls.length = 0; });
afterEach(cleanup);

describe('knowledge search', () => {
  it('offers the drop-down values read from the ontology', async () => {
    render(<App />);
    const role = await screen.findByLabelText('Role');
    expect(within(role).getAllByRole('option')).toHaveLength(1 + 7);
    expect(within(screen.getByLabelText('Activity')).getAllByRole('option')).toHaveLength(1 + 10);
    expect(within(screen.getByLabelText('Work product (input or output)')).getAllByRole('option')).toHaveLength(1 + 23);
  });

  it('sends T1 parameters and shows the rows and the SPARQL query', async () => {
    render(<App />);
    fireEvent.change(await screen.findByLabelText('Role'), { target: { value: 'Technical leader' } });
    fireEvent.change(screen.getByLabelText('Task contains all of'), { target: { value: 'project plan' } });
    fireEvent.change(screen.getByLabelText('Task contains any of'), { target: { value: 'verify, review' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await screen.findByText(`${fixtures.searchT1.rows.length} rows.`);
    expect(calls[0]).toEqual(['T1', { role: 'Technical leader', activity: '', workProduct: '', kwAll: ['project plan'], kwAny: ['verify', 'review'] }]);
    expect(screen.getByText('SPARQL query')).toBeTruthy();
    expect(screen.getByText(fixtures.searchT1.rows[0].taskID)).toBeTruthy();
  });

  it('an example question fills the template parameters', async () => {
    render(<App />);
    await screen.findByLabelText('Role');
    fireEvent.click(screen.getByText(/As a PM, how does the team know project details/));
    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toEqual(['T1', { role: '', activity: '', workProduct: '', kwAll: ['project plan'], kwAny: ['work team', 'deliverables'] }]);
  });
});

describe('assessment results and recommendations', () => {
  const open = async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: 'Assessment results and recommendations' }));
    return screen.findByLabelText('Enterprise / project');
  };

  it('lists the three projects and loads the first one', async () => {
    const select = await open();
    expect(within(select).getAllByRole('option')).toHaveLength(3);
    await screen.findByText('Recommendations (1 work product)');
    expect(calls).toContainEqual(['assessment', 'PJ.01']);
    expect(screen.getByText('0.4040')).toBeTruthy();
    expect(screen.getByText('0.0152')).toBeTruthy();
  });

  it('marks mock data and shows the excluded work products', async () => {
    const select = await open();
    await screen.findByText('Recommendations (1 work product)');
    fireEvent.change(select, { target: { value: 'PJ.02' } });
    await screen.findByText('Recommendations (12 work products)');
    expect(screen.getByText(/It is not data of a real company/)).toBeTruthy();
    expect(screen.getByText('WP.08 Product Operation Guide; WP.18 Software User Documentation')).toBeTruthy();
    expect(screen.getByText('0.5596')).toBeTruthy();
  });

  it('shows for WP.02 of PJ.01 the 7 tasks inferred by the reasoner and the reason', async () => {
    await open();
    await screen.findByText('Recommendations (1 work product)');
    const rec = screen.getByRole('heading', { name: /WP\.02 Agreement/ }).parentElement;
    const tasks = within(rec).getAllByRole('table')[0];
    expect(within(tasks).getAllByRole('row')).toHaveLength(1 + 7);
    expect(within(rec).getByText(/0%, Not achieved/)).toBeTruthy();
    expect(within(tasks).getAllByText('Uses WP.02 as input')).toHaveLength(7);
  });
});
