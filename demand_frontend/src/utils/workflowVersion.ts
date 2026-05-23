const WORKFLOW_VERSION_PATTERN = /^[1-9]\d*(?:\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))?$/

export function normalizeWorkflowVersion(version?: string | number | null) {
  if (version === null || version === undefined) {
    return ''
  }
  return String(version).trim()
}

export function isWorkflowVersion(version?: string | number | null) {
  const normalized = normalizeWorkflowVersion(version)
  return normalized.length > 0 && WORKFLOW_VERSION_PATTERN.test(normalized)
}

export function sameWorkflowVersion(left?: string | number | null, right?: string | number | null) {
  const normalizedLeft = normalizeWorkflowVersion(left)
  const normalizedRight = normalizeWorkflowVersion(right)
  if (!normalizedLeft || !normalizedRight) {
    return normalizedLeft === normalizedRight
  }
  if (isWorkflowVersion(normalizedLeft) && isWorkflowVersion(normalizedRight)) {
    return compareWorkflowVersion(normalizedLeft, normalizedRight) === 0
  }
  return normalizedLeft === normalizedRight
}

export function compareWorkflowVersion(left?: string | number | null, right?: string | number | null) {
  const leftSegments = parseWorkflowVersionSegments(left)
  const rightSegments = parseWorkflowVersionSegments(right)
  for (let index = 0; index < leftSegments.length; index++) {
    if (leftSegments[index] !== rightSegments[index]) {
      return leftSegments[index] - rightSegments[index]
    }
  }
  return 0
}

export function suggestNextWorkflowVersion(latestVersion?: string | number | null) {
  const normalized = normalizeWorkflowVersion(latestVersion)
  if (!isWorkflowVersion(normalized)) {
    return '1.0.0'
  }

  const parts = normalized.split('.')
  if (parts.length === 1) {
    return String(Number.parseInt(parts[0], 10) + 1)
  }

  const [major, minor, patch] = parseWorkflowVersionSegments(normalized)
  return `${major}.${minor}.${patch + 1}`
}

export function formatWorkflowVersionLabel(version?: string | number | null) {
  const normalized = normalizeWorkflowVersion(version)
  return normalized ? `V${normalized}` : '-'
}

function parseWorkflowVersionSegments(version?: string | number | null) {
  const normalized = normalizeWorkflowVersion(version)
  if (!isWorkflowVersion(normalized)) {
    return [0, 0, 0]
  }

  const parts = normalized.split('.')
  if (parts.length === 1) {
    return [Number.parseInt(parts[0], 10), 0, 0]
  }

  return [
    Number.parseInt(parts[0], 10),
    Number.parseInt(parts[1], 10),
    Number.parseInt(parts[2], 10),
  ]
}
