export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');

async function request(path, options) {
  let response;
  try {
    response = await fetch(API_BASE_URL + path, options);
  } catch {
    throw new Error(`The backend at ${API_BASE_URL} is not reachable. Start the Spring Boot service and try again.`);
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || `The request failed (HTTP ${response.status}).`);
  return body;
}

const post = (path, data) => request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });

export const api = {
  options: () => request('/search/options'),
  search: (params) => post('/search', params),
  projects: () => request('/projects'),
  assessment: (id) => request(`/projects/${encodeURIComponent(id)}/assessment`),
  reasoningReport: () => request('/reasoning-report'),
};
