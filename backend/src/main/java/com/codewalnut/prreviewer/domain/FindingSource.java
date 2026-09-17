package com.codewalnut.prreviewer.domain;

/** BOTH means the rule engine and the LLM independently flagged the same spot. */
public enum FindingSource {
    RULE,
    LLM,
    BOTH
}
