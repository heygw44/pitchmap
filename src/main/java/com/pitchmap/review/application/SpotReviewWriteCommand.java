package com.pitchmap.review.application;

import java.time.LocalDate;

/** 후기 작성 요청. visitedDate는 한국 날짜이고, 평점은 1~5이며, 내용은 공백뿐일 수 없다. */
public record SpotReviewWriteCommand(LocalDate visitedDate, int rating, String content) {}
