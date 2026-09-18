package com.codewalnut.prreviewer.dto;

import java.time.LocalDate;

public record ReviewCountByDay(LocalDate date, long count) {}
