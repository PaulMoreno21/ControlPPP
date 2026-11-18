package ec.edu.espe.practicas.service;

public interface BitacoraExporter {
    BitacoraFormat format();
    byte[] export(BitacoraExportData data);
}
