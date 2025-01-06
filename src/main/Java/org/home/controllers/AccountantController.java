package org.home.controllers;

import org.home.models.Accountant;
import org.home.models.Payment;
import org.home.models.Stock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
@RequestMapping("/accountant")
public class AccountantController {

    @GetMapping
    public String askFile() {
        return "/accountant/import";
    }

    @PostMapping("importXLS")
    public String importXLS(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ticker") String ticker,
            @RequestParam("tax") double tax,
            @RequestParam("price") double price,
            @RequestParam(value = "extraInflation", required = false) Double extraInflation,
            @RequestParam(value = "splitDate", required = false) String splitDate,
            @RequestParam(value = "splitRatio", required = false) Double splitRatio,
            @RequestParam(value = "lotAfterSplit", required = false) Integer lotAfterSplit,
            Model model) {

        File tempFile = new File("tmp_" + file.hashCode() + ".xls");
        try {
            file.transferTo(tempFile);
            Accountant accountant = new Accountant(
                    new Stock(ticker, price, splitDate, splitRatio, lotAfterSplit),
                    tax,
                    extraInflation,
                    tempFile
            );
            model.addAttribute("payments", accountant.getPayments());
            model.addAttribute(
                    "paymentsTotal",
                    String.format(
                            Locale.US,
                            "%.2f",
                            accountant.getPayments().stream().mapToDouble(Payment::getTotalDouble).sum()
                    )
            );
            model.addAttribute("profitTotal", String.format(Locale.US, "%.2f", accountant.getProfit()));
            model.addAttribute(
                    "averageAmount",
                    String.format(Locale.US, "%.2f", accountant.getAverageAmount())
            );
            model.addAttribute(
                    "adjustedAmount",
                    String.format(Locale.US, "%.2f", accountant.getInflationAdjustedAmount())
            );
            model.addAttribute("shareBalance", accountant.getSharesBalance());
            model.addAttribute(
                    "firstDealDate",
                    accountant.getFirstTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            );
            model.addAttribute(
                    "lastDealDate",
                    accountant.getLastTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            );
            model.addAttribute(
                    "annualPercentageYield",
                    String.format(Locale.US, "%.2f", accountant.getAnnualPercentageYield()));
            model.addAttribute(
                    "averageSharePrice",
                    String.format(Locale.US, "%.2f", accountant.getAverageSharePrice()));
        } catch (IOException ignored) {}
        tempFile.deleteOnExit();
        return "/accountant/showResult";
    }

    @PostMapping("/back")
    public String back() {
        return "redirect:/accountant";
    }
}
