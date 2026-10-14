'use strict';
/** Tests against Ultra-Sentinel's actual pinned v1 wire validator. This is
 * interoperability evidence only, NEVER independent merge authorization. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { validateWire } = require('../.external/ultra-sentinel/packages/contracts/validate-wire.cjs');

const advisory = Object.freeze({
  schemaVersion: 1,
  targetRepo: 'cardenaspiero255-lang/JARVIS',
  headSha: 'a'.repeat(40),
  verdict: 'ADVISORY',
  evidenceState: 'UNVERIFIED',
  findingCount: 2
});

test('JARVIS advisory matches published Sentinel v1 contract', () => {
  assert.equal(validateWire('review-result', advisory).ok, true);
});

test('wire rejects forged authority and schema drift', () => {
  assert.equal(validateWire('review-result', { ...advisory, authorizedToMerge: true }).ok, false);
  assert.equal(validateWire('review-result', { ...advisory, schemaVersion: 2 }).ok, false);
  assert.equal(validateWire('review-result', {
    ...advisory, verdict: 'CLEAR', evidenceState: 'UNVERIFIED'
  }).ok, false);
});
