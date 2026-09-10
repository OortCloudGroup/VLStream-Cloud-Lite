function cells(count) {
  return Array.from({ length: count }, (_, i) => ({
    index: i + 1,
    type: '',
    data: null
  }))
}

const layouts = {
  1: cells(1),
  4: cells(4),
  6: cells(6),
  8: cells(8),
  9: cells(9),
  16: cells(16),
  17: cells(17),
  21: cells(21),
  23: cells(23),
  24: cells(24)
}

export default layouts
