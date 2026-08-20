import { defineStore } from 'pinia'
import { commonApi } from '../api/common'
import type { Country } from '../models'

export const useCommonStore = defineStore('common', {
  state: () => ({
    countries: [] as Country[],
  }),
  actions: {
    async loadCountries() {
      this.countries = await commonApi.countries()
    },
  },
})
