import http from 'node:http'
import { pathToFileURL } from 'node:url'

const DEFAULT_LATENCY_BUCKETS = [
  0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60,
]
const DEFAULT_CONNECTION_BUCKETS = [1, 5, 10, 30, 60, 120, 300, 600, 1800, 3600]
const CORRELATION_TTL_MS = 5 * 60 * 1000

function counter(map, key, amount = 1) {
  map.set(key, (map.get(key) || 0) + amount)
}

function labels(values) {
  const pairs = Object.entries(values).map(([key, value]) =>
    `${key}="${String(value).replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll('\n', '\\n')}"`)
  return pairs.length === 0 ? '' : `{${pairs.join(',')}}`
}

class Histogram {
  constructor(buckets) {
    this.buckets = buckets
    this.bucketCounts = buckets.map(() => 0)
    this.count = 0
    this.sum = 0
  }

  observe(value) {
    if (!Number.isFinite(value) || value < 0) {
      return false
    }

    this.count += 1
    this.sum += value
    this.buckets.forEach((bucket, index) => {
      if (value <= bucket) {
        this.bucketCounts[index] += 1
      }
    })
    return true
  }
}

export function createSseParser(onEvent) {
  let buffer = ''

  return {
    push(chunk) {
      buffer += chunk

      while (true) {
        const delimiter = /\r\n\r\n|\n\n|\r\r/.exec(buffer)
        if (delimiter === null) {
          return
        }

        const frame = buffer.slice(0, delimiter.index)
        buffer = buffer.slice(delimiter.index + delimiter[0].length)
        const parsed = parseFrame(frame)
        if (parsed !== null) {
          onEvent(parsed)
        }
      }
    },
  }
}

function parseFrame(frame) {
  let id = ''
  let name = 'message'
  const data = []

  for (const line of frame.split(/\r\n|\n|\r/)) {
    if (line === '' || line.startsWith(':')) {
      continue
    }

    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'id') {
      id = value
    } else if (field === 'event') {
      name = value
    } else if (field === 'data') {
      data.push(value)
    }
  }

  if (id === '' && data.length === 0) {
    return null
  }

  return { id, name, data: data.join('\n') }
}

export class SseClientMetrics {
  constructor(runId, targetConnections) {
    this.runId = runId
    this.targetConnections = targetConnections
    this.activeConnections = 0
    this.connectionsOpened = 0
    this.connectionsClosed = new Map()
    this.connectionErrors = new Map()
    this.reconnects = 0
    this.unexpectedCloses = 0
    this.eventsReceived = new Map()
    this.parseErrors = 0
    this.missingEvents = 0
    this.duplicateEvents = 0
    this.outOfOrderEvents = 0
    this.correlationsReceived = 0
    this.invalidLatencies = 0
    this.messageLatency = new Histogram(DEFAULT_LATENCY_BUCKETS)
    this.connectionDuration = new Histogram(DEFAULT_CONNECTION_BUCKETS)
    this.correlations = new Map()
    this.pendingBidArrivals = new Map()
  }

  connectionOpened(listener) {
    if (listener.everOpened) {
      this.reconnects += 1
    }
    listener.everOpened = true
    listener.openedAt = Date.now()
    this.connectionsOpened += 1
    this.activeConnections += 1
  }

  connectionClosed(listener, reason, unexpected = true) {
    if (listener.openedAt === null) {
      return
    }

    this.activeConnections = Math.max(0, this.activeConnections - 1)
    counter(this.connectionsClosed, reason)
    if (unexpected) {
      this.unexpectedCloses += 1
    }
    this.connectionDuration.observe((Date.now() - listener.openedAt) / 1000)
    listener.openedAt = null
  }

  connectionError(cause) {
    counter(this.connectionErrors, cause)
  }

