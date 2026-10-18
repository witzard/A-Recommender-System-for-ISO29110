import { useEffect, useState } from 'react';
import { competencyGroups, competencyQuestions } from './data/competencyQuestions';
import axios from 'axios';
import './App.css';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const API_URL = `${API_BASE_URL}/recommend`;
const OPTIONS_URL = `${API_BASE_URL}/options`;

function sanitizeUserFacingText(value, fallback = 'The request could not be completed. Please check the backend logs.') {
  if (typeof value !== 'string') return fallback;
  // Never render Thai-language text returned by older backend builds or ontology annotations.
  if (/[\\u0E00-\\u0E7F]/.test(value)) return fallback;
  return value;
}

function getEnglishError(error, context) {
  const status = error?.response?.status;
  if (!error?.response) {
    return `Cannot connect to the backend while ${context}. Check that the Spring Boot service is running at ${API_BASE_URL}.`;
  }
  if (status === 404) {
    return 'The requested API endpoint was not found (HTTP 404). Restart the backend and verify that the latest API routes are deployed.';
  }
  if (status >= 500) {
    return 'The backend could not complete the request. Check the Spring Boot logs and confirm that Fuseki is running and the dataset endpoint is configured correctly.';
  }
  if (status === 400) {
    return 'The request was rejected as invalid. Check the selected values and the backend validation details.';
  }
  return `The request failed with HTTP ${status || 'an unknown status'} while ${context}.`;
}

const initialForm = {
  process: 'PM',
  role: 'Project manager',
  topic: 'Project Plan',
  situation: 'NOT_APPROVED',
};

function findDefault(options, preferredValue) {
  return (
    options.find(
      (item) =>
        item.value?.toLowerCase() === preferredValue?.toLowerCase()
    ) ||
    options[0] ||
    null
  );
}

