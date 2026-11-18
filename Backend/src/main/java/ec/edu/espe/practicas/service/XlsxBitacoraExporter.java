package ec.edu.espe.practicas.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class XlsxBitacoraExporter implements BitacoraExporter {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @Override public BitacoraFormat format() { return BitacoraFormat.XLSX; }

    @Override
    public byte[] export(BitacoraExportData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bitácora");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Bitácora de actividades aceptadas");
            sheet.createRow(1).createCell(0).setCellValue("Practicante: " + data.traineeName());
            sheet.createRow(2).createCell(0).setCellValue("Período: "
                    + data.from().format(DISPLAY_DATE) + " a " + data.to().format(DISPLAY_DATE));
            sheet.createRow(3).createCell(0).setCellValue("Total de horas aceptadas: " + data.acceptedHours().toPlainString());

            String[] headers = {"Fecha", "Practicante", "Actividad", "Horas aceptadas", "Entrada", "Salida"};
            Row header = sheet.createRow(5);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
            }
            int index = 6;
            for (BitacoraExportData.ActivityRow activity : data.activities()) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(activity.date().format(DISPLAY_DATE));
                row.createCell(1).setCellValue(activity.traineeName());
                row.createCell(2).setCellValue(activity.description());
                row.createCell(3).setCellValue(activity.hours().doubleValue());
                row.createCell(4).setCellValue(activity.checkIn());
                row.createCell(5).setCellValue(activity.checkOut());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo XLSX de la bitácora.", exception);
        }
    }
}