  receive(listener, event, receivedAtMs = Date.now()) {
    counter(this.eventsReceived, event.name)

    // 참여자 수 이벤트처럼 id: 필드 없이 나가는 이벤트는 event.id가 ''다. Number('')는
    // 0이라 이 검사를 건너뛰지 않으면, 진행 중이던 순차 ID(예: 7)보다 작은 값(0)으로
    // 오인돼 out-of-order로 잘못 집계된다 — id가 실제로 있을 때만 순서를 검사한다.
    const id = Number(event.id)
    let uniqueInOrder = true
    if (event.id !== '' && Number.isSafeInteger(id)) {
      if (listener.lastEventId !== null) {
        if (id === listener.lastEventId) {
          this.duplicateEvents += 1
          uniqueInOrder = false
        } else if (id < listener.lastEventId) {
          this.outOfOrderEvents += 1
          uniqueInOrder = false
        } else if (id > listener.lastEventId + 1) {
          this.missingEvents += id - listener.lastEventId - 1
        }
      }

      if (listener.lastEventId === null || id > listener.lastEventId) {
        listener.lastEventId = id
      }
    }

    if (event.name !== 'BID_PLACED' || !uniqueInOrder) {
      return
    }

    try {
      const data = JSON.parse(event.data)
      if (!Number.isSafeInteger(Number(data.itemId)) || !Number.isFinite(Number(data.bidPrice))) {
        this.parseErrors += 1
        return
      }
      this.recordBidArrival(`${data.itemId}:${data.bidPrice}`, receivedAtMs)
    } catch {
      this.parseErrors += 1
    }
  }

  recordCorrelation(itemId, amount, sentAtMs, nowMs = Date.now()) {
    if (!Number.isSafeInteger(Number(itemId)) || !Number.isFinite(Number(amount)) ||
        !Number.isFinite(Number(sentAtMs))) {
      return false
    }

    const key = `${itemId}:${amount}`
    const correlation = { sentAtMs: Number(sentAtMs), expiresAt: nowMs + CORRELATION_TTL_MS }
    this.correlations.set(key, correlation)
    this.correlationsReceived += 1

    const pending = this.pendingBidArrivals.get(key) || []
    pending.forEach((receivedAtMs) => this.observeLatency(correlation.sentAtMs, receivedAtMs))
    this.pendingBidArrivals.delete(key)
    return true
  }

  recordBidArrival(key, receivedAtMs) {
    const correlation = this.correlations.get(key)
    if (correlation !== undefined) {
      this.observeLatency(correlation.sentAtMs, receivedAtMs)
      return
    }

    const pending = this.pendingBidArrivals.get(key) || []
    pending.push(receivedAtMs)
    this.pendingBidArrivals.set(key, pending)
  }

  observeLatency(sentAtMs, receivedAtMs) {
    if (!this.messageLatency.observe((receivedAtMs - sentAtMs) / 1000)) {
      this.invalidLatencies += 1
    }
  }

  evictExpired(nowMs = Date.now()) {
    for (const [key, value] of this.correlations) {
      if (value.expiresAt < nowMs) {
        this.correlations.delete(key)
      }
    }

    // correlation이 오지 않은 이벤트도 무한히 들고 있지 않는다. 이벤트 수신 시각 자체만
    // 저장하므로 가장 최근 값이 TTL보다 오래됐으면 그 key 전체를 버려도 된다.
    for (const [key, arrivals] of this.pendingBidArrivals) {
      if (arrivals.length === 0 || arrivals.at(-1) + CORRELATION_TTL_MS < nowMs) {
        this.pendingBidArrivals.delete(key)
      }
    }
  }

