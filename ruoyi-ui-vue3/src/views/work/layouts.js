import { createSplitSlots, getSplitLayout } from './splitScreenLayouts.js'

function buildLayouts() {
  const modes = [1, 4, 6, 8, 9, 16, 17, 21, 23, 24]
  const layouts = {}
  modes.forEach((mode) => {
    layouts[mode] = createSplitSlots(mode, (index) => ({
      index: index + 1,
      type: '',
      data: null
    }))
  })
  return layouts
}

const layouts = buildLayouts()

export default layouts
export { getSplitLayout, createSplitSlots }
