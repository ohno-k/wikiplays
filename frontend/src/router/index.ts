import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

// ホーム以外は遅延読み込み (html2canvas などを初期バンドルから外す)
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/daily', name: 'daily', component: () => import('../views/DailyView.vue') },
    { path: '/community', name: 'community', component: () => import('../views/CommunityGenresView.vue') },
    { path: '/stats', name: 'stats', component: () => import('../views/StatsView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('../views/LoginView.vue'), props: { initialMode: 'register' } },
    { path: '/account', name: 'account', component: () => import('../views/AccountView.vue') },
    { path: '/leaderboard', name: 'leaderboard', component: () => import('../views/LeaderboardView.vue') },
    { path: '/verify-email', name: 'verify-email', component: () => import('../views/VerifyEmailView.vue') },
    { path: '/forgot-password', name: 'forgot-password', component: () => import('../views/ForgotPasswordView.vue') },
    { path: '/reset-password', name: 'reset-password', component: () => import('../views/ResetPasswordView.vue') },
    { path: '/oauth-callback', name: 'oauth-callback', component: () => import('../views/OAuthCallbackView.vue') },
    { path: '/daily-archive', name: 'daily-archive', component: () => import('../views/DailyArchiveView.vue') },
    { path: '/rules', name: 'rules', component: () => import('../views/RulesView.vue') },
    { path: '/about', name: 'about', component: () => import('../views/AboutView.vue') },
    { path: '/friends', name: 'friends', component: () => import('../views/FriendsView.vue') },
    { path: '/legal', name: 'legal', component: () => import('../views/LegalView.vue') },
    { path: '/mode/a', name: 'mode-a', component: () => import('../views/modes/ModeA.vue') },
    { path: '/mode/b', name: 'mode-b', component: () => import('../views/modes/ModeB.vue') },
    { path: '/mode/c', name: 'mode-c', component: () => import('../views/modes/ModeC.vue') },
    { path: '/mode/d', name: 'mode-d', component: () => import('../views/modes/ModeD.vue') },
    { path: '/mode/e', name: 'mode-e', component: () => import('../views/modes/ModeE.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
