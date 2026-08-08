import { useEffect, useState } from 'react'
import { Checkout } from './Checkout'
import { LotManagement } from './LotManagement'
import { Receiving } from './Receiving'
import { Unpacking } from './Unpacking'
import { PricingWorkbench } from './PricingWorkbench'
import { ReviewQueue } from './ReviewQueue'
import { Reprint } from './Reprint'
import { MobileCapture } from './MobileCapture'
import { Prep } from './Prep'
import { Catalog } from './Catalog'
import { Suppliers } from './Suppliers'
import { PrinterConfig } from './admin/PrinterConfig'
import { NAV_GROUPS, PHONE_TABS, type View } from './nav'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'

/**
 * The dashboard shell: a grouped, collapsible sidebar on desktop and a bottom tab bar on phones.
 * Screens render inside unchanged; the shell only decides how they are reached. Selecting the
 * Till collapses the sidebar to its icon rail so scanning owns the width (focus mode).
 */
export function App() {
  // Phones are operators' devices: no sidebar, a bottom tab bar of operator screens instead.
  // A phone opened at #capture is a capture station; otherwise it is an unpacking station.
  // Wider screens open on the till. Decided once at load, as before.
  const isPhone = typeof window !== 'undefined' && window.innerWidth <= 760
  const phoneLanding: View =
    typeof window !== 'undefined' && window.location.hash === '#capture' ? 'capture' : 'unpacking'
  const [view, setView] = useState<View>(isPhone ? phoneLanding : 'checkout')

  // Till focus mode: the sidebar sits collapsed while the Till is up, expanded elsewhere.
  // Desktop lands on the till, so it starts collapsed.
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const select = (v: View) => {
    setView(v)
    setSidebarOpen(v !== 'checkout')
  }

  // Badge the header when this is the sandbox instance (same app, throwaway DB copy), so nobody
  // mistakes it for the live shop. The flag comes from the backend, set by start-sandbox.bat.
  const [sandbox, setSandbox] = useState(false)
  useEffect(() => {
    fetch('/api/instance')
      .then((r) => r.json())
      .then((d) => setSandbox(!!d.sandbox))
      .catch(() => {})
  }, [])

  const screen =
    view === 'checkout' ? <Checkout />
      : view === 'lots' ? <LotManagement />
      : view === 'receiving' ? <Receiving />
      : view === 'unpacking' ? <Unpacking />
      : view === 'prep' ? <Prep />
      : view === 'pricing' ? <PricingWorkbench />
      : view === 'review' ? <ReviewQueue />
      : view === 'reprint' ? <Reprint />
      : view === 'capture' ? <MobileCapture />
      : view === 'catalog' ? <Catalog />
      : view === 'suppliers' ? <Suppliers />
      : <PrinterConfig />

  if (isPhone) {
    return (
      <>
        {/* Room for the tab bar plus the phone's home-indicator strip, so it never covers content. */}
        <main style={{ paddingBottom: 'calc(72px + env(safe-area-inset-bottom))' }}>{screen}</main>
        <nav
          className="fixed inset-x-0 bottom-0 z-40 flex border-t border-border bg-background"
          style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        >
          {PHONE_TABS.map((tab) => (
            <button
              key={tab.view}
              data-slot="phone-tab"
              onClick={() => select(tab.view)}
              className={
                'flex flex-1 flex-col items-center gap-1 px-2 py-3 text-base ' +
                (view === tab.view ? 'font-semibold text-primary' : 'text-muted-foreground')
              }
            >
              <tab.icon className="size-6" />
              {tab.label}
            </button>
          ))}
        </nav>
      </>
    )
  }

  return (
    <SidebarProvider open={sidebarOpen} onOpenChange={setSidebarOpen}>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <div className="flex flex-col px-2 py-1 leading-tight group-data-[collapsible=icon]:hidden">
            <span className="text-base font-bold">Bachat Baazar</span>
            {sandbox && (
              <span className="text-[10px] font-bold tracking-[1.5px] text-amber-700">SANDBOX</span>
            )}
          </div>
        </SidebarHeader>
        <SidebarContent>
          {NAV_GROUPS.map((group) => (
            <SidebarGroup key={group.label}>
              <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => (
                    <SidebarMenuItem key={item.view}>
                      <SidebarMenuButton
                        tooltip={item.label}
                        isActive={view === item.view}
                        onClick={() => select(item.view)}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))}
        </SidebarContent>
      </Sidebar>
      <SidebarInset>
        <header className="flex h-10 shrink-0 items-center gap-2 border-b border-border px-2">
          <SidebarTrigger />
          <span className="text-sm text-muted-foreground">
            {NAV_GROUPS.flatMap((g) => g.items).find((i) => i.view === view)?.label}
          </span>
          {sandbox && (
            <span className="ml-auto text-[10px] font-bold tracking-[1.5px] text-amber-700">
              SANDBOX
            </span>
          )}
        </header>
        <div className="min-w-0 flex-1">{screen}</div>
      </SidebarInset>
    </SidebarProvider>
  )
}
