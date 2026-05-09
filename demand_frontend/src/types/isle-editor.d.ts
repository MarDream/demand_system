declare module '@isle-editor/vue3' {
  export const IsleEditor: any
  export const IsleEditorToolbar: any
  export const RichTextKit: any
}

declare module '@isle-editor/core' {
  export function addLocale(locale: string, messages: Record<string, any>): void
}
