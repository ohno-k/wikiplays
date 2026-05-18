import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import DailyView from '../views/DailyView.vue'
import CommunityGenresView from '../views/CommunityGenresView.vue'
import StatsView from '../views/StatsView.vue'
import LoginView from '../views/LoginView.vue'
import AccountView from '../views/AccountView.vue'
import LeaderboardView from '../views/LeaderboardView.vue'
import VerifyEmailView from '../views/VerifyEmailView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import ResetPasswordView from '../views/ResetPasswordView.vue'
import OAuthCallbackView from '../views/OAuthCallbackView.vue'
import DailyArchiveView from '../views/DailyArchiveView.vue'
import RulesView from '../views/RulesView.vue'
import FriendsView from '../views/FriendsView.vue'
import ModeA from '../views/modes/ModeA.vue'
import ModeB from '../views/modes/ModeB.vue'
import ModeC from '../views/modes/ModeC.vue'
import ModeD from '../views/modes/ModeD.vue'
import ModeE from '../views/modes/ModeE.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/daily', name: 'daily', component: DailyView },
    { path: '/community', name: 'community', component: CommunityGenresView },
    { path: '/stats', name: 'stats', component: StatsView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/account', name: 'account', component: AccountView },
    { path: '/leaderboard', name: 'leaderboard', component: LeaderboardView },
    { path: '/verify-email', name: 'verify-email', component: VerifyEmailView },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView },
    { path: '/oauth-callback', name: 'oauth-callback', component: OAuthCallbackView },
    { path: '/daily-archive', name: 'daily-archive', component: DailyArchiveView },
    { path: '/rules', name: 'rules', component: RulesView },
    { path: '/friends', name: 'friends', component: FriendsView },
    { path: '/mode/a', name: 'mode-a', component: ModeA },
    { path: '/mode/b', name: 'mode-b', component: ModeB },
    { path: '/mode/c', name: 'mode-c', component: ModeC },
    { path: '/mode/d', name: 'mode-d', component: ModeD },
    { path: '/mode/e', name: 'mode-e', component: ModeE },
  ],
})

export default router
