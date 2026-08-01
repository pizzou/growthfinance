ALTER TABLE borrowers
    ALTER COLUMN monthly_expenses
    TYPE NUMERIC(38,2)
    USING monthly_expenses::NUMERIC(38,2);