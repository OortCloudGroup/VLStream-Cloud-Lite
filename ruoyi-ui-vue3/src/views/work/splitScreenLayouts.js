/**
 * 分屏布局：与 screen*.svg 图标结构一致（CSS Grid）
 */

export function equalGrid(cols, rows) {
  const cells = []
  for (let r = 1; r <= rows; r++) {
    for (let c = 1; c <= cols; c++) {
      cells.push({ column: String(c), row: String(r) })
    }
  }
  return {
    columns: `repeat(${cols}, 1fr)`,
    rows: `repeat(${rows}, 1fr)`,
    cells
  }
}

function cellsAround(cols, rows, spanCol, spanRow) {
  const cells = [{ column: `1 / ${spanCol + 1}`, row: `1 / ${spanRow + 1}` }]
  for (let r = 1; r <= rows; r++) {
    for (let c = 1; c <= cols; c++) {
      if (c <= spanCol && r <= spanRow) continue
      cells.push({ column: String(c), row: String(r) })
    }
  }
  return {
    columns: `repeat(${cols}, 1fr)`,
    rows: `repeat(${rows}, 1fr)`,
    cells
  }
}

function layout23() {
  const cells = [
    { column: '1 / 4', row: '1 / 4' },
    { column: '1 / 4', row: '4 / 6' },
    { column: '1', row: '6' },
    { column: '2', row: '6' },
    { column: '3', row: '6' }
  ]
  for (let r = 1; r <= 6; r++) {
    for (let c = 4; c <= 6; c++) {
      cells.push({ column: String(c), row: String(r) })
    }
  }
  return {
    columns: 'repeat(6, 1fr)',
    rows: 'repeat(6, 1fr)',
    cells
  }
}

const LAYOUTS = {
  1: equalGrid(1, 1),
  4: equalGrid(2, 2),
  6: cellsAround(3, 3, 2, 2),
  8: cellsAround(4, 4, 3, 3),
  9: equalGrid(3, 3),
  16: equalGrid(4, 4),
  17: cellsAround(5, 4, 2, 2),
  21: cellsAround(6, 6, 4, 4),
  23: layout23(),
  24: equalGrid(4, 6)
}

export function getSplitLayout(mode) {
  const key = Number(mode)
  return LAYOUTS[key] || equalGrid(2, 2)
}

/** 自定义行列（1-9）等分宫格 */
export function getCustomEqualLayout(rows, cols) {
  const r = Math.min(9, Math.max(1, Number(rows) || 1))
  const c = Math.min(9, Math.max(1, Number(cols) || 1))
  return equalGrid(c, r)
}

export function getSplitCellCount(mode) {
  return getSplitLayout(mode).cells.length
}

export function createSplitSlots(mode, factory) {
  const layout = typeof mode === 'object' && mode?.cells
    ? mode
    : getSplitLayout(mode)
  const make = typeof factory === 'function' ? factory : () => ({ type: '', data: null })
  return layout.cells.map((_, index) => {
    const item = make(index)
    return typeof item === 'object' && item !== null
      ? { index: index + 1, ...item }
      : item
  })
}

export default LAYOUTS
