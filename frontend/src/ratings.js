// Labels of the Rating individuals of the ontology. The rating itself always comes from the backend.
export const RATING_LABELS = {
  Not_achieved: 'Not achieved',
  Partially_achieved: 'Partially achieved',
  Largely_achieved: 'Largely achieved',
  Completely_achieved: 'Completely achieved',
  Not_applicable: 'Not applicable',
  Not_rated: 'No percentage',
};
export const RATING_ORDER = Object.keys(RATING_LABELS);
export const ratingLabel = (key) => RATING_LABELS[key] || key;
