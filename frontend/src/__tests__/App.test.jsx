// UI behaviour tests: the real App component, with the API answered from fixtures.json, which is produced by
// tests/reference_api.py running the backend's SPARQL files on the real reasoned dataset.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import fixtures from './fixtures.json';

const calls = [];
vi.mock('../api', () => ({
  API_BASE_URL: 'test',
  describeError: () => 'error',
  api: {
    companies: () => Promise.resolve(fixtures.companies),
    assessment: (id) => { calls.push(id); return Promise.resolve(fixtures.assessments[id]); },
    reasoningStatus: () => Promise.resolve(fixtures.status),
    searchOptions: () => Promise.resolve(fixtures.options),
    search: (p) => { calls.push(p); return Promise.resolve(fixtures.searchExample.response); },
    taskDetails: () => Promise.resolve(fixtures.taskDetailsExample),
  },
}));
import App from '../App';

const sidebar = () => screen.getByRole('complementary', { name: 'Companies' });
const heading = () => screen.getByRole('heading', { level: 1 });

beforeEach(() => { calls.length = 0; });
afterEach(cleanup);

describe('single-page assessor UI', () => {
  it('shows exactly three companies in the sidebar and loads the first one', async () => {
    render(<App />);
    await waitFor(() => expect(heading()).toHaveProperty('textContent', 'Company A'));
    const buttons = within(sidebar()).getAllByRole('button');
    expect(buttons.map((b) => b.textContent)).toEqual(['Company AENT-A', 'Company BENT-B', 'Company CENT-C']);
  });

  it('switches company without navigation and shows that company\'s recommendations', async () => {
    const before = window.location.href;
    render(<App />);
    await screen.findByText(/inferred no recommendation for Company A/);
    fireEvent.click(within(sidebar()).getByText('Company B'));
    await waitFor(() => expect(heading()).toHaveProperty('textContent', 'Company B'));
    expect(screen.getByRole('heading', { name: 'QA practice recommendations (4)' })).toBeTruthy();
    fireEvent.click(within(sidebar()).getByText('Company C'));
    await waitFor(() => expect(heading()).toHaveProperty('textContent', 'Company C'));
    expect(screen.getByRole('heading', { name: 'QA practice recommendations (9)' })).toBeTruthy();
    expect(window.location.href).toBe(before);
    expect(calls).toEqual(['ENT-A', 'ENT-B', 'ENT-C']);
  });

  it('shows rule-derived ratings and the explanation chain of a recommendation', async () => {
    render(<App />);
    await screen.findByText(/inferred no recommendation/);
    fireEvent.click(within(sidebar()).getByText('Company B'));
    await screen.findByRole('heading', { name: 'QA practice recommendations (4)' });
    expect(screen.getAllByText('Inferred by RatingNotAchievedRule').length).toBe(1);
    expect(screen.getByText('No score, so no rule could fire')).toBeTruthy();
    const rec = screen.getByText(/Strengthen SI.BP8/);
    fireEvent.click(rec);
    expect(screen.getAllByText(/Rule ImprovementRecommendationRule/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/RatingNotAchievedRule, ImprovementRecommendationRule|ImprovementRecommendationRule, RatingNotAchievedRule/).length).toBeGreaterThan(0);
  });

  it('offers parameter search (no Q1–Q34 picker) and runs a search', async () => {
    render(<App />);
    await screen.findByRole('combobox', { name: 'Role' });
    expect(screen.queryByText(/^Q\d+$/)).toBeNull();
    expect(document.body.textContent).not.toMatch(/Q1\b.*Q34/);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Role' }));
    fireEvent.click(await screen.findByRole('option', { name: 'Programmer (PR)' }));
    fireEvent.change(screen.getByLabelText('Word in task description'), { target: { value: 'defect' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await screen.findByText('2 tasks match all selected parameters.');
    expect(calls.at(-1)).toMatchObject({ role: 'PR', keyword: 'defect' });
    expect(screen.getByText('SI.4.5')).toBeTruthy();
  });

  it('has no data-entry form for companies or assessments', async () => {
    render(<App />);
    await screen.findByRole('combobox', { name: 'Role' });
    const textboxes = screen.getAllByRole('textbox');
    expect(textboxes).toHaveLength(1);                         // only the search keyword
    expect(textboxes[0].getAttribute('maxlength')).toBe('60');
    expect(screen.queryByRole('button', { name: /add|create|save|submit|register/i })).toBeNull();
  });
});
