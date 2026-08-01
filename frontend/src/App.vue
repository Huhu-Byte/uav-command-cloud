<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const navigationItems = [
  { name: '指挥中心', description: '实时掌握正在执行的巡检任务。' },
  { name: '实时监控', description: '集中查看现场画面、飞行参数和链路状态。' },
  { name: '任务管理', description: '安排、查看和跟进每一项巡检任务。' },
  { name: '设备管理', description: '管理无人机、机场和相关设备。' },
  { name: '事件管理', description: '查看和处理巡检中发现的问题。' },
  { name: '成果中心', description: '按任务查看巡检照片、视频和采集结果。' },
  { name: '操作历史', description: '集中查看返航和告警确认的留痕记录。' },
  { name: '系统管理', description: '查看服务连接、数据来源和接入准备状态。' },
  { name: '航线规划', description: '在地图上制作、编辑并保存可复用的飞行航线。' },
]

const activeNavigation = ref('指挥中心')
const taskFilter = ref('全部')
const selectedTaskId = ref(1)
const acknowledgementResult = ref('')
const acknowledgementMessage = ref('')
const acknowledgementMessageType = ref('')
const acknowledgementSubmitting = ref(false)
const returnMessage = ref('')
const returnMessageType = ref('')
const returnSubmitting = ref(false)
const returnScenario = ref('')
const returnConfirmationOpen = ref(false)
const historyTypeFilter = ref('ALL')
const historyResultFilter = ref('ALL')
const activityHistory = ref([])
const selectedActivityKey = ref('')
const historyLoading = ref(false)
const historyError = ref('')
const selectedDeviceName = ref('巡检无人机 02')
const selectedResultId = ref(1)
const systemReadiness = ref(null)
const systemReadinessLoading = ref(false)
const systemReadinessError = ref('')
const taskDialogOpen = ref(false)
const taskDetailOpen = ref(false)
const editingTaskId = ref(null)
const taskFormMessage = ref('')
const taskFormSubmitting = ref(false)
const taskLoading = ref(false)
const taskLoadError = ref('')
const resultTypeFilter = ref('ALL')
const resultStatusFilter = ref('ALL')
const resultTaskFilter = ref('ALL')
const resultPreviewOpen = ref(false)
const resultExportMessage = ref('')
const resultsLoading = ref(false)
const resultsLoadError = ref('')
const demoAccounts = [
  { id: 'operator', name: '张晨', role: 'FLIGHT_OPERATOR', roleLabel: '飞行操作员' },
  { id: 'admin', name: '李敏', role: 'ADMIN', roleLabel: '管理员' },
  { id: 'viewer', name: '访客演示', role: 'VIEWER', roleLabel: '只读访客' },
]
const selectedAccountId = ref('operator')
const currentModule = computed(() => navigationItems.find((item) => item.name === activeNavigation.value))

const tasks = ref([
  { id: 1, name: '园区东侧例行巡检', route: '东侧围栏巡检路线', device: '巡检无人机 02', status: '执行中', progress: 68, time: '今天 09:30', operator: '张晨' },
  { id: 2, name: '屋顶光伏设备检查', route: '屋顶光伏巡检路线', device: '巡检无人机 01', status: '待执行', progress: 0, time: '今天 14:00', operator: '李然' },
  { id: 3, name: '北门周界安全巡检', route: '北门周界巡检路线', device: '巡检无人机 01', status: '已完成', progress: 100, time: '今天 08:10', operator: '王敏' },
])
const taskForm = ref({
  name: '',
  device: '巡检无人机 01',
  scheduledAt: '',
  frequency: '一次性',
  route: '待规划路线（航线规划暂缓）',
})

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
  returnStatus: {
    inProgress: false,
    phase: '待命',
    message: '无人机正常执行任务，未触发返航',
    returnProgress: 0,
    lastOperation: null,
  },
})
const connectionState = ref('连接实时服务中')
const realtimeFormatError = ref('')
const devices = computed(() => liveStatus.value.devices)
const dataSourceLabel = computed(() => liveStatus.value.source?.label || '演示数据')
const lastUpdatedLabel = computed(() => {
  if (!liveStatus.value.updatedAt) return ''
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
    .format(new Date(liveStatus.value.updatedAt))
})
const offlineDevices = computed(() => devices.value.filter((device) => device.connectionStatus === 'OFFLINE'))
const dataHealth = computed(() => {
  if (realtimeFormatError.value) return { state: 'error', message: realtimeFormatError.value }
  if (!liveStatus.value.source || !liveStatus.value.updatedAt) return { state: 'pending', message: '正在等待符合约定格式的实时数据' }

  const updatedAt = Date.parse(liveStatus.value.updatedAt)
  if (Number.isNaN(updatedAt)) return { state: 'error', message: '实时数据的更新时间格式错误，已停止使用这份数据进行控制判断' }

  const staleAfterSeconds = Number(liveStatus.value.staleAfterSeconds)
  if (!Number.isFinite(staleAfterSeconds) || staleAfterSeconds <= 0) {
    return { state: 'error', message: '实时数据缺少有效的过期时限，已停止使用这份数据进行控制判断' }
  }

  const ageSeconds = Math.max(0, Math.floor((now.value.getTime() - updatedAt) / 1000))
  if (ageSeconds > staleAfterSeconds) return { state: 'stale', message: `实时数据已 ${ageSeconds} 秒未更新，可能过期；暂不允许发起控制操作` }
  return { state: 'normal', message: '实时数据正常' }
})
const activeAccount = computed(() => demoAccounts.find((account) => account.id === selectedAccountId.value) || null)
const canControl = computed(() => ['FLIGHT_OPERATOR', 'ADMIN'].includes(activeAccount.value?.role) && dataHealth.value.state === 'normal')
const identityLabel = computed(() => activeAccount.value
  ? `${activeAccount.value.name} · ${activeAccount.value.roleLabel}`
  : '未登录')
const returnScenarioDescription = computed(() => {
  if (returnScenario.value === 'low_battery') return '演示低电量：按 12% 电量执行安全检查，预期被后端拒绝。'
  if (returnScenario.value === 'offline') return '演示设备离线：预期被后端拒绝。'
  if (returnScenario.value === 'high_risk_weather') return '演示高风险天气：预期被后端拒绝。'
  return '正常返航：后端将检查设备在线、电量、天气、任务状态和重复请求。'
})
const alert = computed(() => liveStatus.value.alert)
const alertPending = computed(() => alert.value.active && !alert.value.acknowledged)
const returnStatus = computed(() => liveStatus.value.returnStatus || {
  inProgress: false,
  phase: '待命',
  message: '',
  returnProgress: 0,
  lastOperation: null,
})
const returnCompleted = computed(() => returnStatus.value.phase === '已完成')
const missionInProgress = computed(() => liveStatus.value.progress < 100 && !returnStatus.value.inProgress && !returnCompleted.value)
const currentTaskStatus = computed(() => {
  if (returnStatus.value.inProgress) return '返航中'
  if (returnCompleted.value) return '已中止'
  return liveStatus.value.progress < 100 ? '执行中' : '已完成'
})
const synchronizedTasks = computed(() => tasks.value.map((task) => task.name === '园区东侧例行巡检'
  ? {
      ...task,
      status: currentTaskStatus.value,
      progress: liveStatus.value.progress,
    }
  : task))
const filteredTasks = computed(() => taskFilter.value === '全部'
  ? synchronizedTasks.value
  : synchronizedTasks.value.filter((task) => task.status === taskFilter.value))
const selectedTask = computed(() => synchronizedTasks.value.find((task) => task.id === selectedTaskId.value) || synchronizedTasks.value[0])
const selectedActivity = computed(() => activityHistory.value.find((record) => record.key === selectedActivityKey.value) || activityHistory.value[0] || null)
const selectedDevice = computed(() => devices.value.find((device) => device.name === selectedDeviceName.value) || devices.value[0] || null)
const inspectionResults = ref([
  { id: 1, type: 'PHOTO', title: '东侧围栏连接点', task: '园区东侧例行巡检', device: '巡检无人机 02', capturedAt: '今天 10:42', location: '东侧围栏 K12', status: '已归档', tone: 'orange' },
  { id: 2, type: 'VIDEO', title: '仓库屋顶连续巡视', task: '园区东侧例行巡检', device: '巡检无人机 02', capturedAt: '今天 10:36', location: '仓库区 A3', status: '可播放', tone: 'blue' },
  { id: 3, type: 'PHOTO', title: '北门周界全景', task: '北门周界安全巡检', device: '巡检无人机 01', capturedAt: '今天 08:24', location: '北门检查点', status: '已归档', tone: 'green' },
  { id: 4, type: 'PHOTO', title: '光伏板表面记录', task: '屋顶光伏设备检查', device: '巡检无人机 01', capturedAt: '待任务执行', location: '屋顶光伏区', status: '待采集', tone: 'gray' },
])
const filteredInspectionResults = computed(() => inspectionResults.value.filter((result) => {
  const matchesType = resultTypeFilter.value === 'ALL' || result.type === resultTypeFilter.value
  const matchesStatus = resultStatusFilter.value === 'ALL' || result.status === resultStatusFilter.value
  const matchesTask = resultTaskFilter.value === 'ALL' || result.task === resultTaskFilter.value
  return matchesType && matchesStatus && matchesTask
}))
const selectedResult = computed(() => filteredInspectionResults.value.find((result) => result.id === selectedResultId.value) || filteredInspectionResults.value[0] || null)
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

const backendBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1/dashboard'
const realtimeServiceUrl = import.meta.env.VITE_WS_URL
  || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws/drone-status`

function identityHeaders() {
  if (!activeAccount.value) return {}
  return {
    'X-Demo-User': activeAccount.value.name,
    'X-Demo-Role': activeAccount.value.role,
  }
}

async function requestDashboardApi(path, options, unavailableMessage) {
  let response
  try {
    response = await fetch(`${backendBaseUrl}${path}`, options)
  } catch {
    throw new Error(unavailableMessage)
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const fallbackMessages = {
      400: '提交的信息不正确，请检查后重试。',
      403: '当前身份没有执行此操作的权限。',
      404: '请求的服务不存在，请检查后端是否已更新。',
      409: '当前状态已发生变化，请刷新实时数据后重试。',
      500: '后端服务暂时出错，请稍后重试。',
    }
    throw new Error(errorBody?.message || fallbackMessages[response.status] || '请求暂时无法完成，请稍后重试。')
  }

  return response.json()
}

function createDefaultTaskForm() {
  return {
    name: '',
    device: '巡检无人机 01',
    scheduledAt: '',
    frequency: '一次性',
    route: '待规划路线（航线规划暂缓）',
  }
}

function formatTaskTime(value) {
  if (!value) return '待安排'
  const time = new Date(value)
  if (Number.isNaN(time.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(time)
}

function openTaskDialog() {
  editingTaskId.value = null
  taskForm.value = createDefaultTaskForm()
  taskFormMessage.value = ''
  taskDialogOpen.value = true
}

function openTaskEditor() {
  if (selectedTask.value?.status !== '待执行') return
  editingTaskId.value = selectedTask.value.id
  taskForm.value = {
    name: selectedTask.value.name,
    device: selectedTask.value.device,
    scheduledAt: selectedTask.value.scheduledAt?.slice(0, 16) || '',
    frequency: selectedTask.value.frequency || '一次性',
    route: selectedTask.value.route,
  }
  taskFormMessage.value = ''
  taskDetailOpen.value = false
  taskDialogOpen.value = true
}

function closeTaskDialog() {
  taskDialogOpen.value = false
  taskFormMessage.value = ''
}

function toTaskView(task) {
  return {
    id: task.id,
    name: task.name,
    route: task.route,
    device: task.device,
    status: task.status,
    progress: task.progress,
    time: `${formatTaskTime(task.scheduledAt)} · ${task.frequency}`,
    scheduledAt: task.scheduledAt,
    frequency: task.frequency,
    operator: task.operator,
  }
}

async function loadTasks() {
  taskLoading.value = true
  taskLoadError.value = ''
  try {
    const loadedTasks = await requestDashboardApi('/tasks', undefined, '暂时无法读取任务列表，请确认后端已启动。')
    tasks.value = loadedTasks.map(toTaskView)
    if (!tasks.value.some((task) => task.id === selectedTaskId.value)) selectedTaskId.value = tasks.value[0]?.id || 0
  } catch (error) {
    taskLoadError.value = error.message
  } finally {
    taskLoading.value = false
  }
}

async function createTask() {
  if (taskFormSubmitting.value) return
  const name = taskForm.value.name.trim()
  if (!name || !taskForm.value.scheduledAt) {
    taskFormMessage.value = '请填写任务名称和计划执行时间。'
    return
  }

  taskFormSubmitting.value = true
  taskFormMessage.value = ''
  try {
    const task = toTaskView(await requestDashboardApi(editingTaskId.value ? `/tasks/${editingTaskId.value}` : '/tasks', {
      method: editingTaskId.value ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json', ...identityHeaders() },
      body: JSON.stringify({
        name,
        device: taskForm.value.device,
        scheduledAt: taskForm.value.scheduledAt,
        frequency: taskForm.value.frequency,
        route: taskForm.value.route,
      }),
    }, '暂时无法保存任务，请确认后端已启动后重试。'))
    if (editingTaskId.value) tasks.value = tasks.value.map((item) => item.id === task.id ? task : item)
    else tasks.value.push(task)
    selectedTaskId.value = task.id
    taskFilter.value = '全部'
    taskDialogOpen.value = false
    taskDetailOpen.value = true
  } catch (error) {
    taskFormMessage.value = error.message
  } finally {
    taskFormSubmitting.value = false
  }
}

function openResultPreview() {
  if (selectedResult.value) resultPreviewOpen.value = true
}

function exportResultList() {
  const rows = [
    ['类型', '标题', '关联任务', '采集设备', '采集时间', '采集位置', '状态'],
    ...filteredInspectionResults.value.map((result) => [
      result.type === 'VIDEO' ? '视频' : '照片',
      result.title,
      result.task,
      result.device,
      result.capturedAt,
      result.location,
      result.status,
    ]),
  ]
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n')
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' }))
  link.download = '巡检成果清单.csv'
  link.click()
  URL.revokeObjectURL(link.href)
  resultExportMessage.value = `已按当前筛选导出 ${filteredInspectionResults.value.length} 条成果清单。`
}

function resultTone(result) {
  if (result.status === '待采集') return 'gray'
  if (result.type === 'VIDEO') return 'blue'
  return 'orange'
}

async function loadInspectionResults() {
  resultsLoading.value = true
  resultsLoadError.value = ''
  try {
    const results = await requestDashboardApi('/results', undefined, '暂时无法读取成果清单，请确认后端已启动。')
    inspectionResults.value = results.map((result) => ({
      id: result.id,
      type: result.type,
      title: result.title,
      task: result.taskName,
      device: result.device,
      capturedAt: result.capturedAt ? formatTaskTime(result.capturedAt) : '待任务执行',
      location: result.location,
      status: result.status,
      tone: resultTone(result),
    }))
  } catch (error) {
    resultsLoadError.value = error.message
  } finally {
    resultsLoading.value = false
  }
}

// 后端统一数据合同会同时服务模拟器和未来的 DJI Cloud API。
// 页面暂时继续使用已有字段显示，因此只在这里完成一次转换，避免业务区域直接依赖某个设备厂商的字段。
function normalizeLiveStatus(payload) {
  validateRealtimeSnapshot(payload)

  const task = payload.task || {}
  const summary = payload.summary || {}
  const activeDevice = payload.devices?.find((device) => device.id === task.deviceId) || payload.devices?.[0] || {}
  const alert = payload.alerts?.[0] || {}
  const returnCommand = payload.returnCommand || {}
  const deviceStatusLabels = {
    FLYING: '正在飞行',
    RETURNING: '正在返航',
    RETURNED: '已返航',
    IDLE: '在线待命',
    OFFLINE: '离线',
  }

  return {
    progress: task.progress ?? 0,
    altitude: task.altitude ?? activeDevice.altitude ?? 0,
    battery: activeDevice.battery ?? 0,
    estimatedCompletion: task.estimatedCompletion || '--',
    onlineDeviceCount: summary.onlineDeviceCount ?? 0,
    todayDistance: summary.todayDistance ?? 0,
    inspectedPoints: summary.inspectedPoints ?? 0,
    totalPoints: summary.totalPoints ?? 0,
    devices: (payload.devices || []).map((device) => ({
      name: device.name,
      connectionStatus: device.connectionStatus,
      status: deviceStatusLabels[device.operationalStatus] || device.operationalStatus || '未知',
      battery: device.battery,
    })),
    alert: {
      active: alert.active ?? false,
      level: alert.level || '无',
      title: alert.title || '当前无告警',
      detail: alert.detail || '设备和气象状态正常',
      occurredAt: alert.occurredAt || '',
      device: alert.deviceName || '',
      acknowledged: alert.acknowledged ?? false,
      handlingStatus: alert.handlingStatus || '无需处理',
      handledBy: alert.handledBy || '',
      handledAt: alert.handledAt || '',
      handlingResult: alert.handlingResult || '',
    },
    returnStatus: {
      inProgress: returnCommand.status === 'IN_PROGRESS',
      phase: returnCommand.phase || '待命',
      message: returnCommand.message || '',
      returnProgress: returnCommand.progress ?? 0,
      lastOperation: returnCommand.lastOperation || null,
    },
    source: payload.source,
    updatedAt: payload.updatedAt,
    staleAfterSeconds: payload.staleAfterSeconds,
  }
}

function validateRealtimeSnapshot(payload) {
  if (!payload || payload.schemaVersion !== '1.0') throw new Error('实时数据格式不受支持，已停止使用这份数据进行控制判断')
  if (!payload.source?.type || !payload.source?.label || !payload.updatedAt) throw new Error('实时数据缺少来源或更新时间，已停止使用这份数据进行控制判断')
  if (!Number.isFinite(Number(payload.staleAfterSeconds)) || Number(payload.staleAfterSeconds) <= 0) throw new Error('实时数据缺少有效的过期时限，已停止使用这份数据进行控制判断')
  if (!payload.task || !payload.summary || !payload.returnCommand || !Array.isArray(payload.devices) || !Array.isArray(payload.alerts)) {
    throw new Error('实时数据缺少任务、设备、告警或控制信息，已停止使用这份数据进行控制判断')
  }
  if (Number.isNaN(Date.parse(payload.updatedAt))) throw new Error('实时数据的更新时间格式错误，已停止使用这份数据进行控制判断')
}

function applyRealtimeMessage(serializedPayload) {
  try {
    applyRealtimeSnapshot(JSON.parse(serializedPayload))
  } catch (error) {
    // 已由统一处理方法保存页面提示；WebSocket 消息异常不应中断下一次推送。
  }
}

function applyRealtimeSnapshot(payload) {
  try {
    const status = normalizeLiveStatus(payload)
    liveStatus.value = status
    realtimeFormatError.value = ''
    return status
  } catch (error) {
    realtimeFormatError.value = error.message || '实时数据格式错误，已停止使用这份数据进行控制判断'
    throw error
  }
}

function connectRealtimeService() {
  socket = new WebSocket(realtimeServiceUrl)

  socket.onopen = () => { connectionState.value = '实时数据已连接' }
  socket.onmessage = (event) => {
    applyRealtimeMessage(event.data)
    connectionState.value = '实时数据已连接'
  }
  socket.onclose = () => {
    connectionState.value = '演示数据（后端未连接）'
    reconnectTimer = window.setTimeout(connectRealtimeService, 5000)
  }
}

async function acknowledgeAlert() {
  if (!canControl.value) {
    acknowledgementMessage.value = '请先以飞行操作员或管理员身份登录。'
    acknowledgementMessageType.value = 'error'
    return
  }
  if (!acknowledgementResult.value.trim()) {
    acknowledgementMessage.value = '请先填写处理结果。'
    acknowledgementMessageType.value = 'error'
    return
  }

  acknowledgementSubmitting.value = true
  acknowledgementMessage.value = ''
  acknowledgementMessageType.value = ''
  try {
    liveStatus.value = applyRealtimeSnapshot(await requestDashboardApi('/alerts/current/acknowledge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...identityHeaders() },
      body: JSON.stringify({ result: acknowledgementResult.value.trim() }),
    }, '暂时无法连接后端服务，请确认后端已启动后重试。'))
    acknowledgementMessage.value = '告警已确认，处理记录已由后端保存。'
    acknowledgementMessageType.value = 'success'
  } catch (error) {
    acknowledgementMessage.value = error.message
    acknowledgementMessageType.value = 'error'
  } finally {
    acknowledgementSubmitting.value = false
  }
}

function openReturnConfirmation() {
  if (!canControl.value) {
    returnMessage.value = '请先以飞行操作员或管理员身份登录。'
    returnMessageType.value = 'error'
    return
  }
  returnMessage.value = ''
  returnMessageType.value = ''
  returnConfirmationOpen.value = true
}

function cancelReturnConfirmation() {
  if (!returnSubmitting.value) returnConfirmationOpen.value = false
}

async function submitReturn() {
  if (returnSubmitting.value) return

  returnSubmitting.value = true
  try {
    const result = await requestDashboardApi('/return', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...identityHeaders() },
      body: JSON.stringify({ scenario: returnScenario.value || null }),
    }, '暂时无法连接后端服务，请确认后端已启动后重试。')
    liveStatus.value = applyRealtimeSnapshot(result.status)
    returnMessage.value = result.success ? result.message : `返航请求失败：${result.message}`
    returnMessageType.value = result.success ? 'success' : 'error'
  } catch (error) {
    returnMessage.value = error.message
    returnMessageType.value = 'error'
  } finally {
    returnSubmitting.value = false
    returnConfirmationOpen.value = false
  }
}

async function loadActivityHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const search = new URLSearchParams({ type: historyTypeFilter.value, result: historyResultFilter.value })
    activityHistory.value = await requestDashboardApi(`/history?${search}`, undefined, '暂时无法连接后端服务，无法读取操作历史。')
    if (!activityHistory.value.some((record) => record.key === selectedActivityKey.value)) {
      selectedActivityKey.value = activityHistory.value[0]?.key || ''
    }
  } catch (error) {
    historyError.value = error.message
  } finally {
    historyLoading.value = false
  }
}

async function loadSystemReadiness() {
  systemReadinessLoading.value = true
  systemReadinessError.value = ''
  try {
    systemReadiness.value = await requestDashboardApi(
      '/integration/dji/readiness',
      undefined,
      '暂时无法读取系统接入状态，请确认后端服务已启动。',
    )
  } catch (error) {
    systemReadinessError.value = error.message
  } finally {
    systemReadinessLoading.value = false
  }
}

watch([activeNavigation, historyTypeFilter, historyResultFilter], ([navigation]) => {
  if (navigation === '操作历史') loadActivityHistory()
  if (navigation === '系统管理') loadSystemReadiness()
  if (navigation === '任务管理') loadTasks()
  if (navigation === '成果中心') loadInspectionResults()
  if (navigation === '航线规划') loadFlightRoutes()
})

watch([resultTypeFilter, resultStatusFilter, resultTaskFilter], () => {
  if (!filteredInspectionResults.value.some((result) => result.id === selectedResultId.value)) {
    selectedResultId.value = filteredInspectionResults.value[0]?.id || 0
  }
  resultExportMessage.value = ''
})

// ========== 航线规划相关状态 ==========
const flightRoutes = ref([])
const selectedRouteId = ref(null)
const routesLoading = ref(false)
const routesLoadError = ref('')
const routeSaveMessage = ref('')
const routeSaveMessageType = ref('')
const routeSubmitting = ref(false)
const routeValidating = ref(false)
const routeValidateReport = ref(null)
const allowUsedRouteModification = ref(false)
const deleteConfirmOpen = ref(false)
const deletingRouteId = ref(null)

const routeForm = ref({
  name: '',
  area: '园区东侧',
  mode: 'WAYPOINT_FLIGHT',
})

const routeAreas = [
  '园区东侧',
  '园区西侧',
  '北门区域',
  '南门区域',
  '屋顶光伏区',
  '仓库区',
  '自定义区域',
]

const flightModes = [
  { value: 'WAYPOINT_FLIGHT', label: '航点飞行' },
  { value: 'AERIAL_PHOTOGRAPHY', label: '航拍测绘' },
]

const payloadOptions = ['主相机', '红外相机', '变焦相机']
const actionOptions = [
  { value: 'TAKE_PHOTO', label: '拍照' },
  { value: 'RECORD_VIDEO', label: '录像' },
  { value: 'HOVER', label: '悬停' },
  { value: 'NONE', label: '无动作' },
]

// 航点数据
const waypoints = ref([])
const selectedWaypointIndex = ref(-1)
const defaultWaypointAction = 'TAKE_PHOTO'
const defaultWaypointPayload = '主相机'

const selectedRoute = computed(() => flightRoutes.value.find((r) => r.id === selectedRouteId.value) || null)

const routeTotalDistance = computed(() => {
  if (waypoints.value.length < 2) return 0
  let total = 0
  const R = 6371000
  for (let i = 0; i < waypoints.value.length - 1; i++) {
    const a = waypoints.value[i]
    const b = waypoints.value[i + 1]
    const dLat = (b.lat - a.lat) * Math.PI / 180
    const dLng = (b.lng - a.lng) * Math.PI / 180
    const lat1 = a.lat * Math.PI / 180
    const lat2 = b.lat * Math.PI / 180
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
    total += 2 * R * Math.asin(Math.sqrt(h))
  }
  return Math.round(total)
})

const routeEstimatedDuration = computed(() => {
  if (waypoints.value.length === 0 || routeTotalDistance.value === 0) return 0
  const avgSpeed = waypoints.value.reduce((s, w) => s + (w.speed || 8), 0) / waypoints.value.length || 8
  return Math.round(routeTotalDistance.value / Math.max(1, avgSpeed) + waypoints.value.length * 2)
})

function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '—'
  if (seconds < 60) return `${seconds} 秒`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m < 60 ? `${m} 分 ${s} 秒` : `${Math.floor(m / 60)} 时 ${m % 60} 分`
}

// ========== 航线规划 API 调用 ==========
async function loadFlightRoutes() {
  routesLoading.value = true
  routesLoadError.value = ''
  try {
    flightRoutes.value = await requestDashboardApi('/routes', undefined, '暂时无法读取航线列表，请确认后端已启动。')
    if (!flightRoutes.value.some((r) => r.id === selectedRouteId.value)) {
      selectedRouteId.value = flightRoutes.value[0]?.id || null
      if (selectedRouteId.value) selectRoute(selectedRouteId.value)
    }
  } catch (error) {
    routesLoadError.value = error.message
  } finally {
    routesLoading.value = false
  }
}

function createNewRoute() {
  selectedRouteId.value = null
  routeForm.value = {
    name: '',
    area: '园区东侧',
    mode: 'WAYPOINT_FLIGHT',
  }
  waypoints.value = []
  selectedWaypointIndex.value = -1
  routeSaveMessage.value = ''
  routeValidateReport.value = null
  allowUsedRouteModification.value = false
}

function selectRoute(id) {
  const route = flightRoutes.value.find((r) => r.id === id)
  if (!route) return
  selectedRouteId.value = id
  routeForm.value = {
    name: route.name,
    area: route.area,
    mode: route.mode,
  }
  waypoints.value = (route.waypoints || []).map((wp) => ({ ...wp }))
  selectedWaypointIndex.value = -1
  routeSaveMessage.value = ''
  routeValidateReport.value = null
  allowUsedRouteModification.value = false
}

function addWaypoint() {
  const index = waypoints.value.length + 1
  const baseLat = waypoints.value.length > 0
    ? waypoints.value[waypoints.value.length - 1].lat + 0.001
    : 39.9042
  const baseLng = waypoints.value.length > 0
    ? waypoints.value[waypoints.value.length - 1].lng + 0.001
    : 116.4074
  waypoints.value.push({
    code: `WP${String(index).padStart(2, '0')}`,
    label: `航点 ${index}`,
    lat: Number(baseLat.toFixed(6)),
    lng: Number(baseLng.toFixed(6)),
    altitude: 60,
    speed: 8,
    action: defaultWaypointAction,
    payload: defaultWaypointPayload,
    actionParam: '单次拍摄',
  })
  selectedWaypointIndex.value = waypoints.value.length - 1
}

function copyWaypoint(index) {
  const src = waypoints.value[index]
  if (!src) return
  const copy = { ...src, code: `WP${String(waypoints.value.length + 1).padStart(2, '0')}` }
  waypoints.value.splice(index + 1, 0, copy)
  selectedWaypointIndex.value = index + 1
}

function removeWaypoint(index) {
  waypoints.value.splice(index, 1)
  if (selectedWaypointIndex.value >= waypoints.value.length) {
    selectedWaypointIndex.value = waypoints.value.length - 1
  }
  // 重新编号
  waypoints.value.forEach((wp, i) => {
    wp.code = `WP${String(i + 1).padStart(2, '0')}`
  })
}

function moveWaypoint(index, direction) {
  const target = index + direction
  if (target < 0 || target >= waypoints.value.length) return
  const [item] = waypoints.value.splice(index, 1)
  waypoints.value.splice(target, 0, item)
  // 重新编号
  waypoints.value.forEach((wp, i) => {
    wp.code = `WP${String(i + 1).padStart(2, '0')}`
  })
  selectedWaypointIndex.value = target
}

function waypointActionLabel(action) {
  return actionOptions.find((o) => o.value === action)?.label || action || '无动作'
}

async function validateCurrentRoute() {
  routeValidating.value = true
  routeValidateReport.value = null
  try {
    routeValidateReport.value = await requestDashboardApi('/routes/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: routeForm.value.name,
        area: routeForm.value.area,
        mode: routeForm.value.mode,
        waypointsJson: JSON.stringify(waypoints.value),
        allowUsedRouteModification: allowUsedRouteModification.value,
      }),
    }, '暂时无法校验航线，请确认后端服务可用。')
  } catch (error) {
    routeValidateReport.value = {
      valid: false,
      errors: [error.message],
      warnings: [],
      waypointCount: waypoints.value.length,
      totalDistanceMeters: routeTotalDistance.value,
      estimatedDurationSeconds: routeEstimatedDuration.value,
    }
  } finally {
    routeValidating.value = false
  }
}

async function saveCurrentRoute() {
  if (routeSubmitting.value) return
  routeSubmitting.value = true
  routeSaveMessage.value = ''
  routeSaveMessageType.value = ''
  try {
    const payload = {
      name: routeForm.value.name.trim(),
      area: routeForm.value.area,
      mode: routeForm.value.mode,
      waypointsJson: JSON.stringify(waypoints.value),
      allowUsedRouteModification: allowUsedRouteModification.value,
    }
    const isEditing = selectedRouteId.value != null
    const url = isEditing ? `/routes/${selectedRouteId.value}` : '/routes'
    const result = await requestDashboardApi(url, {
      method: isEditing ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json', ...identityHeaders() },
      body: JSON.stringify(payload),
    }, isEditing ? '保存修改失败，请确认后端服务可用。' : '创建航线失败，请确认后端服务可用。')
    routeSaveMessage.value = isEditing ? '航线已保存修改。' : '航线已创建。'
    routeSaveMessageType.value = 'success'
    await loadFlightRoutes()
    selectedRouteId.value = result.id
    selectRoute(result.id)
  } catch (error) {
    routeSaveMessage.value = error.message
    routeSaveMessageType.value = 'error'
  } finally {
    routeSubmitting.value = false
  }
}

function openDeleteConfirm(id) {
  deletingRouteId.value = id
  deleteConfirmOpen.value = true
}

function cancelDelete() {
  deleteConfirmOpen.value = false
  deletingRouteId.value = null
}

async function confirmDeleteRoute() {
  const id = deletingRouteId.value
  if (id == null) return
  try {
    await requestDashboardApi(`/routes/${id}`, {
      method: 'DELETE',
      headers: identityHeaders(),
    }, '删除航线失败，请确认后端服务可用。')
    if (selectedRouteId.value === id) {
      createNewRoute()
    }
    await loadFlightRoutes()
    routeSaveMessage.value = '航线已删除。'
    routeSaveMessageType.value = 'success'
  } catch (error) {
    routeSaveMessage.value = error.message
    routeSaveMessageType.value = 'error'
  } finally {
    deleteConfirmOpen.value = false
    deletingRouteId.value = null
  }
}

// 用于任务表单中的航线选择
const routeOptions = computed(() => flightRoutes.value.map((r) => r.name))

onMounted(() => {
  connectRealtimeService()
  loadTasks()
  loadInspectionResults()
  loadFlightRoutes()
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => {
  window.clearTimeout(reconnectTimer)
  window.clearInterval(clockTimer)
  socket?.close()
})
</script>

<template>
  <main class="app-shell">
    <aside class="side-rail">
      <button class="brand" type="button" @click="activeNavigation = '指挥中心'">
        <span class="brand-mark">⌁</span><span>UAV <b>COMMAND</b></span>
      </button>
      <nav class="side-nav" aria-label="主导航">
        <button v-for="item in navigationItems" :key="item.name" type="button" :class="{ active: item.name === activeNavigation, deferred: item.deferred }" @click="activeNavigation = item.name">
          <span>{{ item.name }}</span><small v-if="item.deferred">暂缓</small>
        </button>
      </nav>
      <div class="sidebar-footer">
        <p class="connection-indicator"><i></i>{{ connectionState }}</p>
        <small>{{ dataSourceLabel }}<template v-if="lastUpdatedLabel"> · {{ lastUpdatedLabel }}</template></small>
        <label class="identity-control"><span>演示身份</span><select v-model="selectedAccountId" aria-label="演示身份"><option value="">未登录</option><option v-for="account in demoAccounts" :key="account.id" :value="account.id">{{ account.name }} · {{ account.roleLabel }}</option></select></label>
      </div>
    </aside>

    <section class="workspace">
      <header class="workspace-header">
        <div><p>无人机巡检指挥系统</p><strong>{{ currentModule.name }}</strong></div>
        <span>LIVE · {{ liveTime }}</span>
      </header>
      <div class="workspace-content">

    <section v-if="activeNavigation === '指挥中心'" class="dashboard">
      <header class="dashboard-intro">
        <div><p>DRONE INSPECTION · {{ todayLabel }}</p><h1>{{ returnStatus.inProgress ? '无人机正在返航' : returnCompleted ? '无人机已安全返航' : missionInProgress ? '飞行正在进行' : '巡检任务已完成' }}<span>.</span></h1><p>{{ returnStatus.inProgress ? returnStatus.message : returnCompleted ? '当前巡检任务已中止，无人机已安全返回起飞点。' : missionInProgress ? `园区东侧例行巡检已完成 ${liveStatus.progress}%，无人机 02 正在执行预设航线。` : '园区东侧例行巡检已完成，等待下一项任务安排。' }}</p></div>
        <button type="button" class="quiet-button" @click="activeNavigation = '任务管理'">查看全部任务 <span>↗</span></button>
      </header>

      <section v-if="dataHealth.state !== 'normal' || offlineDevices.length" class="data-health-notices" aria-live="polite">
        <p v-if="dataHealth.state !== 'normal'" :class="['data-health-notice', dataHealth.state]"><b>数据状态</b>{{ dataHealth.message }}</p>
        <p v-if="offlineDevices.length" class="data-health-notice offline"><b>设备离线</b>{{ offlineDevices.map((device) => device.name).join('、') }} 当前离线；请核实设备和网络连接。</p>
      </section>

      <section class="flight-stage">
        <div class="stage-map" aria-label="园区东侧实时航线示意图">
          <div class="map-grid"></div><span class="place place-a">北门</span><span class="place place-b">东侧围栏</span><span class="place place-c">仓库区</span>
          <div class="flight-route"><i></i><i></i><i></i><i></i></div><div class="drone-pin">✦</div>
          <div class="map-caption"><span><i></i>实时航线</span><b>LIVE · {{ liveTime }}</b></div>
        </div>
        <aside class="flight-brief">
          <p class="section-label">当前任务</p><h2>园区东侧<br>例行巡检</h2><span class="live-label">● {{ returnStatus.inProgress ? '返航中' : returnCompleted ? '已返航' : missionInProgress ? '飞行中' : '已完成' }}</span>
          <div class="mission-progress"><div><span>执行进度</span><strong>{{ liveStatus.progress }}%</strong></div><i><b :style="{ width: `${liveStatus.progress}%` }"></b></i></div>
          <dl><div><dt>飞行高度</dt><dd>{{ liveStatus.altitude }} m</dd></div><div><dt>剩余电量</dt><dd>{{ liveStatus.battery }}%</dd></div><div><dt>预计完成</dt><dd>{{ liveStatus.estimatedCompletion }}</dd></div></dl>
          <button type="button" class="return-button" :disabled="returnSubmitting || !missionInProgress || returnStatus.inProgress || returnCompleted || !canControl" @click="openReturnConfirmation">{{ returnSubmitting ? '处理中…' : returnStatus.inProgress ? '返航中' : '立即返航' }}</button>
          <p v-if="!canControl" class="permission-hint">当前身份：{{ identityLabel }}。请切换为飞行操作员或管理员后操作。</p>
          <select v-model="returnScenario" class="return-scenario-select" :disabled="returnSubmitting || returnStatus.inProgress || returnCompleted || !canControl">
            <option value="">正常返航（安全检查）</option>
            <option value="low_battery">演示：低电量拒绝</option>
            <option value="offline">演示：设备离线拒绝</option>
            <option value="high_risk_weather">演示：高风险天气拒绝</option>
          </select>
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
        <article :class="['return-section', { active: returnStatus.inProgress }]">
          <p class="section-label">返航状态</p><h2>{{ returnStatus.phase }}</h2><p>{{ returnStatus.message }}</p>
          <div v-if="returnStatus.inProgress" class="return-progress"><div><span>返航进度</span><strong>{{ returnStatus.returnProgress }}%</strong></div><i><b :style="{ width: `${returnStatus.returnProgress}%` }"></b></i></div>
          <p v-if="returnMessage" :class="['return-message', returnMessageType]" aria-live="polite">{{ returnMessage }}</p>
          <section v-if="returnStatus.lastOperation" class="return-record"><p class="section-label">最近操作</p><dl><div><dt>操作人</dt><dd>{{ returnStatus.lastOperation.operator }}</dd></div><div><dt>操作时间</dt><dd>{{ returnStatus.lastOperation.timestamp }}</dd></div><div><dt>操作结果</dt><dd :class="{ success: returnStatus.lastOperation.result === '成功', failed: returnStatus.lastOperation.result === '失败' }">{{ returnStatus.lastOperation.result }}</dd></div><div><dt>原因</dt><dd>{{ returnStatus.lastOperation.reason }}</dd></div></dl></section>
        </article>
      </section>
    </section>

    <section v-else-if="activeNavigation === '实时监控'" class="module-page">
      <header class="module-intro"><p>LIVE MONITORING</p><h1>现场，一屏看清。</h1><span>{{ currentModule.description }}</span></header>
      <div class="monitor-layout">
        <section class="monitor-stage">
          <div class="monitor-visual">
            <span class="monitor-live"><i></i> LIVE · {{ liveTime }}</span>
            <div class="monitor-reticle"><b></b><b></b><b></b><b></b></div>
            <div class="monitor-caption"><small>当前画面</small><strong>巡检无人机 02 · 主相机</strong><span>模拟画面占位，接入真实视频流后在此播放</span></div>
          </div>
          <div class="monitor-telemetry"><article><span>飞行高度</span><strong>{{ liveStatus.altitude }} m</strong></article><article><span>剩余电量</span><strong>{{ liveStatus.battery }}%</strong></article><article><span>任务进度</span><strong>{{ liveStatus.progress }}%</strong></article><article><span>数据来源</span><strong>{{ dataSourceLabel }}</strong></article></div>
        </section>
        <aside class="monitor-sidebar">
          <div class="section-heading"><div><p class="section-label">VIDEO CHANNELS</p><h2>监控通道</h2></div><span>{{ connectionState }}</span></div>
          <button v-for="device in devices" :key="device.name" type="button" :class="{ selected: device.name === selectedDeviceName, offline: device.status === '离线' }" @click="selectedDeviceName = device.name">
            <span class="channel-preview">{{ device.status === '离线' ? 'OFFLINE' : 'LIVE' }}</span><div><strong>{{ device.name }}</strong><small>{{ device.status }} · 电量 {{ device.battery }}%</small></div>
          </button>
          <p class="monitor-note">当前页面已具备视频区域、通道切换和断线状态；真实视频需等待测试设备提供播放地址。</p>
        </aside>
      </div>
    </section>

    <section v-else-if="activeNavigation === '任务管理'" class="module-page">
      <header class="module-intro"><p>MISSION CONTROL</p><h1>任务，不遗漏每一步。</h1><span>{{ currentModule.description }}</span></header>
      <div class="task-tools"><div><button v-for="status in ['全部', '执行中', '返航中', '待执行', '已完成', '已中止']" :key="status" type="button" :class="{ selected: taskFilter === status }" @click="taskFilter = status">{{ status }}</button></div><div class="page-actions"><button type="button" :disabled="taskLoading" @click="loadTasks">{{ taskLoading ? '正在刷新…' : '刷新' }}</button><button class="create-task" type="button" :disabled="!canControl" @click="openTaskDialog">新建任务 +</button></div></div>
      <p v-if="taskLoadError" class="history-message error" aria-live="polite">{{ taskLoadError }}</p><p v-else-if="taskLoading" class="history-message" aria-live="polite">正在读取任务列表…</p>
      <div class="task-layout"><section class="task-list"><button v-for="task in filteredTasks" :key="task.id" type="button" :class="{ chosen: task.id === selectedTask.id }" @click="selectedTaskId = task.id"><span :class="['task-dot', task.status]"></span><div><strong>{{ task.name }}</strong><small>{{ task.route }} · {{ task.time }}</small></div><em>{{ task.progress }}%</em><b>{{ task.status }}</b></button><p v-if="!filteredTasks.length" class="history-empty">当前筛选条件下没有任务。</p></section><aside class="task-focus"><p class="section-label">选中任务</p><h2>{{ selectedTask.name }}</h2><dl><div><dt>执行设备</dt><dd>{{ selectedTask.device }}</dd></div><div><dt>负责人</dt><dd>{{ selectedTask.operator }}</dd></div><div><dt>任务状态</dt><dd>{{ selectedTask.status }}</dd></div><div><dt>完成进度</dt><dd>{{ selectedTask.progress }}%</dd></div></dl><button type="button" @click="taskDetailOpen = true">查看任务详情 →</button></aside></div>
    </section>

    <section v-else-if="activeNavigation === '设备管理'" class="module-page">
      <header class="module-intro"><p>DEVICE FLEET</p><h1>设备，状态透明。</h1><span>{{ currentModule.description }} 实时状态由统一数据接口更新。</span></header>
      <div class="device-summary"><article><span>设备总数</span><strong>{{ devices.length }}</strong><small>当前纳管无人机</small></article><article><span>在线设备</span><strong>{{ liveStatus.onlineDeviceCount }}</strong><small>可接收实时状态</small></article><article><span>离线设备</span><strong>{{ offlineDevices.length }}</strong><small>需要检查网络或电源</small></article><article><span>数据状态</span><strong>{{ dataHealth.state === 'normal' ? '正常' : '需关注' }}</strong><small>{{ lastUpdatedLabel ? `更新于 ${lastUpdatedLabel}` : '等待数据' }}</small></article></div>
      <div class="device-layout">
        <section class="device-list"><button v-for="device in devices" :key="device.name" type="button" :class="{ selected: device.name === selectedDevice?.name }" @click="selectedDeviceName = device.name"><span :class="['status-dot', { offline: device.status === '离线' }]"></span><div><strong>{{ device.name }}</strong><small>{{ device.connectionStatus === 'OFFLINE' ? '连接中断' : '遥测连接正常' }}</small></div><b>{{ device.status }}</b><em>{{ device.battery }}%</em></button></section>
        <aside v-if="selectedDevice" class="device-detail"><div class="device-detail-heading"><div><p class="section-label">DEVICE DETAIL</p><h2>{{ selectedDevice.name }}</h2></div><span :class="{ offline: selectedDevice.status === '离线' }">{{ selectedDevice.status }}</span></div><dl><div><dt>设备类型</dt><dd>行业巡检无人机</dd></div><div><dt>连接状态</dt><dd>{{ selectedDevice.connectionStatus === 'OFFLINE' ? '离线' : '在线' }}</dd></div><div><dt>当前电量</dt><dd>{{ selectedDevice.battery }}%</dd></div><div><dt>固件状态</dt><dd>版本正常</dd></div><div><dt>最近任务</dt><dd>{{ selectedDevice.name.includes('02') ? '园区东侧例行巡检' : '暂无执行中任务' }}</dd></div><div><dt>维护建议</dt><dd>{{ selectedDevice.status === '离线' ? '检查设备供电与网络连接' : '当前无需维护' }}</dd></div></dl><button type="button" @click="activeNavigation = '实时监控'">查看实时通道 →</button></aside>
      </div>
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
          <form v-if="alertPending && canControl" class="alert-form" @submit.prevent="acknowledgeAlert">
            <label for="handling-result">处理结果</label>
            <textarea id="handling-result" v-model="acknowledgementResult" rows="4" placeholder="例如：已确认现场情况，继续观察风速变化。"></textarea>
            <p v-if="acknowledgementMessage" :class="['form-message', acknowledgementMessageType]" aria-live="polite">{{ acknowledgementMessage }}</p>
            <button type="submit" :disabled="acknowledgementSubmitting">{{ acknowledgementSubmitting ? '正在确认…' : '确认告警' }}</button>
          </form>
          <p v-else-if="alertPending" class="permission-hint">当前身份：{{ identityLabel }}。请切换为飞行操作员或管理员后确认告警。</p>
          <section v-else-if="alert.acknowledged" class="handling-record"><p class="section-label">处理记录</p><dl><div><dt>处理人</dt><dd>{{ alert.handledBy }}</dd></div><div><dt>处理时间</dt><dd>{{ alert.handledAt }}</dd></div><div><dt>处理结果</dt><dd>{{ alert.handlingResult }}</dd></div></dl></section>
        </aside>
      </div>
    </section>

    <section v-else-if="activeNavigation === '成果中心'" class="module-page">
      <header class="module-intro"><p>INSPECTION RESULTS</p><h1>成果，跟任务归档。</h1><span>{{ currentModule.description }} 成果清单由后端保存；真实文件接入仍暂缓。</span></header>
      <div class="result-toolbar"><div><strong>今日成果</strong><span>{{ filteredInspectionResults.length }} 项符合当前筛选</span></div><div class="result-filters"><label>任务<select v-model="resultTaskFilter"><option value="ALL">全部任务</option><option v-for="task in tasks" :key="task.id" :value="task.name">{{ task.name }}</option></select></label><label>类型<select v-model="resultTypeFilter"><option value="ALL">全部</option><option value="PHOTO">照片</option><option value="VIDEO">视频</option></select></label><label>状态<select v-model="resultStatusFilter"><option value="ALL">全部</option><option value="已归档">已归档</option><option value="可播放">可播放</option><option value="待采集">待采集</option></select></label><button type="button" :disabled="resultsLoading" @click="loadInspectionResults">{{ resultsLoading ? '正在刷新…' : '刷新' }}</button><button type="button" :disabled="!filteredInspectionResults.length" @click="exportResultList">导出清单</button></div></div>
      <p v-if="resultsLoadError" class="history-message error" aria-live="polite">{{ resultsLoadError }}</p><p v-else-if="resultsLoading" class="history-message" aria-live="polite">正在读取成果清单…</p>
      <p v-if="resultExportMessage" class="result-message" aria-live="polite">{{ resultExportMessage }}</p>
      <div class="result-layout">
        <section class="result-grid"><button v-for="result in filteredInspectionResults" :key="result.id" type="button" :class="['result-card', result.tone, { selected: result.id === selectedResult?.id }]" @click="selectedResultId = result.id"><span class="result-type">{{ result.type === 'VIDEO' ? '视频' : '照片' }}</span><div class="result-thumbnail"><i>{{ result.status }}</i></div><strong>{{ result.title }}</strong><small>{{ result.task }} · {{ result.capturedAt }}</small></button><p v-if="!filteredInspectionResults.length" class="history-empty result-empty">当前筛选条件下没有成果。</p></section>
        <aside v-if="selectedResult" class="result-detail"><div :class="['result-hero', selectedResult.tone]"><span>{{ selectedResult.type === 'VIDEO' ? 'VIDEO PREVIEW' : 'IMAGE PREVIEW' }}</span><strong>{{ selectedResult.status }}</strong></div><p class="section-label">RESULT DETAIL</p><h2>{{ selectedResult.title }}</h2><dl><div><dt>关联任务</dt><dd>{{ selectedResult.task }}</dd></div><div><dt>采集设备</dt><dd>{{ selectedResult.device }}</dd></div><div><dt>采集时间</dt><dd>{{ selectedResult.capturedAt }}</dd></div><div><dt>采集位置</dt><dd>{{ selectedResult.location }}</dd></div></dl><button type="button" class="preview-result" @click="openResultPreview">打开预览</button><p>真实设备接入后，照片预览、视频播放和原文件下载将在此区域开放。</p></aside><aside v-else class="result-detail result-empty"><p>请选择其他筛选条件查看成果。</p></aside>
      </div>
    </section>

    <section v-else-if="activeNavigation === '操作历史'" class="module-page">
      <header class="module-intro"><p>ACTIVITY HISTORY</p><h1>每一次操作，都能追溯。</h1><span>{{ currentModule.description }} 记录保存在后端数据库，重启服务后仍可查询。</span></header>
      <div class="history-tools">
        <label>记录类型<select v-model="historyTypeFilter"><option value="ALL">全部</option><option value="RETURN">返航请求</option><option value="ALERT">告警确认</option></select></label>
        <label>处理结果<select v-model="historyResultFilter"><option value="ALL">全部</option><option value="成功">成功</option><option value="失败">失败</option></select></label>
        <button type="button" :disabled="historyLoading" @click="loadActivityHistory">{{ historyLoading ? '正在刷新…' : '刷新记录' }}</button>
      </div>
      <p v-if="historyError" class="history-message error" aria-live="polite">{{ historyError }}</p>
      <p v-else-if="historyLoading" class="history-message" aria-live="polite">正在读取操作历史…</p>
      <div v-else class="history-layout">
        <section class="history-list">
          <p v-if="!activityHistory.length" class="history-empty">当前筛选条件下没有记录。</p>
          <button v-for="record in activityHistory" :key="record.key" type="button" :class="{ selected: record.key === selectedActivity?.key }" @click="selectedActivityKey = record.key">
            <span :class="['history-type', record.type.toLowerCase()]">{{ record.type === 'RETURN' ? '返航' : '告警' }}</span>
            <div><strong>{{ record.title }}</strong><small>{{ record.device }} · {{ record.timestamp }}</small></div>
            <b :class="{ failed: record.result === '失败' }">{{ record.result }}</b>
          </button>
        </section>
        <aside v-if="selectedActivity" class="history-detail">
          <div class="history-detail-heading"><div><p class="section-label">记录详情</p><h2>{{ selectedActivity.title }}</h2></div><span :class="{ failed: selectedActivity.result === '失败' }">{{ selectedActivity.result }}</span></div>
          <dl><div><dt>记录类型</dt><dd>{{ selectedActivity.type === 'RETURN' ? '返航请求' : '告警确认' }}</dd></div><div><dt>操作时间</dt><dd>{{ selectedActivity.timestamp }}</dd></div><div><dt>操作人员</dt><dd>{{ selectedActivity.operator }}</dd></div><div><dt>关联设备</dt><dd>{{ selectedActivity.device }}</dd></div><div class="history-detail-full"><dt>{{ selectedActivity.type === 'RETURN' ? '安全检查结果' : '告警与处理结果' }}</dt><dd>{{ selectedActivity.detail }}</dd></div></dl>
        </aside>
        <aside v-else class="history-detail history-empty-detail"><p>请选择一条记录查看详情。</p></aside>
      </div>
    </section>

    <section v-else-if="activeNavigation === '系统管理'" class="module-page">
      <header class="module-intro"><p>SYSTEM STATUS</p><h1>系统，边界清楚。</h1><span>{{ currentModule.description }}</span></header>
      <p v-if="systemReadinessError" class="history-message error" aria-live="polite">{{ systemReadinessError }}</p>
      <div class="system-overview">
        <article><span :class="['system-dot', dataHealth.state]"></span><p class="section-label">REALTIME DATA</p><h2>{{ connectionState }}</h2><p>{{ dataHealth.message }}</p><dl><div><dt>数据来源</dt><dd>{{ dataSourceLabel }}</dd></div><div><dt>最近更新</dt><dd>{{ lastUpdatedLabel || '等待数据' }}</dd></div></dl></article>
       <article><span :class="['system-dot', { ready: systemReadiness?.configured }]"></span><p class="section-label">DJI CLOUD API</p><h2>{{ systemReadinessLoading ? '正在检查…' : systemReadiness?.configured ? '配置已就绪' : '真实接入未启用' }}</h2><p>{{ systemReadiness?.message || '默认继续使用本机模拟器，不会连接真实设备。' }}</p><dl><div><dt>连接超时</dt><dd>{{ systemReadiness?.connectTimeoutMs || 0 }} ms</dd></div><div><dt>失败重试</dt><dd>{{ systemReadiness?.maxRetries || 0 }} 次</dd></div></dl><button type="button" :disabled="systemReadinessLoading" @click="loadSystemReadiness">重新检查</button></article>
        <article><span :class="['system-dot', { ready: systemReadiness?.mqtt?.enabled }]"></span><p class="section-label">DJI MQTT</p><h2>{{ systemReadiness?.mqtt?.enabled ? (systemReadiness?.mqtt?.embeddedBroker ? '内嵌 Broker 已启用' : 'MQTT 已连接') : 'MQTT 未启用' }}</h2><p>{{ systemReadiness?.mqtt?.enabled ? (systemReadiness?.mqtt?.embeddedBroker ? '本机内嵌 Moquette Broker，Dock 3 经局域网直连本机 IP 即可接入。' : '连接至外部 Broker: ' + (systemReadiness?.mqtt?.brokerUrl || '未配置')) : '未启用 MQTT 连接，设备遥测和握手应答不会建立。' }}</p><dl><div><dt>Broker 地址</dt><dd>{{ systemReadiness?.mqtt?.brokerUrl || '未配置' }}</dd></div><div><dt>握手应答</dt><dd>{{ systemReadiness?.mqtt?.handshakeEnabled ? '已启用' : '已关闭' }}</dd></div><div><dt>内嵌 Broker</dt><dd>{{ systemReadiness?.mqtt?.embeddedBroker ? '端口 ' + systemReadiness?.mqtt?.brokerPort : '未启用' }}</dd></div><div><dt>网关 SN</dt><dd>{{ systemReadiness?.mqtt?.gatewaySn || '未配置' }}</dd></div></dl><button type="button" :disabled="systemReadinessLoading" @click="loadSystemReadiness">重新检查</button></article>
       <article><span class="system-dot ready"></span><p class="section-label">ACCESS CONTROL</p><h2>演示权限已启用</h2><p>当前身份为 {{ identityLabel }}。真实部署前仍需接入正式登录与授权系统。</p><dl><div><dt>控制权限</dt><dd>{{ canControl ? '允许演示操作' : '当前不可操作' }}</dd></div><div><dt>操作留痕</dt><dd>数据库持久化</dd></div></dl></article>
      </div>
      <section class="system-boundary"><div><p class="section-label">CURRENT BOUNDARY</p><h2>真实设备接入前的安全边界</h2></div><ul><li>真实凭证只保存在服务器环境变量中</li><li>未完成测试设备验收前不发送真实控制</li><li>网页只读取统一格式，不直接接触厂商密钥</li></ul></section>
    </section>

        <section v-else-if="activeNavigation === '航线规划'" class="module-page route-planner">
          <header class="module-intro">
            <p>FLIGHT ROUTE PLANNER</p>
            <h1>航线，逐点规划。</h1>
            <span>{{ currentModule.description }} 规划完成的航线可在任务管理中直接关联执行。</span>
          </header>

          <div class="route-safety-notice">
            <span>航线由后端统一保存与管理</span>
            <small>规划完成后可在任务管理中关联下发，当前阶段不会自动发送飞行或设备控制指令。</small>
          </div>

          <div class="route-toolbar">
            <div class="route-actions">
              <button type="button" :disabled="routesLoading" @click="loadFlightRoutes">{{ routesLoading ? '正在刷新…' : '刷新列表' }}</button>
              <button type="button" class="primary" @click="createNewRoute">+ 新建航线</button>
            </div>
            <div class="route-summary">
              <span>航线总数 <strong>{{ flightRoutes.length }}</strong></span>
              <span v-if="selectedRoute">航点 <strong>{{ waypoints.length }}</strong></span>
              <span v-if="selectedRoute || waypoints.length">总距离 <strong>{{ routeTotalDistance }} m</strong></span>
              <span v-if="selectedRoute || waypoints.length">预计时长 <strong>{{ formatDuration(routeEstimatedDuration) }}</strong></span>
            </div>
          </div>

          <p v-if="routesLoadError" class="history-message error" aria-live="polite">{{ routesLoadError }}</p>
          <p v-if="routeSaveMessage" :class="['history-message', routeSaveMessageType]" aria-live="polite">{{ routeSaveMessage }}</p>

          <div class="route-layout">
            <!-- 左侧：航线列表 -->
            <aside class="route-sidebar">
              <div class="section-heading">
                <div><p class="section-label">ROUTE LIST</p><h2>航线列表</h2></div>
              </div>
              <div class="route-list">
                <button
                  v-for="route in flightRoutes"
                  :key="route.id"
                  type="button"
                  :class="{ chosen: route.id === selectedRouteId }"
                  @click="selectRoute(route.id)"
                >
                  <span :class="['route-mode-dot', route.mode]"></span>
                  <div>
                    <strong>{{ route.name }}</strong>
                    <small>{{ route.area }} · {{ route.waypointCount }} 航点 · 最近修改 {{ route.modifiedAt?.slice(0, 16)?.replace('T', ' ') || '—' }}</small>
                  </div>
                  <em>{{ route.usedInTasks > 0 ? `${route.usedInTasks} 个任务` : '未使用' }}</em>
                </button>
                <p v-if="!flightRoutes.length && !routesLoading" class="history-empty">暂无航线，点击"新建航线"开始规划。</p>
              </div>
            </aside>

            <!-- 中间：地图与航线可视化 -->
            <section class="route-map-panel">
              <div class="route-map-toolbar">
                <p class="section-label">{{ selectedRouteId ? '编辑中航线' : '草稿模式' }} · MAP VIEW</p>
                <div>
                  <button type="button" :disabled="!canControl" @click="addWaypoint">+ 添加航点</button>
                </div>
              </div>
              <div class="route-map-visual" aria-label="航线地图可视化">
                <div class="map-grid"></div>
                <div class="map-terrain"></div>
                <span class="place place-a">北门</span>
                <span class="place place-b">东侧围栏</span>
                <span class="place place-c">仓库区</span>
                <span class="place place-d">光伏屋顶</span>
                <span class="map-compass">N</span>
                <svg v-if="waypoints.length >= 2" class="route-polyline" viewBox="0 0 100 100" preserveAspectRatio="none">
                  <polyline
                    :points="waypoints.map((_, i) => {
                      const x = 10 + (i * 80 / Math.max(1, waypoints.length - 1));
                      const y = waypoints.length > 1 ? 20 + ((waypoints[i].altitude || 60) - 40) * 0.5 : 50;
                      return `${x},${Math.max(10, Math.min(90, y))}`;
                    }).join(' ')"
                    fill="none"
                    stroke="#3b82f6"
                    stroke-width="0.6"
                    stroke-dasharray="1.5,1"
                  />
                </svg>
                <svg v-if="waypoints.length >= 2" class="route-polyline" viewBox="0 0 100 100" preserveAspectRatio="none">
                  <polyline
                    :points="waypoints.map((_, i) => {
                      const x = 10 + (i * 80 / Math.max(1, waypoints.length - 1));
                      const y = waypoints.length > 1 ? 20 + ((waypoints[i].altitude || 60) - 40) * 0.5 : 50;
                      return `${x},${Math.max(10, Math.min(90, y))}`;
                    }).join(' ')"
                    fill="none"
                    stroke="#3b82f6"
                    stroke-width="0.3"
                    opacity="0.3"
                  />
                </svg>
                <div
                  v-for="(wp, idx) in waypoints"
                  :key="wp.code"
                  :class="['waypoint-marker', { selected: idx === selectedWaypointIndex, first: idx === 0, last: idx === waypoints.length - 1 }]"
                  :style="{ left: `${10 + (idx * 80 / Math.max(1, waypoints.length - 1))}%`, top: `${Math.max(10, Math.min(85, 20 + ((wp.altitude || 60) - 40) * 0.5))}%` }"
                  @click="selectedWaypointIndex = idx"
                >
                  <b>{{ idx + 1 }}</b>
                  <small>{{ wp.label }}</small>
                  <em v-if="wp.altitude" class="wp-altitude-tag">{{ wp.altitude }}m</em>
                </div>
                <div v-if="!waypoints.length" class="map-empty-hint">
                  <span>点击上方"添加航点"开始规划航线</span>
                </div>
                <div class="map-caption">
                  <span><i></i>规划航线（示意）</span>
                  <b>航点数：{{ waypoints.length }}</b>
                </div>
              </div>

              <!-- 航线基本信息 -->
              <div class="route-info-card">
                <div class="route-info-grid">
                  <label>航线名称
                    <input v-model="routeForm.name" maxlength="80" placeholder="例如：北侧围栏例行巡检" />
                  </label>
                  <label>所属区域
                    <select v-model="routeForm.area">
                      <option v-for="area in routeAreas" :key="area" :value="area">{{ area }}</option>
                    </select>
                  </label>
                  <label>飞行模式
                    <select v-model="routeForm.mode">
                      <option v-for="mode in flightModes" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
                    </select>
                  </label>
                  <label class="checkbox" v-if="selectedRoute && selectedRoute.usedInTasks > 0">
                    <input type="checkbox" v-model="allowUsedRouteModification" />
                    <span>该航线已被 {{ selectedRoute.usedInTasks }} 个任务使用，确认允许修改</span>
                  </label>
                </div>
              </div>
            </section>

            <!-- 右侧：航点列表与编辑 -->
            <aside class="waypoint-panel">
              <div class="section-heading">
                <div><p class="section-label">WAYPOINTS</p><h2>航点编辑</h2></div>
                <button type="button" :disabled="!canControl || waypoints.length === 0" @click="addWaypoint">+ 新增</button>
              </div>

              <div class="waypoint-list">
                <div
                  v-for="(wp, idx) in waypoints"
                  :key="wp.code + '-' + idx"
                  :class="['waypoint-item', { selected: idx === selectedWaypointIndex }]"
                  @click="selectedWaypointIndex = idx"
                >
                  <header>
                    <span :class="['waypoint-index', { first: idx === 0, last: idx === waypoints.length - 1 }]">{{ idx + 1 }}</span>
                    <strong>{{ wp.label }}</strong>
                    <em>{{ waypointActionLabel(wp.action) }}</em>
                    <div class="waypoint-item-actions">
                      <button type="button" :disabled="idx === 0" @click.stop="moveWaypoint(idx, -1)" title="上移">↑</button>
                      <button type="button" :disabled="idx === waypoints.length - 1" @click.stop="moveWaypoint(idx, 1)" title="下移">↓</button>
                      <button type="button" @click.stop="copyWaypoint(idx)" title="复制">⎘</button>
                      <button type="button" class="danger" :disabled="!canControl" @click.stop="removeWaypoint(idx)" title="删除">×</button>
                    </div>
                  </header>
                  <div v-if="idx === selectedWaypointIndex" class="waypoint-editor">
                    <label>航点编号<input v-model="wp.code" maxlength="10" /></label>
                    <label>航点名称<input v-model="wp.label" maxlength="30" /></label>
                    <div class="form-two-columns">
                      <label>纬度<input v-model.number="wp.lat" type="number" step="0.000001" /></label>
                      <label>经度<input v-model.number="wp.lng" type="number" step="0.000001" /></label>
                    </div>
                    <div class="form-two-columns">
                      <label>高度 (m)<input v-model.number="wp.altitude" type="number" min="20" max="120" step="5" /></label>
                      <label>速度 (m/s)<input v-model.number="wp.speed" type="number" min="2" max="15" step="1" /></label>
                    </div>
                    <div class="form-two-columns">
                      <label>动作
                        <select v-model="wp.action">
                          <option v-for="act in actionOptions" :key="act.value" :value="act.value">{{ act.label }}</option>
                        </select>
                      </label>
                      <label>载荷
                        <select v-model="wp.payload">
                          <option v-for="p in payloadOptions" :key="p" :value="p">{{ p }}</option>
                        </select>
                      </label>
                    </div>
                    <label>动作参数<input v-model="wp.actionParam" maxlength="50" placeholder="例如：单次拍摄、4K录制、间隔2秒连拍" /></label>
                  </div>
                </div>
                <p v-if="!waypoints.length" class="history-empty">暂无航点，点击"+添加航点"开始。至少需要 3 个航点。</p>
              </div>

              <!-- 校验与保存 -->
              <div class="route-actions-panel">
                <button type="button" :disabled="routeValidating || waypoints.length === 0" @click="validateCurrentRoute">
                  {{ routeValidating ? '校验中…' : '校验航线' }}
                </button>
                <div class="validate-report" v-if="routeValidateReport">
                  <p :class="['validate-summary', routeValidateReport.valid ? 'ok' : 'bad']">
                    {{ routeValidateReport.valid ? '✅ 校验通过' : '❌ 校验不通过' }}
                    <span>共 {{ routeValidateReport.waypointCount }} 航点 · 距离 {{ routeValidateReport.totalDistanceMeters }} m · 预计 {{ formatDuration(routeValidateReport.estimatedDurationSeconds) }}</span>
                  </p>
                  <ul v-if="routeValidateReport.errors?.length" class="validate-errors">
                    <li v-for="(e, i) in routeValidateReport.errors" :key="'e'+i">⚠ {{ e }}</li>
                  </ul>
                  <ul v-if="routeValidateReport.warnings?.length" class="validate-warnings">
                    <li v-for="(w, i) in routeValidateReport.warnings" :key="'w'+i">ℹ {{ w }}</li>
                  </ul>
                </div>
                <div class="route-save-actions">
                  <button
                    v-if="selectedRoute && canControl"
                    type="button"
                    class="danger"
                    :disabled="selectedRoute.usedInTasks > 0"
                    :title="selectedRoute.usedInTasks > 0 ? '被任务使用中，不能删除' : '删除此航线'"
                    @click="openDeleteConfirm(selectedRoute.id)"
                  >删除航线</button>
                  <button
                    type="button"
                    class="primary"
                    :disabled="routeSubmitting || !canControl"
                    @click="saveCurrentRoute"
                  >{{ routeSubmitting ? '保存中…' : selectedRouteId ? '保存修改' : '创建航线' }}</button>
                </div>
                <p v-if="!canControl" class="permission-hint">当前身份：{{ identityLabel }}。请切换为飞行操作员或管理员后编辑航线。</p>
              </div>
            </aside>
          </div>
        </section>

        <section v-else class="module-page empty-state"><p>DEFERRED</p><h1>{{ currentModule.name }}</h1><span>{{ currentModule.description }} 先完成其他前端页面后再继续。</span></section>
      </div>
    </section>
  </main>

  <div v-if="taskDialogOpen" class="dialog-backdrop" @click.self="closeTaskDialog">
    <section class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="task-dialog-title">
      <div class="dialog-heading"><div><p class="section-label">{{ editingTaskId ? 'EDIT MISSION' : 'NEW MISSION' }}</p><h2 id="task-dialog-title">{{ editingTaskId ? '编辑任务' : '新建任务' }}</h2></div><button type="button" aria-label="关闭" @click="closeTaskDialog">×</button></div>
      <p class="dialog-intro">建立任务后会由后端保存。当前只保存任务资料，不会下发航线、飞行或设备控制指令。</p>
      <form class="task-form" @submit.prevent="createTask">
        <label>任务名称<input v-model="taskForm.name" maxlength="40" placeholder="例如：北侧围栏例行巡检" /></label>
        <div class="form-two-columns"><label>执行设备<select v-model="taskForm.device"><option v-for="device in devices.filter((item) => item.status !== '离线')" :key="device.name" :value="device.name">{{ device.name }}</option></select></label><label>执行频率<select v-model="taskForm.frequency"><option>一次性</option><option>每日重复</option><option>每周重复</option></select></label></div>
        <label>计划执行时间<input v-model="taskForm.scheduledAt" type="datetime-local" /></label>
        <label>关联路线
          <select v-model="taskForm.route">
            <option value="待规划路线（航线规划暂缓）">待选择路线</option>
            <option v-for="routeName in routeOptions" :key="routeName" :value="routeName">{{ routeName }}</option>
          </select>
        </label>
        <p v-if="taskFormMessage" class="form-message error" aria-live="polite">{{ taskFormMessage }}</p>
        <div class="dialog-actions"><button type="button" :disabled="taskFormSubmitting" @click="closeTaskDialog">取消</button><button type="submit" :disabled="taskFormSubmitting">{{ taskFormSubmitting ? '正在保存…' : editingTaskId ? '保存修改' : '保存任务' }}</button></div>
      </form>
    </section>
  </div>

  <div v-if="taskDetailOpen" class="dialog-backdrop" @click.self="taskDetailOpen = false">
    <section class="detail-dialog" role="dialog" aria-modal="true" aria-labelledby="task-detail-title">
      <div class="dialog-heading"><div><p class="section-label">MISSION DETAIL</p><h2 id="task-detail-title">{{ selectedTask.name }}</h2></div><button type="button" aria-label="关闭" @click="taskDetailOpen = false">×</button></div>
      <dl><div><dt>任务状态</dt><dd>{{ selectedTask.status }}</dd></div><div><dt>执行设备</dt><dd>{{ selectedTask.device }}</dd></div><div><dt>计划时间</dt><dd>{{ selectedTask.time }}</dd></div><div><dt>负责人</dt><dd>{{ selectedTask.operator }}</dd></div><div><dt>关联路线</dt><dd>{{ selectedTask.route }}</dd></div><div><dt>完成进度</dt><dd>{{ selectedTask.progress }}%</dd></div></dl>
      <p class="dialog-note">任务资料已由后端保存。调度、航线和执行记录仍按当前安排暂缓，不会自动下发到设备。</p>
      <div class="dialog-actions"><button v-if="selectedTask.status === '待执行' && canControl" type="button" @click="openTaskEditor">编辑任务</button><button type="button" @click="taskDetailOpen = false">关闭</button></div>
    </section>
  </div>

  <div v-if="resultPreviewOpen && selectedResult" class="dialog-backdrop" @click.self="resultPreviewOpen = false">
    <section class="preview-dialog" role="dialog" aria-modal="true" aria-labelledby="result-preview-title">
      <div class="dialog-heading"><div><p class="section-label">{{ selectedResult.type === 'VIDEO' ? 'VIDEO PREVIEW' : 'IMAGE PREVIEW' }}</p><h2 id="result-preview-title">{{ selectedResult.title }}</h2></div><button type="button" aria-label="关闭" @click="resultPreviewOpen = false">×</button></div>
      <div :class="['preview-canvas', selectedResult.tone]"><span>{{ selectedResult.type === 'VIDEO' ? '等待接入真实视频流' : '等待接入真实照片文件' }}</span><strong>{{ selectedResult.status }}</strong></div>
      <dl><div><dt>关联任务</dt><dd>{{ selectedResult.task }}</dd></div><div><dt>采集位置</dt><dd>{{ selectedResult.location }}</dd></div><div><dt>采集设备</dt><dd>{{ selectedResult.device }}</dd></div><div><dt>采集时间</dt><dd>{{ selectedResult.capturedAt }}</dd></div></dl>
      <p class="dialog-note">这是页面预览流程演示；真实媒体文件接入后，这里会替换为照片大图或视频播放器。</p>
    </section>
  </div>

  <div v-if="returnConfirmationOpen" class="confirmation-backdrop" @click.self="cancelReturnConfirmation">
    <section class="return-confirmation" role="dialog" aria-modal="true" aria-labelledby="return-confirmation-title">
      <p class="section-label">CONTROL CONFIRMATION</p>
      <h2 id="return-confirmation-title">确认发起返航？</h2>
      <p>返航会中止当前巡检任务。请核对操作对象与安全条件后再继续。</p>
      <dl>
        <div><dt>操作人</dt><dd>{{ identityLabel }}</dd></div>
        <div><dt>操作设备</dt><dd>巡检无人机 02</dd></div>
        <div><dt>当前进度</dt><dd>{{ liveStatus.progress }}%</dd></div>
        <div><dt>剩余电量</dt><dd>{{ liveStatus.battery }}%</dd></div>
      </dl>
      <p class="confirmation-safety">{{ returnScenarioDescription }}</p>
      <div class="confirmation-actions"><button type="button" @click="cancelReturnConfirmation">取消</button><button type="button" :disabled="returnSubmitting" @click="submitReturn">{{ returnSubmitting ? '正在安全检查…' : '确认发起返航' }}</button></div>
    </section>
  </div>

  <div v-if="deleteConfirmOpen" class="confirmation-backdrop" @click.self="cancelDelete">
    <section class="return-confirmation" role="dialog" aria-modal="true" aria-labelledby="delete-confirmation-title">
      <p class="section-label">DELETE CONFIRMATION</p>
      <h2 id="delete-confirmation-title">确认删除此航线？</h2>
      <p>删除后无法恢复，请确认该航线不再需要。</p>
      <dl v-if="selectedRoute">
        <div><dt>航线名称</dt><dd>{{ selectedRoute.name }}</dd></div>
        <div><dt>所属区域</dt><dd>{{ selectedRoute.area }}</dd></div>
        <div><dt>航点数量</dt><dd>{{ selectedRoute.waypointCount }} 个</dd></div>
        <div><dt>关联任务</dt><dd>{{ selectedRoute.usedInTasks }} 个</dd></div>
      </dl>
      <div class="confirmation-actions"><button type="button" @click="cancelDelete">取消</button><button type="button" class="danger" @click="confirmDeleteRoute">确认删除</button></div>
    </section>
  </div>
</template>
