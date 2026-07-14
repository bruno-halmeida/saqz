(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PhoneCarousel = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
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

  return { createPhoneCarousel };
});