  prometheus() {
    const run = { run: this.runId }
    const lines = [
      '# HELP sse_client_connections 실제 SSE 프레임을 읽고 있는 연결 수',
      '# TYPE sse_client_connections gauge',
      `sse_client_connections${labels(run)} ${this.activeConnections}`,
      '# HELP sse_client_target_connections 이번 실행의 목표 SSE 연결 수',
      '# TYPE sse_client_target_connections gauge',
      `sse_client_target_connections${labels(run)} ${this.targetConnections}`,
      '# HELP sse_client_connections_opened_total 수립된 SSE 연결 수',
      '# TYPE sse_client_connections_opened_total counter',
      `sse_client_connections_opened_total${labels(run)} ${this.connectionsOpened}`,
      '# HELP sse_client_reconnects_total 최초 연결 이후 재연결 수',
      '# TYPE sse_client_reconnects_total counter',
      `sse_client_reconnects_total${labels(run)} ${this.reconnects}`,
      '# HELP sse_client_unexpected_closes_total 클라이언트 종료 외의 연결 종료 수',
      '# TYPE sse_client_unexpected_closes_total counter',
      `sse_client_unexpected_closes_total${labels(run)} ${this.unexpectedCloses}`,
      '# HELP sse_client_parse_errors_total SSE BID_PLACED payload 파싱 실패 수',
      '# TYPE sse_client_parse_errors_total counter',
      `sse_client_parse_errors_total${labels(run)} ${this.parseErrors}`,
      '# HELP sse_client_missing_events_total 방별 SSE ID 사이에서 빠진 이벤트 수',
      '# TYPE sse_client_missing_events_total counter',
      `sse_client_missing_events_total${labels(run)} ${this.missingEvents}`,
      '# HELP sse_client_duplicate_events_total 같은 연결에서 같은 SSE ID를 다시 받은 수',
      '# TYPE sse_client_duplicate_events_total counter',
      `sse_client_duplicate_events_total${labels(run)} ${this.duplicateEvents}`,
      '# HELP sse_client_out_of_order_events_total 이전보다 작은 SSE ID를 받은 수',
      '# TYPE sse_client_out_of_order_events_total counter',
      `sse_client_out_of_order_events_total${labels(run)} ${this.outOfOrderEvents}`,
      '# HELP sse_client_correlations_received_total 입찰 k6에서 받은 요청 시작 시각 수',
      '# TYPE sse_client_correlations_received_total counter',
      `sse_client_correlations_received_total${labels(run)} ${this.correlationsReceived}`,
      '# HELP sse_client_invalid_latencies_total 음수 등 유효하지 않은 E2E 지연 표본 수',
      '# TYPE sse_client_invalid_latencies_total counter',
      `sse_client_invalid_latencies_total${labels(run)} ${this.invalidLatencies}`,
      '# HELP sse_client_latency_pending_events 아직 입찰 요청 시작 시각과 매칭되지 않은 수신 이벤트 수',
      '# TYPE sse_client_latency_pending_events gauge',
      `sse_client_latency_pending_events${labels(run)} ${this.pendingArrivalCount()}`,
    ]

    for (const [reason, value] of this.connectionsClosed) {
      lines.push(`sse_client_connections_closed_total${labels({ ...run, reason })} ${value}`)
    }
    if (this.connectionsClosed.size === 0) {
      lines.push(`sse_client_connections_closed_total${labels({ ...run, reason: 'none' })} 0`)
    }

    for (const [cause, value] of this.connectionErrors) {
      lines.push(`sse_client_connection_errors_total${labels({ ...run, cause })} ${value}`)
    }
    if (this.connectionErrors.size === 0) {
      lines.push(`sse_client_connection_errors_total${labels({ ...run, cause: 'none' })} 0`)
    }

    for (const [event, value] of this.eventsReceived) {
      lines.push(`sse_client_events_received_total${labels({ ...run, event })} ${value}`)
    }
    if (this.eventsReceived.size === 0) {
      lines.push(`sse_client_events_received_total${labels({ ...run, event: 'none' })} 0`)
    }

    lines.push(...renderHistogram('sse_client_msg_latency_seconds', this.messageLatency, run))
    lines.push(...renderHistogram('sse_client_connection_duration_seconds', this.connectionDuration, run))
    return `${lines.join('\n')}\n`
  }

  pendingArrivalCount() {
    let count = 0
    for (const arrivals of this.pendingBidArrivals.values()) {
      count += arrivals.length
    }
    return count
  }
}

function renderHistogram(name, histogram, baseLabels) {
  const result = []
  histogram.buckets.forEach((bucket, index) => {
    result.push(`${name}_bucket${labels({ ...baseLabels, le: bucket })} ${histogram.bucketCounts[index]}`)
  })
  result.push(`${name}_bucket${labels({ ...baseLabels, le: '+Inf' })} ${histogram.count}`)
  result.push(`${name}_sum${labels(baseLabels)} ${histogram.sum}`)
  result.push(`${name}_count${labels(baseLabels)} ${histogram.count}`)
  return result
}

function newListener(index, shareCode) {
  return {
    index,
    shareCode,
    lastEventId: null,
    everOpened: false,
    openedAt: null,
    controller: null,
  }
}

function errorCause(error) {
  if (error?.name === 'AbortError') {
    return 'aborted'
  }
  if (error instanceof TypeError) {
    return 'network'
  }
  return 'other'
}

