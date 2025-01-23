package org.home.models;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class  Accountant {

    final int TRADE_DATE = 0, TICKER = 3, DIRECTION = 6, QUANTITY = 7, PRICE = 8,
            VOLUME = 10, BROKER_FEE = 14, TRADE_SYSTEM_FEE = 16;

    final String INFLATION_DATA_URL = "https://www.statbureau.org/ru/russia/inflation-tables",
            DIV_DATA_URL ="https://www.dohod.ru/ik/analytics/dividend/";

    private final Stock stock;
    private final double tax;
    private final Map<Integer, List<Double>> inflation;
    private Sheet tradesSheet;

    public Accountant(Stock stock, double tax, Double extraInflation, File tradesDescriber, String... divsDescriber) {

        this.stock = stock;
        this.tax = tax;
        this.inflation = getInflationMeasures(extraInflation);

        try {
            tradesSheet = new HSSFWorkbook(Files.newInputStream(tradesDescriber.toPath())).getSheetAt(0);
            if (divsDescriber.length == 0)
                parseDivDataFromHtml();
            else
                parseDivDataFromFile(divsDescriber);
        } catch (IOException e) {
            System.exit(1);
        }
    }

    private void parseDivDataFromFile(String[] divsDescriber) throws IOException {
        Sheet divsSheet = new HSSFWorkbook(Files.newInputStream(Paths.get(divsDescriber[0])))
                .getSheetAt(0);
        for (int row = 0; row <= divsSheet.getLastRowNum(); row++)
            if (divsSheet.getRow(row).getCell(0) != null &&
                    divsSheet.getRow(row).getCell(1) != null) {
                stock.getExDivDates().add(divsSheet.getRow(row).getCell(0).getLocalDateTimeCellValue());
                stock.getDivs().add(divsSheet.getRow(row).getCell(1).getNumericCellValue());
            }
    }

    private void parseDivDataFromHtml() {

        StringBuilder content = new StringBuilder();

        try {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(new URL(DIV_DATA_URL + stock.getTicker().toLowerCase())
                                    .openConnection()
                                    .getInputStream())
                    );
            String line;
            while ((line = reader.readLine()) != null)
                content.append(line);
        } catch (Exception ignored) {}

        Pattern date = Pattern.compile(
                "\\d+\\.\\d+\\.\\d+(?=<\\/td>\\s{0,20}<td>\\d{4}<\\/td>\\s{0,20}<td>\\d{0,2}\\.*\\d{0,10}<\\/td>)"
        ),
                div = Pattern.compile(
                        "(?<=td>\\s{0,20}<td>\\d{4}<\\/td>\\s{0,20}<td>)\\d{0,2}\\.*\\d{0,10}(?=<\\/td>)"
                );

        Matcher dateMatcher = date.matcher(content),
                divMatcher = div.matcher(content);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        while (dateMatcher.find())
            if (divMatcher.find()) {
                stock.getExDivDates().add(
                        LocalDateTime.of(LocalDate.parse(dateMatcher.group(), dtf),
                                LocalTime.of(0, 0, 0, 0))
                );
                stock.getDivs().add(Double.parseDouble(divMatcher.group()));
            }

        Collections.reverse(stock.getExDivDates());
        Collections.reverse(stock.getDivs());
    }

    public List<Payment> getPayments() {

        int sharesQuantity = 0, exDivDatePointer = 0, lot = getInitialLot();
        double splitRatio = stock.getSplitRatio();
        List<Payment> payments = new ArrayList<>();

        loop:
            for (Row row : tradesSheet)
                if (stock.getExDivDates().size() > 0 && isRowContainsTicker(row)) {
                    while (getTradeDateTime(row).isAfter(stock.getExDivDates().get(exDivDatePointer))) {
                        if (stock.getSplitDate() != null &&
                                (getTradeDateTime(row).isAfter(stock.getSplitDate()) ||
                                getTradeDateTime(row).isEqual(stock.getSplitDate()))) {
                            splitRatio = 1;
                            lot = stock.getLotAfterSplit();
                        }
                        payments.add(
                                new Payment(
                                        stock.getExDivDates().get(exDivDatePointer),
                                        sharesQuantity,
                                        stock.getDivs().get(exDivDatePointer++),
                                        tax
                                )
                        );
                        if (exDivDatePointer == stock.getExDivDates().size())
                            break loop;
                    }
                    sharesQuantity += (getTradeSharesQuantity(row) * lot) / splitRatio;
                }

        while (exDivDatePointer < stock.getExDivDates().size()) {
            payments.add(
                    new Payment(
                            stock.getExDivDates().get(exDivDatePointer),
                            sharesQuantity,
                            stock.getDivs().get(exDivDatePointer++),
                            tax
                    )
            );
        }

        payments.removeIf((payment) -> payment.getSharesQuantity().equals("0"));

        return payments;
    }

    public double getProfit() {

        double volume = 0;

        for (Row row : tradesSheet) {
            if (isRowContainsTicker(row))
                volume += getTradeVolume(row) + getTradeFees(row);
        }

        return -volume;
    }

    public double getInflationAdjustedAmount() {

        LocalDateTime tradeDayTime = null, currentPaymentDate;
        List<Payment> payments = getPayments();
        double balance = 0;
        int currentPayment = 0;

        for (Row row : tradesSheet) {
            if (isRowContainsTicker(row)) {
                if (tradeDayTime == null) {
                    tradeDayTime = getTradeDateTime(row);
                    balance = row.getCell(VOLUME).getNumericCellValue() + getTradeFees(row);
                    continue;
                }
                while (currentPayment < payments.size() &&
                        getTradeDateTime(row).
                                    isAfter(currentPaymentDate = payments.get(currentPayment).getDate())) {
                    balance = adjustWithInflationAndPayments(balance, tradeDayTime, payments.get(currentPayment++));
                    tradeDayTime = currentPaymentDate;
                }

                balance += (balance / 100) *
                        getDailyInflationBetween(tradeDayTime, getTradeDateTime(row)) *
                        Duration.between(tradeDayTime, getTradeDateTime(row)).
                                toMinutes() / (double) 1440;
                balance += getTradeVolume(row) + getTradeFees(row);
                tradeDayTime = getTradeDateTime(row);
            }
        }

        assert tradeDayTime != null;

        while (currentPayment < payments.size()) {
            balance = adjustWithInflationAndPayments(balance, tradeDayTime, payments.get(currentPayment));
            tradeDayTime = payments.get(currentPayment++).getDate();
        }

        if (getSharesBalance() > 0) {
            balance += (balance / 100) *
                    getDailyInflationBetween(tradeDayTime, LocalDateTime.now()) *
                    Duration.between(tradeDayTime, LocalDateTime.now()).toMinutes() / (double) 1440;
        }

        return balance;
    }

    private double getDailyInflationBetween(LocalDateTime start, LocalDateTime end) {

        int startYear = start.getYear(), startMonth = start.getMonthValue(),
                endYear = end.getYear(), endMonth = end.getMonthValue();

        if (!inflation.containsKey(startYear) || startMonth > inflation.get(startYear).size())
            return 0;

        if (!inflation.containsKey(endYear)) {
            endYear = inflation.keySet().stream().max(Comparator.comparingInt(year -> year)).get();
            endMonth = inflation.get(endYear).size();
        } else if (endMonth > inflation.get(endYear).size())
            endMonth = inflation.get(endYear).size();

        double accumulator = 0;
        int months = 0;

        do {
            if (startMonth > 12) {
                startMonth = 1;
                startYear++;
            }
            accumulator += inflation.get(startYear).get(startMonth - 1);
            months++;
        } while (!(startMonth++ == endMonth && startYear == endYear));

        return accumulator / (months * 30.4375);
    }

    private Map<Integer, List<Double>> getInflationMeasures(Double extraInflation) {

        Map<Integer, List<Double>> inflationData = new TreeMap<>((year1, year2) -> year2 - year1);
        StringBuilder content = new StringBuilder();

        try {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(new URL(INFLATION_DATA_URL)
                            .openConnection().getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
                content.append(line);
        } catch (Exception ignored) {}

        Pattern yearSnippet = Pattern.compile(
                "(?<=href=\"\\/ru\\/russia\\/inflation\\/)[\\d\\Wa-zA-Z]{100,1150}(?=\\d+,\\d+<\\/td>)"
        ),
                monthInflation = Pattern.compile("-?\\d{1,2},\\d{1,2}");

        Matcher yearSnippetMatcher = yearSnippet.matcher(content);

        while (yearSnippetMatcher.find()) {
            String snippet = yearSnippetMatcher.group();
            Matcher monthInflationMatcher = monthInflation.matcher(snippet);
            int year = Integer.parseInt(snippet.substring(0,4));
            List<Double> monthlyData= new ArrayList<>();
            while (monthInflationMatcher.find())
                monthlyData.add(Double.parseDouble(monthInflationMatcher.group().replace(",", ".")));
            inflationData.put(year, monthlyData);
            if (year == 2000)
                break;
        }

        if (extraInflation != null) {
            int lastYear = inflationData.keySet().stream().findFirst().get();
            if (inflationData.get(lastYear).size() < 12)
                inflationData.get(lastYear).add(extraInflation);
            else
                inflationData.put(++lastYear, new ArrayList<>(Collections.singleton(extraInflation)));
        }

        return inflationData;
    }

    public double getAverageAmount() {

        double volume = 0, total = 0, result;
        int sharesQuantity = 0;
        LocalDateTime dateTime = null;

        for (Row row : tradesSheet) {
            if (isRowContainsTicker(row)) {
                if (dateTime != null)
                    total += volume * Duration.between(dateTime, getTradeDateTime(row)).toMinutes() / (double) 1440;

                dateTime = getTradeDateTime(row);
                volume += getTradeVolume(row) + getTradeFees(row);
                sharesQuantity += getTradeSharesQuantity(row);
            }
        }

        assert dateTime != null;

        if (sharesQuantity != 0) {
            total += volume * Duration.between(dateTime, LocalDateTime.now()).toMinutes() / (double) 1440;
            result = total / (Duration.between(getFirstTradeDate(), LocalDateTime.now()).toMinutes() / (double) 1440);
        } else
            result = total / (Duration.between(getFirstTradeDate(), dateTime).toMinutes() / (double) 1440);

        return result;
    }

    public int getSharesBalance() {

        int sharesQuantity = 0, lot = getInitialLot();
        double splitRatio = stock.getSplitRatio();

        for (Row row : tradesSheet)
            if (isRowContainsTicker(row)) {
                if (stock.getSplitDate() != null &&
                        (getTradeDateTime(row).isAfter(stock.getSplitDate()) ||
                                getTradeDateTime(row).isEqual(stock.getSplitDate()))) {
                    splitRatio = 1;
                    lot = stock.getLotAfterSplit();
                }
                sharesQuantity += (getTradeSharesQuantity(row) * lot) / splitRatio;
            }

        return sharesQuantity;
    }

    public LocalDateTime getFirstTradeDate() {
        LocalDateTime firstTradeDate = null;
        for (Row row : tradesSheet)
            if (isRowContainsTicker(row)) {
                firstTradeDate = getTradeDateTime(row);
                break;
            }
        return firstTradeDate;
    }

    public LocalDateTime getLastTradeDate() {
        LocalDateTime lastTradeDate = null;
        for (Row row : tradesSheet)
            if (isRowContainsTicker(row))
                lastTradeDate = getTradeDateTime(row);
        return lastTradeDate;
    }

    private int getInitialLot() {
        int lot = 0;
        for (Row row : tradesSheet)
            if (isRowContainsTicker(row)) {
                lot = (int) (row.getCell(VOLUME).getNumericCellValue() /
                        row.getCell(PRICE).getNumericCellValue() /
                        row.getCell(QUANTITY).getNumericCellValue());
                break;
            }
        return lot;
    }

    private double adjustWithInflationAndPayments(double balance, LocalDateTime tradeDayTime, Payment payment) {
        balance += (balance / 100) * getDailyInflationBetween(tradeDayTime, payment.getDate()) *
                Duration.between(tradeDayTime, payment.getDate()).toMinutes() / (double) 1440;
        balance -= payment.getTotalDouble();
        return balance;
    }

    public double getAnnualPercentageYield() {
        return 100 / (getAverageAmount() /
                ((getSharesBalance() > 0 ? getSharesBalance() * stock.getPrice() : 0) - getInflationAdjustedAmount())) /
                (Duration.between(
                        getFirstTradeDate(),
                        getSharesBalance() > 0 ? LocalDateTime.now() : getLastTradeDate()
                ).toDays() / 365.25);
    }

    public double getAverageSharePrice() {
        return getSharesBalance() > 0 ?
                getInflationAdjustedAmount() / getSharesBalance() : 0;
    }

    private double getTradeFees(Row row) {
        return row.getCell(BROKER_FEE).getNumericCellValue() +
                row.getCell(TRADE_SYSTEM_FEE).getNumericCellValue();
    }

    private double getTradeVolume(Row row) {
        return isRowContainsBuyTrade(row) ?
                row.getCell(VOLUME).getNumericCellValue() : -row.getCell(VOLUME).getNumericCellValue();
    }

    private double getTradeSharesQuantity(Row row) {
        return isRowContainsBuyTrade(row) ?
                row.getCell(QUANTITY).getNumericCellValue() : -row.getCell(QUANTITY).getNumericCellValue();
    }

    private LocalDateTime getTradeDateTime(Row row) {
        return row.getCell(TRADE_DATE).getLocalDateTimeCellValue();
    }

    private boolean isRowContainsTicker(Row row) {
        return row != null &&
                row.getCell(TICKER) != null &&
                row.getCell(TICKER).getCellType() == CellType.STRING &&
                row.getCell(TICKER).getStringCellValue().equals(stock.getTicker().toUpperCase());
    }

    private boolean isRowContainsBuyTrade(Row row) {
        return row.getCell(DIRECTION).getStringCellValue().equals("Купля");
    }

}
