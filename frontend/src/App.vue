<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const navigationItems = [
  { name: '指挥中心', description: '实时掌握正在执行的巡检任务。' },
  { name: '任务管理', description: '安排、查看和跟进每一项巡检任务。' },
  { name: '航线规划', description: '为无人机设计安全、高效的飞行路线。' },
  { name: '设备管理', description: '管理无人机、机场和相关设备。' },
  { name: '事件管理', description: '查看和处理巡检中发现的问题。' },
]

const activeNavigation = ref('指挥中心')
const taskFilter = ref('全部')
const selectedTaskId = ref(1)
const acknowledgementResult = ref('')
const acknowledgementMessage = ref('')
const acknowledgementSubmitting = ref(false)
const currentModule = computed(() => navigationItems.find((item) => item.name === activeNavigation.value))

const tasks = [
  { id: 1, name: '园区东侧例行巡检', route: '东侧围栏巡检路线', device: '巡检无人机 02', status: '执行中', progress: 68, time: '今天 09:30', operator: '张晨' },
  { id: 2, name: '屋顶光伏设备检查', route: '屋顶光伏巡检路线', device: '巡检无人机 01', status: '待执行', progress: 0, time: '今天 14:00', operator: '李然' },
  { id: 3, name: '北门周界安全巡检', route: '北门周界巡检路线', device: '巡检无人机 01', status: '已完成', progress: 100, time: '今天 08:10', operator: '王敏' },
]

const demoDevices = [
  { name: '巡检无人机 01', status: '在线待命', battery: 86 },
  { name: '巡检无人机 02', status: '正在飞行', battery: 72 },
  { name: '巡检无人机 03', status: '离线', battery: 0 },
]

// 页面刚打开时先显示这份演示数据；连接到后端后，会自动被实时数据替换。
const liveStatus = ref({
  progress: 68,
  altitude: 80,
  battery: 72,
  estimatedCompletion: '11:05',
  onlineDeviceCount: 2,
  todayDistance: 18.6,
  inspectedPoints: 34,
  totalPoints: 50,
  devices: demoDevices,
  alert: {
    active: false,
    level: '无',
    title: '当前无告警',
    detail: '设备和气象状态正常',
    occurredAt: '',
    device: '园区东侧气象站',
    acknowledged: false,
    handlingStatus: '无需处理',
    handledBy: '',
    handledAt: '',
    handlingResult: '',
  },
})
const connectionState = ref('连接实时服务中')
const devices = computed(() => liveStatus.value.devices)
const alert = computed(() => liveStatus.value.alert)
const alertPending = computed(() => alert.value.active && !alert.value.acknowledged)
const missionInProgress = computed(() => liveStatus.value.progress < 100)
const synchronizedTasks = computed(() => tasks.map((task) => task.id === 1
  ? {
      ...task,
      status: missionInProgress.value ? '执行中' : '已完成',
      progress: liveStatus.value.progress,
    }
  : task))
const filteredTasks = computed(() => taskFilter.value === '全部'
  ? synchronizedTasks.value
  : synchronizedTasks.value.filter((task) => task.status === taskFilter.value))
const selectedTask = computed(() => synchronizedTasks.value.find((task) => task.id === selectedTaskId.value) || synchronizedTasks.value[0])
const now = ref(new Date())
const todayLabel = computed(() => {
  const parts = new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).formatToParts(now.value)
  const month = parts.find((part) => part.type === 'month')?.value
  const day = parts.find((part) => part.type === 'day')?.value
  return `${month} / ${day}`
})
const liveTime = computed(() => new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(now.value))

let socket
let reconnectTimer
let clockTimer

function connectRealtimeService() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${protocol}://localhost:8080/ws/drone-status`)

  socket.onopen = () => { connectionState.value = '实时数据已连接' }
  socket.onmessage = (event) => {
    liveStatus.value = JSON.parse(event.data)
    connectionState.value = '实时数据已连接'
  }
  socket.onclose = () => {
    connectionState.value = '演示数据（后端未连接）'
    reconnectTimer = window.setTimeout(connectRealtimeService, 5000)
  }
}

