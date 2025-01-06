package org.home.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Payment {

    private final LocalDateTime date;
    private final int sharesQuantity;
    private final double dividend, total;

    public Payment(LocalDateTime date, int sharesQuantity, double dividend, double tax) {
        this.date = date;
        this.sharesQuantity = sharesQuantity;
        this.dividend = dividend;
        total = sharesQuantity * dividend - (sharesQuantity * dividend) / 100 * tax;
    }

    public String getDateString() { return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }

    public LocalDateTime getDate() { return date; }

    public String getSharesQuantity() { return String.valueOf(sharesQuantity); }

    public String getDividend() {
        return String.format(Locale.US, dividend < 1 ? "%6.7f" : "%6.2f" , dividend);
    }

    public String getTotal() {
        return String.format(Locale.US, "%8.2f", total);
    }

    public Double getTotalDouble() { return total; }
}
