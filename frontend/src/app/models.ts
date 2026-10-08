// Mirrors the Java API DTOs. Amounts use Java BigDecimal on the backend.
export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'INFORMATION_REQUIRED' | 'SETTLED' | 'REJECTED';
export interface Claim {
  id: string; version: number; claimant: string; type: 'MOTOR' | 'PROPERTY'; market: string;
  currency: string; incidentDate: string; description: string; estimatedAmount: number;
  settlementAmount: number | null; status: ClaimStatus; assignedOfficer: string | null;
  createdAt: string; updatedAt: string;
  history: { occurredAt: string; actor: string; status: string; message: string }[];
}
export interface SubmitClaim {
  type: 'MOTOR' | 'PROPERTY'; market: string; incidentDate: string; description: string; estimatedAmount: number;
}
export interface User { username: string; role: 'CLAIMANT' | 'OFFICER'; }
export interface Dashboard {
  byStatus: Record<string, number>; workloadByOfficer: Record<string, number>;
  outstandingExposureByCurrency: Record<string, number>; settledAmountByCurrency: Record<string, number>;
}
