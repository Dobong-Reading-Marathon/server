package com.dobong.dobong.library;

/** 내서재 목록의 책 한 권 */

public record LibraryItemResponse(
        Long enrollmentBookId,
        String title,
        String author,
        int totalPages,
        int readPages,
        boolean completed,
        boolean libraryOwned,
        boolean children,
        LibraryStatus status
) {}