async function runConnection(listener, config, metrics, isStopping) {
  const headers = { Accept: 'text/event-stream', 'Cache-Control': 'no-cache' }
  if (listener.lastEventId !== null) {
    headers['Last-Event-ID'] = String(listener.lastEventId)
  }

  const controller = new AbortController()
  listener.controller = controller
  const connectTimer = setTimeout(() => controller.abort(), config.connectTimeoutMs)

  try {
    const response = await fetch(
      `${config.baseUrl}/auction-rooms/share/${encodeURIComponent(listener.shareCode)}/subscribe`,
      { headers, signal: controller.signal },
    )
    clearTimeout(connectTimer)

    if (response.status !== 200 || response.body === null) {
      metrics.connectionError(`http_${Math.floor(response.status / 100)}xx`)
      return
    }

    metrics.connectionOpened(listener)
    const decoder = new TextDecoder()
    const parser = createSseParser((event) => metrics.receive(listener, event))
    const reader = response.body.getReader()

    while (!isStopping()) {
      const { value, done } = await reader.read()
      if (done) {
        metrics.connectionClosed(listener, 'eof')
        return
      }
      parser.push(decoder.decode(value, { stream: true }))
    }

    metrics.connectionClosed(listener, 'client_shutdown', false)
  } catch (error) {
    clearTimeout(connectTimer)
    if (listener.openedAt !== null) {
      metrics.connectionClosed(listener, isStopping() ? 'client_shutdown' : 'read_error', !isStopping())
    } else if (!isStopping()) {
      metrics.connectionError(errorCause(error))
    }
  } finally {
    listener.controller = null
  }
}

async function main() {
  const shareCodes = (process.env.SHARE_CODES || process.env.SHARE_CODE || '')
    .split(',').map((value) => value.trim()).filter(Boolean)
  const config = {
    baseUrl: process.env.BASE_URL || 'http://nginx/api/v1',
    runId: process.env.RUN_ID || 'standalone',
    connections: Number(process.env.CONNECTIONS || 1),
    rampUpSeconds: Number(process.env.RAMP_UP_SECONDS || 120),
    reconnectDelayMs: Number(process.env.RECONNECT_DELAY_MS || 1000),
    connectTimeoutMs: Number(process.env.CONNECT_TIMEOUT_MS || 15000),
    port: Number(process.env.METRICS_PORT || 9091),
  }

  if (shareCodes.length === 0) {
    throw new Error('SHARE_CODES 또는 SHARE_CODE가 필요합니다.')
  }
  if (!Number.isSafeInteger(config.connections) || config.connections < 1) {
    throw new Error(`CONNECTIONS는 1 이상의 정수여야 합니다: ${config.connections}`)
  }

  const metrics = new SseClientMetrics(config.runId, config.connections)
  const listeners = Array.from({ length: config.connections }, (_, index) =>
    newListener(index, shareCodes[index % shareCodes.length]))
  let stopping = false

  const server = http.createServer(async (request, response) => {
    if (request.method === 'GET' && request.url === '/metrics') {
      response.writeHead(200, { 'Content-Type': 'text/plain; version=0.0.4; charset=utf-8' })
      response.end(metrics.prometheus())
      return
    }
    if (request.method === 'GET' && request.url === '/health') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ status: 'UP', connections: metrics.activeConnections }))
      return
    }
    if (request.method === 'POST' && request.url === '/published') {
      try {
        const body = await readJson(request)
        if (!metrics.recordCorrelation(body.itemId, body.amount, body.sentAtMs)) {
          response.writeHead(400)
          response.end()
          return
        }
        response.writeHead(202)
        response.end()
      } catch {
        response.writeHead(400)
        response.end()
      }
      return
    }
    response.writeHead(404)
    response.end()
  })

  server.listen(config.port, '0.0.0.0')
  const intervalMs = config.rampUpSeconds <= 0 ? 0 : config.rampUpSeconds * 1000 / config.connections

  listeners.forEach((listener, index) => {
    setTimeout(async () => {
      while (!stopping) {
        await runConnection(listener, config, metrics, () => stopping)
        if (!stopping) {
          await new Promise((resolve) => setTimeout(resolve, config.reconnectDelayMs))
        }
      }
    }, Math.floor(index * intervalMs))
  })

  const eviction = setInterval(() => metrics.evictExpired(), 30000)
  const shutdown = () => {
    if (stopping) {
      return
    }
    stopping = true
    clearInterval(eviction)
    listeners.forEach((listener) => listener.controller?.abort())
    setTimeout(() => server.close(() => process.exit(0)), 100).unref()
    setTimeout(() => process.exit(0), 5000).unref()
  }
  process.on('SIGTERM', shutdown)
  process.on('SIGINT', shutdown)
}

async function readJson(request) {
  let body = ''
  for await (const chunk of request) {
    body += chunk
    if (body.length > 4096) {
      throw new Error('body too large')
    }
  }
  return JSON.parse(body)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message)
    process.exit(1)
  })
}
