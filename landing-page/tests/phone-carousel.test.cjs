const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  AUTO_ADVANCE_MS,
  createPhoneCarousel,
  createPhoneAutoplay,
} = require('../assets/phone-carousel.js');

function createClock() {
  let now = 0;
  let nextId = 1;
  const timers = new Map();
  return {
    now: function () { return now; },
    setIntervalFn: function (fn, ms) {
      const id = nextId;
      nextId += 1;
      timers.set(id, { fn, ms, next: now + ms });
      return id;
    },
    clearIntervalFn: function (id) { timers.delete(id); },
    advance: function (ms) {
      now += ms;
      for (const timer of [...timers.values()]) {
        while (timer.next <= now) {
          timer.fn();
          timer.next += timer.ms;
        }
      }
    },
  };
}

test('select wraps around the four app screens', () => {
  const seen = [];
  const carousel = createPhoneCarousel({ count: 4, onChange: (index) => seen.push(index) });
  assert.equal(carousel.select(5), 1);
  assert.equal(carousel.select(-1), 3);
  assert.deepEqual(seen, [1, 3]);
});

test('next walks Início, Grupos, Financeiro, Perfil and loops', () => {
  const carousel = createPhoneCarousel({ count: 4 });
  assert.equal(carousel.getIndex(), 0);
  assert.equal(carousel.next(), 1);
  assert.equal(carousel.next(), 2);
  assert.equal(carousel.next(), 3);
  assert.equal(carousel.next(), 0);
});

test('autoplay advances every 2.4 seconds while enabled', () => {
  const clock = createClock();
  const carousel = createPhoneCarousel({ count: 4 });
  const autoplay = createPhoneAutoplay({
    carousel,
    setIntervalFn: clock.setIntervalFn,
    clearIntervalFn: clock.clearIntervalFn,
  });
  autoplay.start();
  assert.equal(AUTO_ADVANCE_MS, 2400);
  assert.equal(autoplay.isRunning(), true);
  assert.equal(carousel.getIndex(), 0);
  clock.advance(2399);
  assert.equal(carousel.getIndex(), 0);
  clock.advance(1);
  assert.equal(carousel.getIndex(), 1);
  clock.advance(2400);
  assert.equal(carousel.getIndex(), 2);
  clock.advance(4800);
  assert.equal(carousel.getIndex(), 0);
});

test('autoplay skips ticks and can stop while disabled', () => {
  const clock = createClock();
  let enabled = true;
  const carousel = createPhoneCarousel({ count: 4 });
  const autoplay = createPhoneAutoplay({
    carousel,
    isEnabled: () => enabled,
    setIntervalFn: clock.setIntervalFn,
    clearIntervalFn: clock.clearIntervalFn,
  });
  autoplay.start();
  enabled = false;
  clock.advance(2400);
  assert.equal(carousel.getIndex(), 0);
  autoplay.stop();
  assert.equal(autoplay.isRunning(), false);
  enabled = true;
  clock.advance(2400);
  assert.equal(carousel.getIndex(), 0);
  autoplay.start();
  clock.advance(2400);
  assert.equal(carousel.getIndex(), 1);
});

test('restart waits a full interval after a manual select', () => {
  const clock = createClock();
  const carousel = createPhoneCarousel({ count: 4 });
  const autoplay = createPhoneAutoplay({
    carousel,
    setIntervalFn: clock.setIntervalFn,
    clearIntervalFn: clock.clearIntervalFn,
  });
  autoplay.start();
  clock.advance(2000);
  carousel.select(2);
  autoplay.restart();
  clock.advance(2399);
  assert.equal(carousel.getIndex(), 2);
  clock.advance(1);
  assert.equal(carousel.getIndex(), 3);
});
