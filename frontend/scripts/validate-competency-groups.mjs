import { competencyGroups, competencyQuestions } from '../src/data/competencyQuestions.js';

const errors = [];
const questionIds = competencyQuestions.map((question) => question.id);
const duplicates = questionIds.filter((id, index) => questionIds.indexOf(id) !== index);
if (duplicates.length) {
  errors.push(`Duplicate question IDs: ${[...new Set(duplicates)].join(', ')}`);
}

const groupedIds = competencyGroups.flatMap((group) => group.questionIds);
const groupedDuplicates = groupedIds.filter((id, index) => groupedIds.indexOf(id) !== index);
if (groupedDuplicates.length) {
  errors.push(`Questions assigned to multiple groups: ${[...new Set(groupedDuplicates)].join(', ')}`);
}

const missingFromGroups = questionIds.filter((id) => !groupedIds.includes(id));
const unknownInGroups = groupedIds.filter((id) => !questionIds.includes(id));
if (missingFromGroups.length) errors.push(`Questions missing from groups: ${missingFromGroups.join(', ')}`);
if (unknownInGroups.length) errors.push(`Groups refer to unknown question IDs: ${unknownInGroups.join(', ')}`);

for (const group of competencyGroups) {
  for (const id of group.questionIds) {
    const question = competencyQuestions.find((item) => item.id === id);
    if (question && question.groupId !== group.id) {
      errors.push(`Q${id} groupId mismatch: data says ${question.groupId}, group list says ${group.id}`);
    }
  }
}

if (competencyGroups.length !== 8) errors.push(`Expected 8 groups, got ${competencyGroups.length}`);
if (competencyQuestions.length !== 34) errors.push(`Expected 34 questions, got ${competencyQuestions.length}`);

if (errors.length) {
  console.error('Competency grouping validation failed:');
  errors.forEach((error) => console.error(`- ${error}`));
  process.exit(1);
}

console.log('Competency grouping validation passed: 34 questions assigned exactly once across 8 groups.');
