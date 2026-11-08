package com.example.vacancyparser;

import java.time.LocalDate;

record Vacancy(
        String id,
        String url,
        String title,
        String company,
        String salary,
        String requirements,
        String city,
        LocalDate publishedAt
) {}
