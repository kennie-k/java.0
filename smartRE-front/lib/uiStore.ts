import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const SIDEBAR_WIDTH = 260
export const SIDEBAR_WIDTH_COLLAPSED = 76

interface UIStore {
  collapsed: boolean
  toggleCollapsed: () => void
  setCollapsed: (v: boolean) => void
}

export const useUIStore = create<UIStore>()(
  persist(
    set => ({
      collapsed: false,
      toggleCollapsed: () => set(s => ({ collapsed: !s.collapsed })),
      setCollapsed: v => set({ collapsed: v }),
    }),
    { name: 'sre_ui' }
  )
)
