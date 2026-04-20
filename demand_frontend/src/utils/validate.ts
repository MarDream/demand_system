import type { FormItemRule } from 'element-plus'

export function required(msg = '此项为必填项'): FormItemRule {
  return { required: true, message: msg, trigger: 'blur' }
}

export function email(): FormItemRule {
  return { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
}

export function phone(): FormItemRule {
  return { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码', trigger: 'blur' }
}

export function length(min: number, max: number): FormItemRule {
  return { min, max, message: `长度应在 ${min} 到 ${max} 之间`, trigger: 'blur' }
}
