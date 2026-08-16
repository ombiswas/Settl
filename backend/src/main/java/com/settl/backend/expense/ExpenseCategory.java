package com.settl.backend.expense;

public enum ExpenseCategory {
    FOOD_AND_DINING("Food & Dining"),
    TRANSPORTATION("Transportation"),
    HOUSING_AND_UTILITIES("Housing & Utilities"),
    ENTERTAINMENT("Entertainment"),
    SHOPPING("Shopping"),
    HEALTHCARE("Healthcare"),
    TRAVEL("Travel"),
    EDUCATION("Education"),
    PERSONAL_CARE("Personal Care"),
    OTHER("Other");

    private final String displayName;

    ExpenseCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
