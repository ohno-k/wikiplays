import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

// 本番 (Static Site) では VITE_API_BASE_URL を設定し、/api/* を Backend に振る。
// 未設定なら dev proxy / 同一オリジン想定で素通り。
const apiBase = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
if (apiBase) {
  const originalFetch = window.fetch.bind(window)
  window.fetch = (input, init) => {
    if (typeof input === 'string' && input.startsWith('/api/')) {
      return originalFetch(apiBase + input, init)
    }
    if (input instanceof URL && input.pathname.startsWith('/api/')) {
      return originalFetch(apiBase + input.pathname + input.search, init)
    }
    if (input instanceof Request && input.url.startsWith(window.location.origin + '/api/')) {
      const newUrl = apiBase + input.url.slice(window.location.origin.length)
      return originalFetch(new Request(newUrl, input), init)
    }
    return originalFetch(input, init)
  }
}

createApp(App).use(router).mount('#app')
