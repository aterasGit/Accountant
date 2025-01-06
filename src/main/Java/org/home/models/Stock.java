package org.home.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*****
 * Copyright (c) 2024 Renat Salimov
 **/

public class Stock {

    private final String ticker;
    private final double price, splitRatio;
    private final int lotAfterSplit;
    private final LocalDateTime splitDate;
    private final List<LocalDateTime> exDivDates = new ArrayList<>();
    private final List<Double> divs = new ArrayList<>();

    public Stock(String ticker, double price, String splitDate, Double splitRatio, Integer lotAfterSplit) {
        this.ticker = ticker;
        this.price = price;
        this.splitRatio = splitRatio != null ? splitRatio : 1;
        this.lotAfterSplit = lotAfterSplit != null ? lotAfterSplit : 0;
        this.splitDate = splitDate.equals("") ? null :
                LocalDateTime.of(
                        LocalDate.parse(splitDate),
                        LocalTime.of(0, 0, 0, 0)
                );
    }

    public String getTicker() {
        return ticker;
    }

    public double getPrice() {
        return price;
    }

    public double getSplitRatio() {
        return splitRatio;
    }

    public int getLotAfterSplit() {
        return lotAfterSplit;
    }

    public LocalDateTime getSplitDate() {
        return splitDate;
    }

    public List<LocalDateTime> getExDivDates() {
        return exDivDates;
    }

    public List<Double> getDivs() {
        return divs;
    }
}
