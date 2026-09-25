package com.apuestas.service;

public interface IngestionBatchService {
    void ejecutarBatchMatutino();
    void ejecutarRefrescoTarde();
    void ejecutarConciliacionNocturna();
    String ejecutarDescargaManualHoy();
}
