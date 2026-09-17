package com.codewalnut.prreviewer.domain;

/**
 * The specific technology a practice applies to. Broader than "programming
 * language" on purpose — Hibernate, React, Bootstrap, HTML/CSS, and MySQL
 * all need their own practices even though only some of these are languages.
 * See docs/architecture.md, "Extending beyond Java", for how this grows.
 */
public enum Technology {
    JAVA,
    SPRING,
    HIBERNATE,
    SQL,
    MYSQL,
    JAVASCRIPT,
    TYPESCRIPT,
    REACT,
    HTML,
    CSS,
    BOOTSTRAP,
    GENERAL
}
