import { ElMessage, type MessageOptions } from 'element-plus'

export interface ToastOptions {
  title?: string
  duration?: number
  closable?: boolean
}

function buildOptions(typeMessage: string, options?: ToastOptions): MessageOptions {
  const opts: MessageOptions = {
    message: options?.title ? `${options.title}: ${typeMessage}` : typeMessage,
    duration: options?.duration ?? 3000,
    showClose: options?.closable ?? true,
  }
  return opts
}

export function useToast() {
  const success = (message: string, options?: ToastOptions) => {
    ElMessage.success(buildOptions(message, options))
  }

  const warning = (message: string, options?: ToastOptions) => {
    ElMessage.warning(buildOptions(message, options))
  }

  const error = (message: string, options?: ToastOptions) => {
    ElMessage.error(buildOptions(message, options))
  }

  const info = (message: string, options?: ToastOptions) => {
    ElMessage.info(buildOptions(message, options))
  }

  const loading = (message: string) => {
    const instance = ElMessage.info({
      message,
      duration: 0,
      showClose: false,
    })
    return () => {
      instance.close()
    }
  }

  return {
    success,
    warning,
    error,
    info,
    loading,
  }
}
