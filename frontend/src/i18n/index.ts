import { createI18n } from 'vue-i18n'
import en from './locales/en.json'
import es from './locales/es.json'

const STORAGE_KEY = 'demo-locale'

export const SUPPORTED_LOCALES = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
]

function detectLocale(): string {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) return stored
  const browserLang = navigator.language.split('-')[0]
  return SUPPORTED_LOCALES.some((l) => l.code === browserLang) ? browserLang : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'en',
  messages: { en, es },
})

export function setLocale(locale: string) {
  i18n.global.locale.value = locale as 'en' | 'es'
  localStorage.setItem(STORAGE_KEY, locale)
}
