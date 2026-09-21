import useDictStore from '@/store/modules/dict'
import { getDicts } from '@/api/system/dict/data'
import { translateDictValue } from '@/i18n/systemDicts'

function localizeDict(dictType, item) {
  if (item?.rawLabel !== undefined) return item
  const localized = {
    ...item,
    rawLabel: item.label,
    dictType
  }
  Object.defineProperty(localized, 'label', {
    enumerable: true,
    configurable: true,
    get() {
      return translateDictValue(dictType, localized.value, localized.rawLabel)
    }
  })
  return localized
}

/**
 * 获取字典数据
 */
export function useDict(...args) {
  const res = ref({});
  return (() => {
    args.forEach((dictType, index) => {
      res.value[dictType] = [];
      const dicts = useDictStore().getDict(dictType);
      if (dicts) {
        res.value[dictType] = dicts.map(item => localizeDict(dictType, item));
      } else {
        getDicts(dictType).then(resp => {
          res.value[dictType] = resp.data.map(p => localizeDict(dictType, { label: p.dictLabel, value: p.dictValue, elTagType: p.listClass, elTagClass: p.cssClass }))
          useDictStore().setDict(dictType, res.value[dictType]);
        })
      }
    })
    return toRefs(res.value);
  })()
}
