(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PhoneCarousel = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const AUTO_ADVANCE_MS = 2400;

  function createPhoneCarousel({ count, onChange = function () {} }) {
    let index = 0;

    function select(nextIndex) {
      index = ((nextIndex % count) + count) % count;
      onChange(index);
      return index;
    }

    return {
      getIndex: function () { return index; },
      next: function () { return select(index + 1); },
      select,
    };
  }

  function createPhoneAutoplay({
    carousel,
    intervalMs = AUTO_ADVANCE_MS,
    isEnabled = function () { return true; },
    setIntervalFn = setInterval,
    clearIntervalFn = clearInterval,
  }) {
    let timerId = null;

    function stop() {
      if (timerId === null) return;
      clearIntervalFn(timerId);
      timerId = null;
    }

    function start() {
      stop();
      if (!isEnabled()) return;
      timerId = setIntervalFn(function () {
        if (!isEnabled()) return;
        carousel.next();
      }, intervalMs);
    }

    return {
      start,
      stop,
      restart: start,
      isRunning: function () { return timerId !== null; },
    };
  }

  return { AUTO_ADVANCE_MS, createPhoneCarousel, createPhoneAutoplay };
});
