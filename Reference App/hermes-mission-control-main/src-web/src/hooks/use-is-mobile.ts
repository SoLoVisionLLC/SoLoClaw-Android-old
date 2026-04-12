import { useState, useEffect } from 'react'

export function useIsMobile(breakpoint = 768) {
  const getIsMobile = () => {
    if (typeof window === 'undefined') return false
    return window.innerWidth < breakpoint
  }

  const [isMobile, setIsMobile] = useState(getIsMobile)

  useEffect(() => {
    const onResize = () => setIsMobile(getIsMobile())
    onResize()
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [breakpoint])

  return isMobile
}
