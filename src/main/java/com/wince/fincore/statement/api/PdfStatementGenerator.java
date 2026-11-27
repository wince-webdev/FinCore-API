package com.wince.fincore.statement.api;

import com.wince.fincore.statement.application.StatementService;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

public class PdfStatementGenerator {
    public static byte[] generate(StatementService.Statement st) {
        try {
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, baos);
            doc.open();
            doc.add(new Paragraph("FinCore - Releve de compte"));
            doc.add(new Paragraph("Compte: " + mask(st.accountNumber())));
            doc.add(new Paragraph("Devise: " + st.currency()));
            doc.add(new Paragraph("Periode: " + st.from() + " -> " + st.to()));
            doc.add(new Paragraph("Solde ouverture: " + st.openingBalance()));
            PdfPTable table = new PdfPTable(5);
            table.addCell("Date");
            table.addCell("Reference");
            table.addCell("Debit");
            table.addCell("Credit");
            table.addCell("Solde");
            for (StatementService.StatementLine line : st.movements()) {
                table.addCell(String.valueOf(line.date()));
                table.addCell(line.reference());
                table.addCell(String.valueOf(line.debit()));
                table.addCell(String.valueOf(line.credit()));
                table.addCell(String.valueOf(line.balanceAfter()));
            }
            doc.add(table);
            doc.add(new Paragraph("Solde cloture: " + st.closingBalance()));
            String content = st.accountNumber() + st.openingBalance() + st.closingBalance();
            for (StatementService.StatementLine l : st.movements()) content += l.debit().toPlainString() + l.credit().toPlainString() + l.balanceAfter().toPlainString();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes()));
            doc.add(new Paragraph("Empreinte de verification: " + hash));
            doc.add(new Paragraph("Date de generation: " + java.time.Instant.now()));
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String mask(String num) {
        if (num == null || num.length() < 6) return num;
        return num.substring(0, 4) + "****" + num.substring(num.length() - 2);
    }
}
