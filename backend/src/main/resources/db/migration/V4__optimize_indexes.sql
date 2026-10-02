-- V4: Optimize database indexes for query performance and remove redundant indexes

-- 1. Expenses: Group-level member spending sum query
-- Supports: ExpenseRepository.sumPaidByUserIdInGroup, countGroupExpensesPaidByUserId
CREATE INDEX IF NOT EXISTS idx_expenses_group_id_paid_by ON expenses(group_id, paid_by);

-- 2. Settlements: Group-level payments sent query
-- Supports: SettlementRepository.sumSettlementsPaidByUserIdInGroup
CREATE INDEX IF NOT EXISTS idx_settlements_group_id_from_user ON settlements(group_id, from_user);

-- 3. Settlements: Group-level payments received query
-- Supports: SettlementRepository.sumSettlementsReceivedByUserIdInGroup
CREATE INDEX IF NOT EXISTS idx_settlements_group_id_to_user ON settlements(group_id, to_user);

-- 4. Users: Email verification token lookup (partial index ignores verified users where token is NULL)
-- Supports: UserRepository.findByVerificationToken
CREATE INDEX IF NOT EXISTS idx_users_verification_token ON users(verification_token) WHERE verification_token IS NOT NULL;

-- 5. Refresh Tokens: User active sessions query
-- Supports: RefreshTokenRepository queries filtering by user_id and active/expiration status
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked_expires ON refresh_tokens(user_id, revoked, expires_at);

-- 6. Expenses: Personal expenses lookup (partial index excludes group expenses)
-- Supports: ExpenseRepository.findPersonalExpensesByUserId, findPersonalExpensesByUserIdAndCategory, deletePersonalExpensesByUserId
CREATE INDEX IF NOT EXISTS idx_expenses_personal_paid_by ON expenses(paid_by) WHERE group_id IS NULL;

-- 7. Audit Log: Activity feed sorted by creation date descending
-- Supports: AuditLogRepository.findByGroupIdOrderByCreatedAtDesc
CREATE INDEX IF NOT EXISTS idx_audit_log_group_id_created_at ON audit_log(group_id, created_at DESC);

-- Drop single-column index on audit_log(group_id) since the composite index above subsumes it
DROP INDEX IF EXISTS idx_audit_log_group_id;

-- 8. Refresh Tokens: Active unrevoked token lookup with compact working set
-- Supports: RefreshTokenRepository.findByTokenHash for active tokens
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash_unrevoked ON refresh_tokens(token_hash) WHERE revoked = false;
