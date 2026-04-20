export interface DistributionData {
  [key: string]: number
}

export interface DurationData {
  stateName: string
  avgHours: number
  maxHours: number
  minHours: number
}

export interface BurndownPoint {
  date: string
  remaining: number
  completed: number
  total: number
}

export interface CfdPoint {
  date: string
  [stateName: string]: string | number
}
