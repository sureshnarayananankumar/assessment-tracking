package com.assesshub.domain.enums;

public enum QuestionType {
    MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE, SHORT_ANSWER, LONG_ANSWER, CODING, FILE_UPLOAD;

    /** Question types whose answer is a selection of options. */
    public boolean usesOptions() {
        return this == MCQ_SINGLE || this == MCQ_MULTI || this == TRUE_FALSE;
    }

    /** Single selection only. */
    public boolean singleSelection() {
        return this == MCQ_SINGLE || this == TRUE_FALSE;
    }

    public boolean usesText() {
        return this == SHORT_ANSWER || this == LONG_ANSWER;
    }
}
