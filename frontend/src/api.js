import axios from 'axios';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const http = axios.create({ baseURL: API_BASE_URL, timeout: 30000 });

export function describeError(error) {
  if (!error?.response) return `The backend at ${API_BASE_URL} is not reachable. Start the Spring Boot service and try again.`;
  return error.response.data?.error || `The request failed (HTTP ${error.response.status}).`;
}

export const api = {
  companies: () => http.get('/companies').then((r) => r.data),
  assessment: (id) => http.get(`/companies/${encodeURIComponent(id)}/assessment`).then((r) => r.data),
  reasoningStatus: () => http.get('/reasoning/status').then((r) => r.data),
  searchOptions: () => http.get('/search/options').then((r) => r.data),
  search: (params) => http.post('/search', params).then((r) => r.data),
  taskDetails: (iri) => http.get('/search/task', { params: { iri } }).then((r) => r.data),
};
