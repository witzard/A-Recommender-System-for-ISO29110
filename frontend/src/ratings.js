// Rating bands are those of the ontology's Rating individuals (hasMinPercentage / hasMaxPercentage)
// and of the four SWRL rating rules. Used ONLY to draw the scale; the rating shown always comes from the backend.
export const RATINGS = {
  Not_achieved: { label: 'Not achieved', short: 'N', min: 0, max: 15, color: '#B42318', tint: '#FDECEA' },
  Partially_achieved: { label: 'Partially achieved', short: 'P', min: 16, max: 50, color: '#B54708', tint: '#FEF1E6' },
  Largely_achieved: { label: 'Largely achieved', short: 'L', min: 51, max: 90, color: '#3F7D5C', tint: '#EAF3EE' },
  Completely_achieved: { label: 'Completely achieved', short: 'F', min: 91, max: 100, color: '#1E5A3C', tint: '#E3EFE8' },
  Not_rated: { label: 'Not rated', short: '–', color: '#5B6475', tint: '#EEF0F4' },
};
export const ratingInfo = (key) => RATINGS[key] || RATINGS.Not_rated;
