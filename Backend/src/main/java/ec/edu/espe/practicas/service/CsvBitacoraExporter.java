package ec.edu.espe.practicas.service;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class CsvBitacoraExporter implements BitacoraExporter {
    @Override public BitacoraFormat format() { return BitacoraFormat.CSV; }

    @Override
    public byte[] export(BitacoraExportData data) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRow(csv, "practicante", data.traineeName());
        appendRow(csv, "desde", data.from().toString());
        appendRow(csv, "hasta", data.to().toString());
        appendRow(csv, "total_horas_aceptadas", data.acceptedHours().toPlainString());
        csv.append("\r\n");
        appendRow(csv, "fecha", "practicante", "actividad", "horas_aceptadas", "entrada", "salida");
        data.activities().forEach(row -> appendRow(csv,
                row.date().toString(), row.traineeName(), row.description(), row.hours().toPlainString(),
                row.checkIn(), row.checkOut()));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, String... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            String value = cells[index] == null ? "" : cells[index];
            String trimmed = value.stripLeading();
            if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
