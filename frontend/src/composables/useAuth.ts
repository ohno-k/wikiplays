import { ref, computed, readonly } from 'vue'

export interface UserInfo {
  id: number
  email: string
  displayName: string
  role: string
  plan: 'FREE' | 'PREMIUM'
  premiumActive: boolean
  xp: number
  level: number
  xpIntoLevel: number
  xpForNextLevel: number
}

interface AuthResponse {
  token: string
  user: UserInfo
}

const TOKEN_KEY = 'wikiplays_jwt'
const USER_KEY = 'wikiplays_user'

const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
const user = ref<UserInfo | null>(loadUser())

function loadUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function persist(t: string | null, u: UserInfo | null) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
  else localStorage.removeItem(TOKEN_KEY)
  if (u) localStorage.setItem(USER_KEY, JSON.stringify(u))
  else localStorage.removeItem(USER_KEY)
}

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const isPremium = computed(() => user.value?.premiumActive === true)

  async function register(email: string, password: string, displayName: string, legacyPlayerId?: string): Promise<UserInfo> {
    const res = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, displayName, legacyPlayerId }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: '登録失敗' }))
      throw new Error(err.message || '登録失敗')
    }
    const data: AuthResponse = await res.json()
    token.value = data.token
    user.value = data.user
    persist(data.token, data.user)
    return data.user
  }

  async function login(email: string, password: string): Promise<UserInfo> {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'ログイン失敗' }))
      throw new Error(err.message || 'ログイン失敗')
    }
    const data: AuthResponse = await res.json()
    token.value = data.token
    user.value = data.user
    persist(data.token, data.user)
    return data.user
  }

  function logout() {
    token.value = null
    user.value = null
    persist(null, null)
  }

  /** サーバーから最新のユーザー情報を取得 (サブスク状態の更新確認等)。 */
  async function refresh(): Promise<UserInfo | null> {
    if (!token.value) return null
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Authorization': `Bearer ${token.value}` },
      })
      if (!res.ok) {
        if (res.status === 401) logout()
        return null
      }
      const u: UserInfo = await res.json()
      user.value = u
      persist(token.value, u)
      return u
    } catch {
      return null
    }
  }

  /** 認証付き fetch ラッパー。 */
  async function authFetch(input: RequestInfo, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers)
    if (token.value) headers.set('Authorization', `Bearer ${token.value}`)
    return fetch(input, { ...init, headers })
  }

  return {
    token: readonly(token),
    user: readonly(user),
    isLoggedIn,
    isPremium,
    register,
    login,
    logout,
    refresh,
    authFetch,
  }
}