function App() {
  const [form, setForm] = useState(initialForm);
  const [options, setOptions] = useState({
    processes: [],
    roles: [],
    workProducts: [],
    situations: [],
  });
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [results, setResults] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedQuestion, setSelectedQuestion] = useState(null);
  const [questionAnswer, setQuestionAnswer] = useState(null);
  const [questionLoading, setQuestionLoading] = useState(false);
  const [questionError, setQuestionError] = useState('');

  useEffect(() => {
    const loadOptions = async () => {
      try {
        const response = await axios.get(OPTIONS_URL);
        const loadedOptions = response.data;

        setOptions(loadedOptions);

        const defaultProcess = findDefault(
          loadedOptions.processes,
          initialForm.process
        );
        const defaultRole = findDefault(
          loadedOptions.roles,
          initialForm.role
        );
        const defaultTopic = findDefault(
          loadedOptions.workProducts,
          initialForm.topic
        );
        const defaultSituation = findDefault(
          loadedOptions.situations,
          initialForm.situation
        );

        setForm({
          process: defaultProcess?.value || '',
          role: defaultRole?.value || '',
          topic: defaultTopic?.value || '',
          situation: defaultSituation?.value || '',
        });
      } catch (err) {
        setError(getEnglishError(err, 'loading recommendation options'));
      } finally {
        setOptionsLoading(false);
      }
    };

    loadOptions();
  }, []);

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
      setError(getEnglishError(err, 'retrieving recommendations'));
    } finally {
      setLoading(false);
    }
  };

  const handleQuestionClick = async (question) => {
    setSelectedQuestion(question);
    setQuestionAnswer(null);
    setQuestionError('');
    setQuestionLoading(true);

    try {
      const response = await axios.get(`${API_BASE_URL}/questions/${question.id}/answer`);
      setQuestionAnswer(response.data);
    } catch (err) {
      setQuestionError(getEnglishError(err, 'searching the ontology'));
    } finally {
      setQuestionLoading(false);
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
            Select structured project information retrieved from the ontology.
          </Typography>

          <Divider sx={{ my: 3 }} />

          {optionsLoading ? (
            <Box className="options-loading">
              <CircularProgress size={28} />
              <Typography>Loading options from the ontology...</Typography>
            </Box>
          ) : (
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
                  {options.processes.map((item) => (
                    <MenuItem key={item.value} value={item.value}>
                      {item.label}
                    </MenuItem>
                  ))}
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
                  {options.roles.map((item) => (
                    <MenuItem key={item.value} value={item.value}>
                      {item.label}
                    </MenuItem>
                  ))}
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
                  {options.workProducts.map((item) => (
                    <MenuItem key={item.value} value={item.value}>
                      {item.value}
                    </MenuItem>
                  ))}
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
                  {options.situations.map((item) => (
                    <MenuItem key={item.value} value={item.value}>
                      {item.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>
          )}

          <Button
            className="recommend-button"
            variant="contained"
            size="large"
            onClick={handleRecommend}
            disabled={loading || optionsLoading || !form.process || !form.role || !form.topic || !form.situation}
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

      <Card className="input-card coverage-card" elevation={0}>
        <CardContent>
          <Typography variant="h5" className="section-title">
            Competency Question Groups
          </Typography>
          <Typography className="section-description">
            Select any question to search the connected ISO/IEC 29110 ontology for supporting tasks and Base Practices.
          </Typography>
          <Divider sx={{ my: 2.5 }} />
          <Stack spacing={1.25}>
            {competencyGroups.map((group) => {
              const questions = competencyQuestions.filter(
                (question) => group.questionIds.includes(question.id)
              );
              return (
                <Accordion key={group.id} disableGutters elevation={0} className="group-accordion">
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Box className="group-summary">
                      <Box>
                        <Typography className="group-title">{group.name}</Typography>
                        <Typography className="group-description">{group.description}</Typography>
                      </Box>
                      <Chip label={`${questions.length} questions`} size="small" />
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Stack spacing={1}>
                      {questions.map((question) => (
                        <Button
                          key={question.id}
                          className="question-list-item question-button"
                          onClick={() => handleQuestionClick(question)}
                          aria-label={`Open answer for ${question.qid}`}
                        >
                          <Typography className="result-id">{question.qid}</Typography>
                          <Box className="question-button-copy">
                            <Typography>{question.question}</Typography>
                            <Typography className="question-hint">Click to view ontology evidence →</Typography>
                          </Box>
                        </Button>
                      ))}
                    </Stack>
                  </AccordionDetails>
                </Accordion>
              );
            })}
          </Stack>
        </CardContent>
      </Card>

      <Dialog
        open={Boolean(selectedQuestion)}
        onClose={() => setSelectedQuestion(null)}
        fullWidth
        maxWidth="md"
        aria-labelledby="question-answer-title"
      >
        <DialogTitle id="question-answer-title">
          {selectedQuestion ? `${selectedQuestion.qid} · Answer and Ontology Evidence` : 'Question answer'}
        </DialogTitle>
        <DialogContent dividers>
          {selectedQuestion && (
            <Typography className="answer-question">{selectedQuestion.question}</Typography>
          )}
          {questionLoading && (
            <Box className="options-loading">
              <CircularProgress size={24} />
              <Typography>Searching the ISO/IEC 29110 ontology...</Typography>
            </Box>
          )}
          {questionError && <Alert severity="error">{questionError}</Alert>}
          {questionAnswer && (
            <Stack spacing={2}>
              <Box>
                <Typography variant="h6" className="answer-section-title">Answer</Typography>
                <Typography className="answer-summary">
                  {sanitizeUserFacingText(
                    questionAnswer.answer || questionAnswer.message,
                    'Ontology evidence was retrieved, but its summary contains unsupported language. Please rebuild and restart the latest backend.'
                  )}
                </Typography>
                <Typography className="answer-meta">
                  This answer summarizes the ontology guidance. It does not verify whether a specific company follows the process.
                </Typography>
              </Box>
              {questionAnswer.basePractices?.length > 0 && (
                <Box>
                  <Typography variant="h6" className="answer-section-title">Key Base Practices</Typography>
                  <Stack spacing={1}>
                    {questionAnswer.basePractices.slice(0, 5).map((item, index) => (
                      <Box className="answer-evidence-item" key={`${item.bpID}-${index}`}>
                        <Typography className="result-id">{item.bpID}</Typography>
                        <Typography>{sanitizeUserFacingText(item.bpDescription, 'This evidence description contains unsupported language and has been hidden.')}</Typography>
                      </Box>
                    ))}
                  </Stack>
                </Box>
              )}
              {questionAnswer.tasks?.length > 0 && (
                <Box>
                  <Typography variant="h6" className="answer-section-title">Related Tasks</Typography>
                  <Stack spacing={1}>
                    {questionAnswer.tasks.slice(0, 6).map((item, index) => (
                      <Box className="answer-evidence-item" key={`${item.taskID}-${index}`}>
                        <Typography className="result-id">{item.taskID}</Typography>
                        <Typography>{sanitizeUserFacingText(item.taskDescription, 'This task description contains unsupported language and has been hidden.')}</Typography>
                        {item.roleName && <Typography className="answer-meta">Role: {item.roleName}</Typography>}
                        {item.inputName && <Typography className="answer-meta">Input: {item.inputName}</Typography>}
                        {item.outputName && <Typography className="answer-meta">Output: {item.outputName}</Typography>}
                      </Box>
                    ))}
                  </Stack>
                </Box>
              )}
              {(questionAnswer.basePractices?.length > 5 || questionAnswer.tasks?.length > 6) && (
                <Typography className="answer-meta">
                  Showing the most relevant evidence only to keep the answer focused.
                </Typography>
              )}
              {questionAnswer.searchTerms?.length > 0 && (
                <Typography className="answer-meta">
                  Ontology search terms: {questionAnswer.searchTerms.join(', ')}
                </Typography>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedQuestion(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Typography className="footer-note">
        Spring Boot + Apache Jena Fuseki + ISO/IEC 29110 ontology
      </Typography>
    </Box>
  );
}

export default App;
