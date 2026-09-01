import { useQuery } from '@tanstack/react-query'
import { fetchAccount } from '../api/client'

export const accountQueryKey = ['account'] as const

export function useAccount() {
  return useQuery({
    queryKey: accountQueryKey,
    queryFn: fetchAccount,
    retry: false,
    staleTime: 30_000,
  })
}
