package marvin.com.br.tiraentulho.model;


public class SincronizacaoResponse {

    private String status;
    private int rotas;
    private int percursos;
    private int rotas_recebidas;
    private int percursos_recebidos;

    public String getStatus() {
        return status;
    }

    public int getRotas() {
        return rotas;
    }

    public int getPercursos() {
        return percursos;
    }

    public int getRotas_recebidas() {
        return rotas_recebidas;
    }

    public int getPercursos_recebidos() {
        return percursos_recebidos;
    }
}