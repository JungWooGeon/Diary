import { test, expect } from 'vitest'
import crisis from '../../shared/ai/crisis.json'
import { detectCrisis, fillSlot } from '../src/ai'

test.each(crisis.testCases.positive)('crisis: %s', t => expect(detectCrisis(t).crisis).toBe(true))
test.each(crisis.testCases.negative)('not crisis: %s', t => expect(detectCrisis(t).crisis).toBe(false))
test('NFD input normalizes like NFC', () => expect(detectCrisis('죽고 싶다'.normalize('NFD')).crisis).toBe(true))
test('slot josa', () => {
  expect(fillSlot('{person}(이)랑 놀았네', 'person', '민준')).toBe('민준이랑 놀았네')
  expect(fillSlot('{person}(이)랑 놀았네', 'person', '엄마')).toBe('엄마랑 놀았네')
  expect(fillSlot('{place}(으)로 갔구나', 'place', '서울')).toBe('서울로 갔구나')
})
