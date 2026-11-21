import { test, expect } from 'vitest'
import vectors from '../../shared/light-test-vectors.json'
import { mix, dayGradient } from '../src/light'

test.each(vectors.mix.map(v => [v.name, v] as const))('mix %s', (_n, v) => {
  const r = mix(v.emotions)
  expect({ light: r.light, glow: r.glow, off: r.off }).toEqual({ light: v.expect.light, glow: v.expect.glow, off: v.expect.off })
  expect(r.strength).toBeCloseTo(v.expect.strength, 6)
})

test.each(vectors.dayGradient.map(v => [v.name, v] as const))('dayGradient %s', (_n, v) => {
  const r = dayGradient(v.entries)
  expect(r.map(s => ({ light: s.light.light, glow: s.light.glow, off: s.light.off }))).toEqual(v.expect.map(e => ({ light: e.light, glow: e.glow, off: e.off })))
  r.forEach((s, i) => { expect(s.position).toBeCloseTo(v.expect[i].position, 6); expect(s.light.strength).toBeCloseTo(v.expect[i].strength, 6) })
})
