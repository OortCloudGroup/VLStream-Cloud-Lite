export default [
  {
    layout: 'colFormItem',
    tagIcon: 'input',
    get label() { return translatePhrase("手机号") },
    vModel: 'mobile',
    formId: 6,
    tag: 'el-input',
    get placeholder() { return translatePhrase("请输入手机号") },
    defaultValue: '',
    span: 24,
    style: { width: '100%' },
    clearable: true,
    prepend: '',
    append: '',
    'prefix-icon': 'Cellphone',
    'suffix-icon': '',
    maxlength: 11,
    'show-word-limit': true,
    readonly: false,
    disabled: false,
    required: true,
    changeTag: true,
    regList: [{
      pattern: '/^1(3|4|5|7|8|9)\\d{9}$/',
      get message() { return translatePhrase("手机号格式错误") }
    }]
  }
]
