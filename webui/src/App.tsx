import { useEffect, useState } from 'react'
import type { ReactElement } from 'react'
import { NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { fetchCurrentUser, type AuthUser } from '@/lib/api'
import { MessageSquare, Brain, Zap, Wrench, Users, UserCircle } from 'lucide-react'
import './App.css'
import ChatsPage from './ChatsPage'
import LoginPage from './LoginPage'
import MemoriesPage from './MemoriesPage'
import SkillsPage from './SkillsPage'
import ToolCatalogPage from './ToolCatalogPage'
import UsersPage from './UsersPage'
import UserInfoPage from './UserInfoPage'
import WorkflowPage from './WorkflowPage'

const baseMenuItems = [
  { label: 'Chats', path: '/workspace/chats', icon: MessageSquare },
  { label: 'Memories', path: '/workspace/memories', icon: Brain },
  { label: 'Skills', path: '/workspace/skills', icon: Zap },
  { label: 'Workflows', path: '/workspace/workflows', icon: Zap },
  { label: 'Tool Catalog', path: '/workspace/tool-catalog', icon: Wrench },
]

function isAdminLike(user: AuthUser | null) {
  return user?.role === 'ADMIN' || user?.role === 'SUPERADMIN'
}

function WorkspaceLayout({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()
  const menuItems =
    isAdminLike(currentUser)
      ? [...baseMenuItems, { label: 'Users', path: '/workspace/users', icon: Users }]
      : baseMenuItems
  const accountLabel = `@${currentUser?.displayName || currentUser?.username || 'account'}`

  return (
    <TooltipProvider>
      <SidebarProvider>
        <Sidebar collapsible="icon">
          <SidebarHeader>
            <div className="flex items-center justify-between px-2 h-12 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0">
              <NavLink to="/workspace/chats" className="flex items-center gap-2 group-data-[collapsible=icon]:hidden overflow-hidden">
                <div className="flex aspect-square size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                  <Zap className="size-4" />
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">Lightflare</span>
                  <span className="truncate text-xs text-muted-foreground">Workspace</span>
                </div>
              </NavLink>
              <SidebarTrigger className="-mr-1 shrink-0 group-data-[collapsible=icon]:m-0" />
            </div>
          </SidebarHeader>

          <SidebarContent>
            <SidebarGroup>
              <SidebarGroupContent>
                <SidebarMenu>
                  {menuItems.map((item) => (
                    <SidebarMenuItem key={item.path}>
                      <SidebarMenuButton
                        asChild
                        isActive={location.pathname === item.path || location.pathname.startsWith(`${item.path}/`)}
                        tooltip={item.label}
                      >
                        <NavLink to={item.path}>
                          <item.icon />
                          <span>{item.label}</span>
                        </NavLink>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </SidebarContent>

          <SidebarFooter>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  asChild
                  isActive={location.pathname === '/workspace/account'}
                  tooltip={accountLabel}
                >
                  <NavLink to="/workspace/account">
                    <UserCircle />
                    <span>{accountLabel}</span>
                  </NavLink>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarFooter>

          <SidebarRail />
        </Sidebar>

        <SidebarInset>
          <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4 md:hidden">
            <SidebarTrigger className="-ml-1" />
          </header>
        <div className="flex-1 min-h-0 flex flex-col">
          <Outlet />
        </div>
      </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}

function ProtectedWorkspace({ currentUser }: { currentUser: AuthUser | null }) {
  const location = useLocation()

  if (!currentUser) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (currentUser.mustChangePassword && location.pathname !== '/workspace/account') {
    return <Navigate to="/workspace/account" replace />
  }

  return <WorkspaceLayout currentUser={currentUser} />
}

function AdminOnlyRoute({ currentUser, children }: { currentUser: AuthUser | null, children: ReactElement }) {
  if (!isAdminLike(currentUser)) {
    return <Navigate to="/workspace/chats" replace />
  }

  return children
}

function App() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [isAuthLoading, setIsAuthLoading] = useState(true)
  const authDisabled = false

  useEffect(() => {
    let isMounted = true

    async function loadCurrentUser() {
      try {
        const user = await fetchCurrentUser()
        if (isMounted) {
          setCurrentUser(user)
        }
      } catch {
        if (isMounted) {
          setCurrentUser(null)
        }
      } finally {
        if (isMounted) {
          setIsAuthLoading(false)
        }
      }
    }

    void loadCurrentUser()

    return () => {
      isMounted = false
    }
  }, [])

  if (!authDisabled && isAuthLoading) {
    return <main className="login-shell"><section className="login-card"><h1>Loading...</h1></section></main>
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/workspace/chats" replace />} />
      <Route path="/login" element={<LoginPage currentUser={currentUser} onLogin={setCurrentUser} />} />
      <Route
        path="/workspace"
        element={authDisabled ? <WorkspaceLayout currentUser={currentUser} /> : <ProtectedWorkspace currentUser={currentUser} />}
      >
        <Route index element={<Navigate to="chats" replace />} />
        <Route
          path="chats"
          element={<ChatsPage />}
        />
        <Route
          path="skills"
          element={<SkillsPage />}
        />
        <Route
          path="memories"
          element={<MemoriesPage />}
        />
        <Route
          path="account"
          element={<UserInfoPage currentUser={currentUser} onUserChange={setCurrentUser} />}
        />
        <Route
          path="tool-catalog"
          element={<ToolCatalogPage />}
        />
        <Route
          path="workflows"
          element={<WorkflowPage />}
        />
        <Route
          path="workflows/:id"
          element={<WorkflowPage />}
        />
        <Route
          path="users"
          element={
            <AdminOnlyRoute currentUser={currentUser}>
              <UsersPage currentUser={currentUser} />
            </AdminOnlyRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/workspace/chats" replace />} />
    </Routes>
  )
}

export default App
