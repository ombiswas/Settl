-- V3: Reconcile all recurring expense templates to EQUAL split
UPDATE recurring_expenses
SET split_type = 'EQUAL'
WHERE split_type <> 'EQUAL';
