import { useState } from 'react';
import axios from 'axios';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';

const API_URL = 'http://localhost:8080/api/recommend';

const initialForm = {
  process: 'PM',
  role: 'Project manager',
  topic: 'Project Plan',
  situation: 'NOT_APPROVED',
};

function App() {
  const [form, setForm] = useState(initialForm);
  const [results, setResults] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((current) => ({
      ...current,
      [name]: value,
    }));

    setError('');
  };

  const handleRecommend = async () => {
    setLoading(true);
    setError('');
    setResults(null);

    try {
      const response = await axios.post(API_URL, form);
      setResults(response.data.results);
    } catch (err) {
      const message =
        err.response?.data?.error ||
        err.message ||
        'Unable to connect to the recommendation service.';

      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box className="app-shell">
      <Box className="hero-section">
        <Typography variant="overline" className="eyebrow">
          ISO/IEC 29110 · Quality Assurance
        </Typography>

        <Typography component="h1" className="page-title">
          Recommendation System
        </Typography>

        <Typography className="page-subtitle">
          Select a project context to retrieve relevant Base Practices and
          supporting Tasks from the ISO/IEC 29110 ontology.
        </Typography>
      </Box>

      <Card className="input-card" elevation={0}>
        <CardContent>
          <Typography variant="h5" className="section-title">
            Project Context
          </Typography>

          <Typography className="section-description">
            Provide four structured inputs for the recommendation query.
          </Typography>

          <Divider sx={{ my: 3 }} />

          <Box className="input-grid">
            <FormControl fullWidth>
              <InputLabel id="process-label">Process</InputLabel>
              <Select
                labelId="process-label"
                name="process"
                value={form.process}
                label="Process"
                onChange={handleChange}
              >
                <MenuItem value="PM">Project Management (PM)</MenuItem>
                <MenuItem value="SI">Software Implementation (SI)</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="role-label">Role</InputLabel>
              <Select
                labelId="role-label"
                name="role"
                value={form.role}
                label="Role"
                onChange={handleChange}
              >
                <MenuItem value="Project manager">Project manager</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="topic-label">Topic / Work Product</InputLabel>
              <Select
                labelId="topic-label"
                name="topic"
                value={form.topic}
                label="Topic / Work Product"
                onChange={handleChange}
              >
                <MenuItem value="Project Plan">Project Plan</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="situation-label">Situation</InputLabel>
              <Select
                labelId="situation-label"
                name="situation"
                value={form.situation}
                label="Situation"
                onChange={handleChange}
              >
                <MenuItem value="NOT_APPROVED">
                  Not approved / requires approval
                </MenuItem>
                <MenuItem value="APPROVED">Already approved</MenuItem>
              </Select>
            </FormControl>
          </Box>

          <Button
            className="recommend-button"
            variant="contained"
            size="large"
            onClick={handleRecommend}
            disabled={loading}
          >
            {loading ? (
              <>
                <CircularProgress size={20} color="inherit" />
                <span>Retrieving recommendations...</span>
              </>
            ) : (
              'Get Recommendations'
            )}
          </Button>
        </CardContent>
      </Card>

      {error && (
        <Alert severity="error" className="result-card">
          {error}
        </Alert>
      )}

      {results && (
        <Stack spacing={2.5} className="results-section">
          <Box>
            <Typography variant="h5" className="section-title">
              Recommendation Results
            </Typography>
            <Typography className="section-description">
              Retrieved from the ISO/IEC 29110 ontology through the backend
              SPARQL service.
            </Typography>
          </Box>

          <Card className="result-card" elevation={0}>
            <CardContent>
              <Typography variant="h6" className="result-heading">
                Base Practices
              </Typography>

              {results.basePractices?.length ? (
                <Stack spacing={1.5}>
                  {results.basePractices.map((item) => (
                    <Box key={item.bpID} className="result-item">
                      <Typography className="result-id">
                        {item.bpID}
                      </Typography>
                      <Typography>{item.bpDesc}</Typography>
                    </Box>
                  ))}
                </Stack>
              ) : (
                <Typography className="empty-result">
                  No matching Base Practices were found.
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card className="result-card" elevation={0}>
            <CardContent>
              <Typography variant="h6" className="result-heading">
                Supporting Tasks
              </Typography>

              {results.supportingTasks?.length ? (
                <Stack spacing={1.5}>
                  {results.supportingTasks.map((item) => (
                    <Box key={item.taskID} className="result-item">
                      <Typography className="result-id">
                        {item.taskID}
                      </Typography>
                      <Typography>
                        {item.roleName} · {item.wpName}
                      </Typography>
                      {(item.currentState || item.finalState) && (
                        <Typography className="state-text">
                          {item.currentState || 'Current state unavailable'}
                          {' → '}
                          {item.finalState || 'Final state unavailable'}
                        </Typography>
                      )}
                    </Box>
                  ))}
                </Stack>
              ) : (
                <Typography className="empty-result">
                  No supporting Tasks were found for the selected role and
                  situation.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Stack>
      )}

      <Typography className="footer-note">
        Prototype retrieval layer · Spring Boot + Apache Jena Fuseki + ISO/IEC
        29110 ontology
      </Typography>
    </Box>
  );
}

export default App;