async function acknowledgeAlert() {
  if (!acknowledgementResult.value.trim()) {
    acknowledgementMessage.value = '请先填写处理结果。'
    return
  }

  acknowledgementSubmitting.value = true
  acknowledgementMessage.value = ''
  try {
    const protocol = window.location.protocol === 'https:' ? 'https' : 'http'
    const response = await fetch(`${protocol}://localhost:8080/api/v1/dashboard/alerts/current/acknowledge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ handler: '张晨', result: acknowledgementResult.value.trim() }),
    })
    if (!response.ok) throw new Error('告警确认失败')
    liveStatus.value = await response.json()
    acknowledgementMessage.value = '告警已确认，处理记录已由后端保存。'
  } catch (error) {
    acknowledgementMessage.value = '暂时无法确认告警，请检查后端连接后重试。'
  } finally {
    acknowledgementSubmitting.value = false
  }
}

onMounted(() => {
  connectRealtimeService()
  clockTimer = window.setInterval(() => { now.value = new Date() }, 30000)
})
onBeforeUnmount(() => {
  window.clearTimeout(reconnectTimer)
  window.clearInterval(clockTimer)
  socket?.close()
})
</script>

<template>
  <main class="app-shell">
    <header class="site-header">
      <button class="brand" type="button" @click="activeNavigation = '指挥中心'">
        <span class="brand-mark">⌁</span><span>UAV <b>COMMAND</b></span>
      </button>
      <nav class="main-nav" aria-label="主导航">
        <button v-for="item in navigationItems" :key="item.name" type="button" :class="{ active: item.name === activeNavigation }" @click="activeNavigation = item.name">{{ item.name }}</button>
      </nav>
      <div class="header-status"><span></span><small>{{ connectionState }}</small><button type="button">张晨</button></div>
    </header>

    <section v-if="activeNavigation === '指挥中心'" class="dashboard">
      <header class="dashboard-intro">
        <div><p>DRONE INSPECTION · {{ todayLabel }}</p><h1>{{ missionInProgress ? '飞行正在进行' : '巡检任务已完成' }}<span>.</span></h1><p>{{ missionInProgress ? `园区东侧例行巡检已完成 ${liveStatus.progress}%，无人机 02 正在执行预设航线。` : '园区东侧例行巡检已完成，等待下一项任务安排。' }}</p></div>
        <button type="button" class="quiet-button" @click="activeNavigation = '任务管理'">查看全部任务 <span>↗</span></button>
      </header>

      <section class="flight-stage">
        <div class="stage-map" aria-label="园区东侧实时航线示意图">
          <div class="map-grid"></div><span class="place place-a">北门</span><span class="place place-b">东侧围栏</span><span class="place place-c">仓库区</span>
          <div class="flight-route"><i></i><i></i><i></i><i></i></div><div class="drone-pin">✦</div>
          <div class="map-caption"><span><i></i>实时航线</span><b>LIVE · {{ liveTime }}</b></div>
        </div>
        <aside class="flight-brief">
          <p class="section-label">当前任务</p><h2>园区东侧<br>例行巡检</h2><span class="live-label">● {{ missionInProgress ? '飞行中' : '已完成' }}</span>
          <div class="mission-progress"><div><span>执行进度</span><strong>{{ liveStatus.progress }}%</strong></div><i><b :style="{ width: `${liveStatus.progress}%` }"></b></i></div>
          <dl><div><dt>飞行高度</dt><dd>{{ liveStatus.altitude }} m</dd></div><div><dt>剩余电量</dt><dd>{{ liveStatus.battery }}%</dd></div><div><dt>预计完成</dt><dd>{{ liveStatus.estimatedCompletion }}</dd></div></dl>
          <button type="button" @click="activeNavigation = '任务管理'">任务详情 <span>→</span></button>
        </aside>
      </section>

      <section class="signal-strip" aria-label="实时数据">
        <article><span>在线设备</span><strong>{{ String(liveStatus.onlineDeviceCount).padStart(2, '0') }}</strong><small>全部连接正常</small></article>
        <article><span>今日巡检</span><strong>{{ liveStatus.todayDistance }} <em>km</em></strong><small>较昨日 +12%</small></article>
        <article><span>已巡检点位</span><strong>{{ liveStatus.inspectedPoints }} <em>/ {{ liveStatus.totalPoints }}</em></strong><small>当前任务数据</small></article>
        <article class="attention"><span>待确认告警</span><strong>{{ alertPending ? '01' : '00' }}</strong><small>{{ alertPending ? alert.title : '当前没有待确认告警' }}</small></article>
      </section>

      <section class="dashboard-bottom">
        <article class="fleet-section"><div class="section-heading"><div><p class="section-label">设备机队</p><h2>无人机状态</h2></div><button type="button" @click="activeNavigation = '设备管理'">所有设备 →</button></div><div class="fleet-list"><article v-for="device in devices" :key="device.name"><span :class="['status-dot', { offline: device.status === '离线' }]"></span><strong>{{ device.name }}</strong><small>{{ device.status }}</small><b>{{ device.battery }}%</b></article></div></article>
        <article :class="['event-section', { inactive: !alertPending }]"><p class="section-label">{{ alertPending ? '需要关注' : '当前状态' }}</p><h2>{{ alertPending ? alert.title : '没有待确认告警' }}</h2><p>{{ alertPending ? `${alert.detail}${alert.occurredAt ? ` · ${alert.occurredAt}` : ''}` : '当前告警均已完成确认。' }}</p><button type="button" @click="activeNavigation = '事件管理'">查看告警 →</button></article>
      </section>
    </section>

    <section v-else-if="activeNavigation === '任务管理'" class="module-page">
      <header class="module-intro"><p>MISSION CONTROL</p><h1>任务，不遗漏每一步。</h1><span>{{ currentModule.description }}</span></header>
      <div class="task-tools"><div><button v-for="status in ['全部', '执行中', '待执行', '已完成']" :key="status" type="button" :class="{ selected: taskFilter === status }" @click="taskFilter = status">{{ status }}</button></div><button class="create-task" type="button">新建任务 +</button></div>
      <div class="task-layout"><section class="task-list"><button v-for="task in filteredTasks" :key="task.id" type="button" :class="{ chosen: task.id === selectedTask.id }" @click="selectedTaskId = task.id"><span :class="['task-dot', task.status]"></span><div><strong>{{ task.name }}</strong><small>{{ task.route }} · {{ task.time }}</small></div><em>{{ task.progress }}%</em><b>{{ task.status }}</b></button></section><aside class="task-focus"><p class="section-label">选中任务</p><h2>{{ selectedTask.name }}</h2><dl><div><dt>执行设备</dt><dd>{{ selectedTask.device }}</dd></div><div><dt>负责人</dt><dd>{{ selectedTask.operator }}</dd></div><div><dt>完成进度</dt><dd>{{ selectedTask.progress }}%</dd></div></dl><button type="button">查看任务记录 →</button></aside></div>
    </section>

    <section v-else-if="activeNavigation === '事件管理'" class="module-page">
      <header class="module-intro"><p>ALERT OPERATIONS</p><h1>告警，有记录地处理。</h1><span>{{ currentModule.description }} 每次确认都会保留处理人、时间和结果。</span></header>
      <div class="alert-layout">
        <section class="alert-list">
          <article :class="{ selected: alert.active }">
            <span :class="['alert-level', { resolved: alert.acknowledged }]">{{ alert.level }}</span>
            <div><strong>{{ alert.title }}</strong><small>{{ alert.device }} · {{ alert.occurredAt || '尚未发生' }}</small></div>
            <b>{{ alert.handlingStatus }}</b>
          </article>
        </section>
        <aside class="alert-detail">
          <div class="alert-detail-heading"><div><p class="section-label">告警详情</p><h2>{{ alert.title }}</h2></div><span :class="{ resolved: alert.acknowledged }">{{ alert.handlingStatus }}</span></div>
          <dl><div><dt>告警设备</dt><dd>{{ alert.device }}</dd></div><div><dt>告警等级</dt><dd>{{ alert.level }}</dd></div><div><dt>发生时间</dt><dd>{{ alert.occurredAt || '—' }}</dd></div><div><dt>告警原因</dt><dd>{{ alert.detail }}</dd></div></dl>
          <form v-if="alertPending" class="alert-form" @submit.prevent="acknowledgeAlert">
            <label for="handling-result">处理结果</label>
            <textarea id="handling-result" v-model="acknowledgementResult" rows="4" placeholder="例如：已确认现场情况，继续观察风速变化。"></textarea>
            <p v-if="acknowledgementMessage">{{ acknowledgementMessage }}</p>
            <button type="submit" :disabled="acknowledgementSubmitting">{{ acknowledgementSubmitting ? '正在确认…' : '确认告警' }}</button>
          </form>
          <section v-else-if="alert.acknowledged" class="handling-record"><p class="section-label">处理记录</p><dl><div><dt>处理人</dt><dd>{{ alert.handledBy }}</dd></div><div><dt>处理时间</dt><dd>{{ alert.handledAt }}</dd></div><div><dt>处理结果</dt><dd>{{ alert.handlingResult }}</dd></div></dl></section>
        </aside>
      </div>
    </section>

    <section v-else class="module-page empty-state"><p>COMING SOON</p><h1>{{ currentModule.name }}</h1><span>{{ currentModule.description }} 完整功能正在规划中。</span></section>
  </main>
</template>
