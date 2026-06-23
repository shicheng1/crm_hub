import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'orders', name: 'OrderList', component: () => import('../views/OrderList.vue'), meta: { title: '工单列表' } },
      { path: 'orders/create', name: 'OrderCreate', component: () => import('../views/OrderCreate.vue'), meta: { title: '创建工单' } },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('../views/OrderDetail.vue'), meta: { title: '工单详情' } },
      { path: 'todo', name: 'TodoList', component: () => import('../views/TodoList.vue'), meta: { title: '待我审批' } },
      { path: 'flows', name: 'FlowManage', component: () => import('../views/FlowManage.vue'), meta: { title: '审批流管理' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path !== '/login' && !getToken()) {
    next('/login')
  } else {
    next()
  }
})

export default router
