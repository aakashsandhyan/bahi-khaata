import * as React from 'react'

// The shell's phone breakpoint. 760 matches the app's existing width check in App.tsx, not
// shadcn's default 768 — one number for "phone" across the app.
const MOBILE_BREAKPOINT = 760

export function useIsMobile() {
  const [isMobile, setIsMobile] = React.useState<boolean | undefined>(undefined)

  React.useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`)
    const onChange = () => {
      setIsMobile(window.innerWidth <= MOBILE_BREAKPOINT)
    }
    mql.addEventListener('change', onChange)
    setIsMobile(window.innerWidth <= MOBILE_BREAKPOINT)
    return () => mql.removeEventListener('change', onChange)
  }, [])

  return !!isMobile
}
